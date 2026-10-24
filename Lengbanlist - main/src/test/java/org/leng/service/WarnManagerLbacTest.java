package org.leng.service;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.WarnEntry;
import org.leng.storage.DatabaseManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarnManagerLbacTest {

    @Mock Lengbanlist plugin;
    @Mock DatabaseManager db;
    @Mock BanManager banManager;
    @Mock AuditManager audit;
    @Mock Server server;
    @Mock PluginManager pluginManager;

    WarnManager manager;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getDatabaseManager()).thenReturn(db);
        lenient().when(plugin.getBanManager()).thenReturn(banManager);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("WarnManagerLbacTest"));
        lenient().when(plugin.getServer()).thenReturn(server);
        manager = new WarnManager(plugin);
    }

    @Test
    void doesNotLiftManualIpBanWhenWarningsBelowThreshold() {
        when(db.getWarnings("1.2.3.4", false)).thenReturn(List.of());
        BanIpEntry manual = new BanIpEntry("1.2.3.4", "admin",
                System.currentTimeMillis() + 60_000L, "manual", false);
        when(banManager.getBanIpEntry("1.2.3.4")).thenReturn(manual);

        manager.checkUnbanIfNecessary("1.2.3.4");

        verify(banManager, never()).tryUnbanIp(anyString(), anyString(), anyBoolean());
    }

    @Test
    void liftsLbacIpBanWhenWarningsBelowThreshold() {
        when(db.getWarnings("1.2.3.4", false)).thenReturn(List.of());
        BanIpEntry auto = new BanIpEntry("1.2.3.4", WarnManager.AUTO_STAFF,
                System.currentTimeMillis() + 60_000L, "auto", true);
        when(banManager.getBanIpEntry("1.2.3.4")).thenReturn(auto);
        when(banManager.tryUnbanIp("1.2.3.4", WarnManager.AUTO_STAFF, false))
                .thenReturn(BanManager.BanMutationResult.APPLIED);

        manager.checkUnbanIfNecessary("1.2.3.4");

        verify(banManager).tryUnbanIp("1.2.3.4", WarnManager.AUTO_STAFF, false);
    }

    @Test
    void autoBansAtThresholdAndMarksTrigger() {
        long now = System.currentTimeMillis();
        when(plugin.isFeatureEnabled("offline-warn")).thenReturn(false);
        when(plugin.getAuditManager()).thenReturn(audit);
        when(db.upsertWarning(any(WarnEntry.class))).thenReturn(true);
        when(db.getWarnings("Steve", false)).thenReturn(List.of(
                new WarnEntry("Steve", "Admin", now - 1_000L, "r1"),
                new WarnEntry("Steve", "Admin", now - 2_000L, "r2"),
                new WarnEntry("Steve", "Admin", now - 3_000L, "r3")));
        when(banManager.getBanEntry("Steve")).thenReturn(null);
        ArgumentCaptor<BanEntry> captor = ArgumentCaptor.forClass(BanEntry.class);
        when(banManager.tryBanPlayer(captor.capture())).thenReturn(BanManager.BanMutationResult.APPLIED);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            manager.warnPlayer("Steve", "Admin", "cheat");
        }

        BanEntry ban = captor.getValue();
        assertEquals(WarnManager.AUTO_STAFF, ban.getStaff());
        assertTrue(ban.getReason().contains("[LBAC#1]"));
        assertTrue(ban.getTime() > now);
        verify(server).broadcastMessage(contains("[LBAC]"));
    }
}

package org.leng.manager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MuteManagerIdentityTest {

    private static final String UUID_ALICE = "11111111-1111-1111-1111-111111111111";

    @Mock Lengbanlist plugin;
    @Mock AuditManager auditManager;
    @TempDir Path tempDir;

    private DatabaseManager db;
    private MuteManager manager;

    @BeforeEach
    void setUp() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.type", "sqlite");
        config.set("database.sqlite.file", "lengbanlist.db");
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getStorageConfig()).thenReturn(config);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("MuteManagerIdentityTest"));
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getAuditManager()).thenReturn(auditManager);

        db = new DatabaseManager(plugin);
        db.initialize();
        lenient().when(plugin.getDatabaseManager()).thenReturn(db);
        manager = new MuteManager(plugin);
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    private void rename(String from, String to) {
        db.recordIdentity(UUID_ALICE, from, System.currentTimeMillis());
        db.recordIdentity(UUID_ALICE, to, System.currentTimeMillis());
    }

    private void withBukkit(Runnable action) {
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(Mockito.mock(PluginManager.class));
            action.run();
        }
    }

    @Test
    void muteOnOldNameStillAppliesAfterRename() {
        rename("Alice", "Alicia");
        db.upsertMute(new MuteEntry("Alice", "staff", System.currentTimeMillis() + 60_000L, "旧名禁言"));

        assertTrue(manager.isPlayerMuted("Alicia"), "改名不该让人甩掉禁言");
        assertTrue(manager.isPlayerMuted("Alice"), "按旧名查也还是禁言中");
        assertFalse(manager.isPlayerMuted("Bob"));
    }

    @Test
    void unmuteByNewNameClearsTheOldNameRow() {
        rename("Alice", "Alicia");
        db.upsertMute(new MuteEntry("Alice", "staff", System.currentTimeMillis() + 60_000L, "旧名禁言"));

        withBukkit(() -> manager.unmutePlayer("Alicia", "staff"));

        assertTrue(db.getMutesByPlayer("Alicia").isEmpty(), "按新名解禁要把旧名的记录一起清掉");
        assertFalse(manager.isPlayerMuted("Alicia"));
    }

    @Test
    void ipMuteTargetsKeepMatchingLiterally() {
        withBukkit(() -> manager.mutePlayer(
                new MuteEntry("203.0.113.7", "staff", System.currentTimeMillis() + 60_000L, "IP 禁言")));

        assertTrue(manager.isIpMuted("203.0.113.7"));
        assertEquals(1, db.getMutesByPlayer("203.0.113.7").size(), "IP 目标原样入库，不走身份归并");
        assertFalse(manager.isPlayerMuted("Alice"), "IP 禁言不该算到任何玩家头上");
    }
}

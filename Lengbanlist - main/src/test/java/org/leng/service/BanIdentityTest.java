package org.leng.service;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.leng.storage.DatabaseManager;

@ExtendWith(MockitoExtension.class)
class BanIdentityTest {

    private static final String UUID_ALICE = "11111111-1111-1111-1111-111111111111";

    @Mock Lengbanlist plugin;
    @TempDir Path tempDir;

    private DatabaseManager db;
    private BanManager manager;

    @BeforeEach
    void setUp() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.type", "sqlite");
        config.set("database.sqlite.file", "lengbanlist.db");
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getStorageConfig()).thenReturn(config);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("BanIdentityTest"));
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        db = new DatabaseManager(plugin);
        db.initialize();
        when(plugin.getDatabaseManager()).thenReturn(db);
        manager = new BanManager(plugin);
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    private static long endTime() {
        return System.currentTimeMillis() + 60_000L;
    }

    private void renameAliceToAlicia() {
        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());
        db.recordIdentity(UUID_ALICE, "Alicia", System.currentTimeMillis());
    }

    @Test
    void banWriteFillsUuidFromKnownIdentity() {
        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());
        db.replaceActiveBan(new BanEntry("Alice", "staff", endTime(), "浣滃紛", false));

        assertTrue(db.isPlayerBanned("Alice"));
        assertTrue(db.isPlayerBannedByUuid(UUID_ALICE));
        assertEquals("Alice", db.getBanByUuid(UUID_ALICE).getTarget());
    }

    @Test
    void banWriteLeavesUuidEmptyForUnknownName() {
        db.replaceActiveBan(new BanEntry("Ghost", "staff", endTime(), "浠庢湭鐧诲綍杩�", false));

        assertTrue(db.isPlayerBanned("Ghost"), "娌℃湁韬�浠借�板綍鏃舵寜鍚嶅瓧鐓ф牱灏佸緱浣�");
        assertNull(db.getBanByUuid(UUID_ALICE));
    }

    @Test
    void renameDoesNotLiftTheBan() {
        renameAliceToAlicia();
        db.replaceActiveBan(new BanEntry("Alice", "staff", endTime(), "浣滃紛", false));

        assertFalse(db.isPlayerBanned("Alicia"), "鎸夊悕瀛楁煡涓嶅埌锛岃繖灏辨槸鏀瑰悕缁曞皝绂佺殑鍏ュ彛");
        assertTrue(db.isPlayerBannedByUuid(UUID_ALICE), "鎸� UUID 蹇呴』浠嶇劧鍛戒腑");
    }

    @Test
    void banningUnderTheNewNameReplacesTheOldNameRow() {
        renameAliceToAlicia();
        db.replaceActiveBan(new BanEntry("Alice", "staff", endTime(), "鏃у悕灏佺��", false));
        db.replaceActiveBan(new BanEntry("Alicia", "staff", endTime() + 1000L, "鏂板悕灏佺��", false));

        List<BanEntry> history = db.getBansByPlayer("Alicia");
        assertEquals(2, history.size(), "鏃ц�板綍鐣欎綔鍘嗗彶");
        assertEquals(1, history.stream().filter(BanEntry::isActive).count(),
                "鍚屼竴涓�浜轰笉璇ュ悓鏃舵寕鐫�涓ゆ潯鐢熸晥涓�鐨勫皝绂�");
        assertEquals("Alicia", db.getBanByUuid(UUID_ALICE).getTarget());
    }

    @Test
    void unbanByNewNameLiftsTheOldNameBan() {
        renameAliceToAlicia();
        db.replaceActiveBan(new BanEntry("Alice", "staff", endTime(), "浣滃紛", false));

        assertEquals(DatabaseManager.WriteResult.APPLIED, db.deactivateBanForUnban("Alicia", System.currentTimeMillis()));

        assertFalse(db.isPlayerBannedByUuid(UUID_ALICE));
        assertTrue(db.getBansByPlayer("Alicia").stream().noneMatch(BanEntry::isActive));
    }

    @Test
    void expiredBanUnderOldNameIsCleanedUpByUuidMatch() {
        long past = System.currentTimeMillis() - 1000L;
        renameAliceToAlicia();
        db.replaceActiveBan(new BanEntry("Alice", "staff", past, "杩囨湡灏佺��", false));

        assertEquals(DatabaseManager.WriteResult.NO_CHANGE, db.deactivateBanForUnban("Alicia", System.currentTimeMillis()),
                "杩囨湡琛屾湰鏉ュ氨涓嶇畻銆屾湁鏁堣В灏併�嶏紝涓庢棫琛屼负涓�鑷�");
        assertTrue(db.getBansByPlayer("Alicia").stream().noneMatch(BanEntry::isActive),
                "鏀瑰悕鍚庣殑杩囨湡灏佺�佷篃瑕佽兘琚�鏀舵帀锛屼笉鐒跺畠浼氫竴鐩存寕鍦� active 閲�");
    }

    @Test
    void renamedPlayerIsStillKickedOnJoin() {
        renameAliceToAlicia();
        db.replaceActiveBan(new BanEntry("Alice", "staff", endTime(), "浣滃紛", false));

        when(plugin.isFeatureEnabled("ban")).thenReturn(true);
        lenient().when(plugin.isFeatureEnabled("ban-ip")).thenReturn(false);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alicia");
        when(player.getUniqueId()).thenReturn(UUID.fromString(UUID_ALICE));

        List<Runnable> scheduled = new ArrayList<>();
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(invocation -> {
                scheduled.add(invocation.getArgument(1));
                return null;
            }).when(scheduler).runTask(any(), any(Runnable.class));

            manager.checkBanOnJoin(player);
            scheduled.forEach(Runnable::run);
        }

        verify(player).kickPlayer(contains("浣滃紛"));
    }

    @Test
    void unbannedPlayerIsNotKickedOnJoin() {
        renameAliceToAlicia();

        when(plugin.isFeatureEnabled("ban")).thenReturn(true);
        when(plugin.isFeatureEnabled("ban-ip")).thenReturn(false);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Alicia");
        when(player.getUniqueId()).thenReturn(UUID.fromString(UUID_ALICE));

        manager.checkBanOnJoin(player);

        verify(player, Mockito.never()).kickPlayer(any());
    }
}

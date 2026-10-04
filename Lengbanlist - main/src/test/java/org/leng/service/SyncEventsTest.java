package org.leng.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.FreezeEntry;
import org.leng.object.MuteEntry;
import org.leng.object.SyncEvent;
import org.leng.object.WarnEntry;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import org.leng.storage.DatabaseManager;

@ExtendWith(MockitoExtension.class)
class SyncEventsTest {

    @Mock Lengbanlist plugin;
    @TempDir Path tempDir;

    private DatabaseManager db;
    private File dbFile;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tempDir.resolve("lengbanlist.db").toFile();
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.type", "sqlite");
        config.set("database.sqlite.file", "lengbanlist.db");
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getStorageConfig()).thenReturn(config);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("LengbanlistTest"));
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        db = new DatabaseManager(plugin);
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    private List<SyncEvent> events() {
        return db.getSyncEventsAfter(0L, 200);
    }

    private List<String> signatures() {
        List<String> result = new ArrayList<>();
        for (SyncEvent event : events()) {
            result.add(event.scope() + "/" + event.target() + "/" + event.action());
        }
        return result;
    }

    @Test
    void banWrites_publishBanAndIpBanEvents() {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Alice", "staff", end, "浣滃紛", false));
        db.deactivateBanForUnban("Alice", System.currentTimeMillis());
        db.replaceActiveIpBan(new BanIpEntry("203.0.113.7", "staff", end, "VPN", false));
        db.deactivateIpBanForUnban("203.0.113.7", System.currentTimeMillis());

        assertEquals(List.of(
                "ban/Alice/add",
                "ban/Alice/remove",
                "ip-ban/203.0.113.7/add",
                "ip-ban/203.0.113.7/remove"), signatures());
    }

    @Test
    void muteFreezeWarnWrites_publishTheirScopes() {
        long end = System.currentTimeMillis() + 60_000L;
        db.upsertMute(new MuteEntry("Bob", "staff", end, "鍒峰睆"));
        db.deleteMute("Bob");
        db.saveFreeze(new FreezeEntry("Carol", "staff", System.currentTimeMillis(), "鎹ｄ贡"));
        db.deleteFreeze("Carol");
        db.upsertWarning(new WarnEntry("w1", "Dave", "staff", System.currentTimeMillis(), "骞垮憡"));
        db.updateWarningRevoked("w1", true, "Dave");

        assertEquals(List.of(
                "mute/bob/add",
                "mute/Bob/remove",
                "freeze/Carol/add",
                "freeze/Carol/remove",
                "warn/Dave/add",
                "warn/Dave/remove"), signatures());
    }

    @Test
    void deleteAllFreezes_publishesWildcardEvent() throws Exception {
        db.saveFreeze(new FreezeEntry("Carol", "staff", System.currentTimeMillis(), "鎹ｄ贡"));
        long cursor = db.getLatestSyncEventId();
        assertTrue(db.deleteAllFreezes() > 0);

        List<SyncEvent> after = db.getSyncEventsAfter(cursor, 10);
        assertEquals(1, after.size());
        assertEquals(SyncEvent.SCOPE_FREEZE, after.get(0).scope());
        assertEquals(SyncEvent.TARGET_ALL, after.get(0).target());
    }

    @Test
    void noOpWrite_publishesNothing() {
        long end = System.currentTimeMillis() + 60_000L;
        assertEquals(DatabaseManager.WriteResult.NO_CHANGE,
                db.replaceExistingActiveBan(new BanEntry("Nobody", "staff", end, "鏀规湡", false)));
        assertEquals(DatabaseManager.WriteResult.NO_CHANGE,
                db.deactivateBanForUnban("Nobody", System.currentTimeMillis()));
        assertTrue(events().isEmpty(), "娌℃湁瀹為檯鍙樻洿鏃朵笉搴斾骇鐢熻法鏈嶄簨浠�");
    }

    @Test
    void cursorReturnsOnlyNewerEvents() {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Alice", "staff", end, "绗�涓�涓�", false));
        long cursor = db.getLatestSyncEventId();
        db.replaceActiveBan(new BanEntry("Bob", "staff", end, "绗�浜屼釜", false));

        List<SyncEvent> after = db.getSyncEventsAfter(cursor, 10);
        assertEquals(1, after.size());
        assertEquals("Bob", after.get(0).target());
        assertTrue(after.get(0).id() > cursor);
        assertEquals(2, db.getSyncEventsAfter(0L, 10).size());
    }

    @Test
    void cleanupRemovesExpiredEventsOnly() throws Exception {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Fresh", "staff", end, "鏂扮殑", false));
        db.execute("INSERT INTO sync_events (timestamp, server, scope, target, uuid, action)"
                + " VALUES (1, '', 'ban', 'Stale', '', 'add')");
        assertEquals(2, events().size());

        assertEquals(1, db.cleanupSyncEvents(60));

        List<SyncEvent> remaining = events();
        assertEquals(1, remaining.size());
        assertEquals("Fresh", remaining.get(0).target());
    }

    @Test
    void cleanupDisabledWhenRetentionIsZero() {
        assertEquals(0, db.cleanupSyncEvents(0));
        assertEquals(0, db.cleanupSyncEvents(-5));
    }

    @Test
    void emptyTableHasZeroCursor() {
        assertEquals(0L, db.getLatestSyncEventId());
        assertTrue(events().isEmpty());
    }
}

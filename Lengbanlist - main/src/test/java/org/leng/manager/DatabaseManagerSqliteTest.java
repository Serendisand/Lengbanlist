package org.leng.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.leng.Lengbanlist;
import org.leng.object.AuditEntry;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DatabaseManagerSqliteTest {

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

    @Test
    void banThenUnban_cacheStaysConsistent() {
        long end = System.currentTimeMillis() + 60_000L;
        assertEquals(DatabaseManager.WriteResult.APPLIED, db.replaceActiveBan(new BanEntry("Alice", "staff", end, "作弊", false)));

        assertTrue(db.isPlayerBanned("Alice"), "封禁后应立即可查(写路径必须失效缓存)");
        assertNotNull(db.getBan("Alice"));
        assertEquals(1, db.countActiveBans());

        assertEquals(DatabaseManager.WriteResult.APPLIED, db.deactivateBanForUnban("Alice", System.currentTimeMillis()));
        assertFalse(db.isPlayerBanned("Alice"), "解封后缓存必须同步失效");
        assertNull(db.getBan("Alice"));
        assertEquals(0, db.countActiveBans());
    }

    @Test
    void banLookup_isCaseInsensitive() {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Alice", "staff", end, "原因", false));
        assertNotNull(db.getBan("aLiCe"));
        assertTrue(db.isPlayerBanned("ALICE"));
    }

    @Test
    void replaceActiveBan_keepsOnlyNewestActiveRow() {
        long now = System.currentTimeMillis();
        db.replaceActiveBan(new BanEntry("Bob", "s1", now + 1000L, "旧", false));
        db.replaceActiveBan(new BanEntry("Bob", "s2", now + 2000L, "新", false));

        List<BanEntry> active = db.getAllActiveBans();
        assertEquals(1, active.size(), "旧封禁应被置为 active=0");
        assertEquals("新", active.get(0).getReason());
        assertEquals(2, db.countBanHistory("Bob"), "countBanHistory 数的是全部行,含已失效的");
    }

    @Test
    void expiredBan_isActiveInTableButNotCountedAsBanned() {
        long past = System.currentTimeMillis() - 60_000L;
        db.replaceActiveBan(new BanEntry("Carol", "staff", past, "已过期", false));

        assertFalse(db.isPlayerBanned("Carol"));
        assertEquals(1, db.getAllActiveBans().size());
        assertEquals(0, db.countActiveBans());
        assertTrue(db.getBans().isEmpty());
    }

    @Test
    void deactivateExpiredBans_flipsActiveFlag() {
        long past = System.currentTimeMillis() - 60_000L;
        db.replaceActiveBan(new BanEntry("Dave", "staff", past, "过期", false));
        assertTrue(db.deactivateExpiredBans());
        assertEquals(0, db.getAllActiveBans().size());
    }

    @Test
    void getBansExpiringBefore_includesOnlyUpcomingAndPermanentExcluded() {
        long now = System.currentTimeMillis();
        db.replaceActiveBan(new BanEntry("Soon", "s", now + 30_000L, "快到期", false));
        db.replaceActiveBan(new BanEntry("Later", "s", now + 600_000L, "还早", false));
        db.replaceActiveBan(new BanEntry("Forever", "s", Long.MAX_VALUE, "永久", false));

        List<BanEntry> expiring = db.getBansExpiringBefore(now + 60_000L);
        assertEquals(1, expiring.size());
        assertEquals("Soon", expiring.get(0).getTarget());
    }

    @Test
    void getMutesExpiringBefore_filtersByDeadline() {
        long now = System.currentTimeMillis();

        db.upsertMute(new MuteEntry("soonmute", "s", now + 10_000L, "快到期"));
        db.upsertMute(new MuteEntry("latermute", "s", now + 600_000L, "还早"));
        db.upsertMute(new MuteEntry("forevermute", "s", Long.MAX_VALUE, "永久"));

        List<MuteEntry> expiring = db.getMutesExpiringBefore(now + 60_000L);
        assertEquals(1, expiring.size());
        assertEquals("soonmute", expiring.get(0).getTarget());

        assertEquals(3, db.countActiveMutes());
    }

    @Test
    void ipBan_roundTripAndExactLookup() {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveIpBan(new BanIpEntry("203.0.113.7", "staff", end, "VPN", false));

        assertTrue(db.isIpBanned("203.0.113.7"));
        assertFalse(db.isIpBanned("203.0.113.8"), "getIpBan 是精确匹配,不含网段");
        assertNotNull(db.getIpBan("203.0.113.7"));
        assertEquals(1, db.getIpBans().size());
        assertEquals(1, db.countActiveIpBans());

        db.deactivateIpBanForUnban("203.0.113.7", System.currentTimeMillis());
        assertFalse(db.isIpBanned("203.0.113.7"));
    }

    @Test
    void ipBans_areSortedByIpToMatchOriginalOrderBy() {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveIpBan(new BanIpEntry("203.0.113.9", "s", end, "r", false));
        db.replaceActiveIpBan(new BanIpEntry("203.0.113.1", "s", end, "r", false));

        List<BanIpEntry> list = db.getIpBans();
        assertEquals("203.0.113.1", list.get(0).getIp());
        assertEquals("203.0.113.9", list.get(1).getIp());
    }

    @Test
    void banCache_reflectsExternalWriteAfterExplicitReload() throws Exception {
        long end = System.currentTimeMillis() + 60_000L;
        assertFalse(db.isPlayerBanned("Foreign"), "先读一次把快照装进缓存");

        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO bans (target, staff, end_time, reason, is_auto, active) VALUES (?, ?, ?, ?, 0, 1)")) {
            ps.setString(1, "Foreign");
            ps.setString(2, "other-server");
            ps.setLong(3, end);
            ps.setString(4, "跨服封禁");
            ps.executeUpdate();
        }
        assertFalse(db.isPlayerBanned("Foreign"), "TTL 内仍读旧快照,这是预期行为");

        db.reloadBanCache();
        assertTrue(db.isPlayerBanned("Foreign"), "显式重载后必须看到外部写入");
    }

    @Test
    void reloadBanCache_picksUpExternalUnban() throws Exception {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Eve", "s", end, "本地封禁", false));
        assertTrue(db.isPlayerBanned("Eve"));

        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE bans SET active = 0 WHERE LOWER(target) = LOWER(?)")) {
            ps.setString(1, "Eve");
            ps.executeUpdate();
        }
        db.reloadBanCache();
        assertFalse(db.isPlayerBanned("Eve"));
    }

    @Test
    void auditChainAndKeysetPagination_preservesOrderAndCompleteness() {
        for (int i = 0; i < 25; i++) {
            assertTrue(db.addAuditLogChained("staff", "封禁", "target" + i, "原因" + i, true));
        }

        List<AuditEntry> collected = new java.util.ArrayList<>();
        long cursor = 0L;
        List<AuditEntry> page;
        while (!(page = db.getAuditLogsAfter(cursor, 10)).isEmpty()) {
            collected.addAll(page);
            cursor = page.get(page.size() - 1).getId();
        }
        assertEquals(25, collected.size());
        for (int i = 1; i < collected.size(); i++) {
            assertTrue(collected.get(i).getId() > collected.get(i - 1).getId(), "id 必须严格递增");
        }
        assertEquals("target0", collected.get(0).getTarget());
        assertEquals("target24", collected.get(24).getTarget());
    }

    @Test
    void auditChain_linksEachRowToPredecessor() {
        db.addAuditLogChained("staff", "封禁", "a", "r", true);
        db.addAuditLogChained("staff", "解封", "b", "r", true);

        List<AuditEntry> rows = db.getAuditLogsAfter(0L, 10);
        assertEquals(2, rows.size());
        String expected = DatabaseManager.hashRow(
                rows.get(0).getPrevHash(), rows.get(0).getTimestamp(), rows.get(0).getActor(),
                rows.get(0).getAction(), rows.get(0).getTarget(), rows.get(0).getReason(), rows.get(0).isSuccess());
        assertEquals(expected, rows.get(1).getPrevHash(), "第二行的 prev_hash 必须是第一行的哈希");
    }

    @Test
    void getAuditLogsByActorInRange_returnsAscending() {
        long from = System.currentTimeMillis() - 1000L;
        db.addAuditLogChained("staff", "封禁", "x", "r", true);
        db.addAuditLogChained("staff", "解封", "x", "r", true);
        List<AuditEntry> logs = db.getAuditLogsByActorInRange("staff", from, System.currentTimeMillis() + 1000L);
        assertEquals(2, logs.size());
        assertTrue(logs.get(0).getId() < logs.get(1).getId());
    }

    @Test
    void getReportsByReporterWithStatus_isPushedDownToSql() {
        long now = System.currentTimeMillis();
        db.upsertReport(new ReportEntry("victim1", "Alice", "理由1", "r1", now, "受理中"));
        db.upsertReport(new ReportEntry("victim2", "Alice", "理由2", "r2", now + 1, "已关闭"));
        db.upsertReport(new ReportEntry("victim3", "Bob", "理由3", "r3", now + 2, "受理中"));

        List<ReportEntry> result = db.getReportsByReporterWithStatus("Alice", "受理中");
        assertEquals(1, result.size());
        assertEquals("r1", result.get(0).getId());
        assertEquals(2, db.getPendingReportCount(), "已关闭的不算待处理");
    }

    @Test
    void cleanupOldData_deletesOldDeletedBansAndReturnsWhetherAnythingHappened() {
        long old = System.currentTimeMillis() - 30L * 86400000L;
        db.replaceActiveBan(new BanEntry("Old", "s", old, "老封禁", false));
        db.deactivateExpiredBans();

        assertTrue(db.cleanupOldData(7), "应确实删掉了过期历史");
        assertFalse(db.cleanupOldData(7), "没有可删的行时应返回 false,免得白跑一次 VACUUM");
    }

    @Test
    void reclaimSpace_survivesBothFreshAndExistingDatabase() throws Exception {
        long now = System.currentTimeMillis();

        for (int i = 0; i < 300; i++) {
            db.replaceActiveBan(new BanEntry("filler" + i, "s", now + 60_000L,
                    "填充".repeat(30) + i, false));
        }
        db.reclaimSpace(); 

        db.close();
        db = new DatabaseManager(plugin);
        db.initialize();

        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE bans SET active = 0, end_time = ?")) {
            ps.setLong(1, now - 1000L);
            ps.executeUpdate();
        }
        assertTrue(db.cleanupOldData(0), "应确实删掉了 300 行");
        db.reclaimSpace(); 

        assertTrue(dbFile.exists());
        assertTrue(db.isHealthy());
        assertTrue(db.getAllActiveBans().isEmpty());
    }

    @Test
    void dataSurvivesReopen() throws Exception {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Persist", "staff", end, "持久化", false));
        db.close();

        db = new DatabaseManager(plugin);
        db.initialize();
        assertTrue(db.isPlayerBanned("Persist"));
        assertEquals("持久化", db.getBan("Persist").getReason());
    }
}

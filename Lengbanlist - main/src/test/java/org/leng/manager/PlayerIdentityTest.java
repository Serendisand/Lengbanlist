package org.leng.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.MuteEntry;
import org.leng.object.PlayerIdentity;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PlayerIdentityTest {

    private static final String UUID_ALICE = "11111111-1111-1111-1111-111111111111";

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

    private int countUuidRows(String table, String uuidColumn, String uuid) throws Exception {
        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + uuidColumn + " = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Test
    void firstLoginRecordsIdentity() {
        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());

        PlayerIdentity identity = db.getIdentityByUuid(UUID_ALICE);
        assertEquals(UUID_ALICE, identity.uuid());
        assertEquals("Alice", identity.name());
        assertEquals(List.of("Alice"), identity.knownNames());
        assertTrue(identity.hasUuid());
    }

    @Test
    void repeatedLoginDoesNotCreateNameHistory() {
        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());
        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());
        db.recordIdentity(UUID_ALICE, "alice", System.currentTimeMillis());

        assertEquals(List.of("alice"), db.getIdentityByUuid(UUID_ALICE).knownNames(),
                "只差大小写的重复登录既不该写曾用名，也不该在曾用名里显示两遍");
        assertEquals("alice", db.getIdentityByUuid(UUID_ALICE).name(), "当前名以最近一次登录为准");
    }

    @Test
    void renameKeepsHistoryAndResolvesBothNames() {
        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());
        db.recordIdentity(UUID_ALICE, "Alicia", System.currentTimeMillis());

        PlayerIdentity identity = db.getIdentityByUuid(UUID_ALICE);
        assertEquals("Alicia", identity.name(), "当前名应更新为新名字");
        assertTrue(identity.knownNames().containsAll(List.of("Alice", "Alicia")));

        assertEquals(UUID_ALICE, db.resolveIdentity("Alicia").uuid());
        assertEquals(UUID_ALICE, db.resolveIdentity("Alice").uuid(), "旧名也要能解析回同一个 UUID");
        assertEquals(UUID_ALICE, db.resolveIdentity("aLiCe").uuid(), "名字解析忽略大小写");
        assertEquals(UUID_ALICE, db.resolveIdentity(UUID_ALICE).uuid(), "直接传 UUID 也要能解析");
        assertTrue(db.resolveIdentity(UUID_ALICE).knownNames().contains("Alice"));
    }

    @Test
    void unknownNameResolvesToNameOnly() {
        PlayerIdentity identity = db.resolveIdentity("Nobody");

        assertFalse(identity.hasUuid(), "没有身份记录时不应编造 UUID");
        assertEquals("Nobody", identity.name());
        assertEquals(List.of("nobody"), identity.lowerNames());
    }

    @Test
    void emptyInputResolvesToEmptyIdentity() {
        assertEquals(PlayerIdentity.EMPTY, db.resolveIdentity(null));
        assertEquals(PlayerIdentity.EMPTY, db.resolveIdentity("   "));
        assertEquals(PlayerIdentity.EMPTY, db.getIdentityByUuid(null));
        assertFalse(PlayerIdentity.EMPTY.hasUuid());
    }

    @Test
    void uuidInputWithoutIdentityFallsBackToName() {
        String unknownUuid = "99999999-9999-9999-9999-999999999999";
        PlayerIdentity identity = db.resolveIdentity(unknownUuid);

        assertFalse(identity.hasUuid());
        assertEquals(unknownUuid, identity.name());
    }

    @Test
    void loginRecordsUuidOnIpRows() throws Exception {
        long now = System.currentTimeMillis();
        db.recordIdentity(UUID_ALICE, "Alice", now);
        db.recordPlayerLoginIp(UUID_ALICE, "Alice", "203.0.113.5", now);

        assertEquals("203.0.113.5", db.getPlayerIp("Alice"));
        assertEquals(1, db.getPlayerIpHistory("Alice").size());
        assertEquals(1, countUuidRows("player_ip_history", "uuid", UUID_ALICE));
        assertEquals(1, countUuidRows("player_ips", "uuid", UUID_ALICE));
    }

    @Test
    void loginBackfillsUuidOnLegacyIpHistoryRows() throws Exception {
        long now = System.currentTimeMillis();
        db.recordPlayerIp("Alice", "203.0.113.5", now);
        assertEquals(0, countUuidRows("player_ip_history", "uuid", UUID_ALICE));

        db.recordIdentity(UUID_ALICE, "Alice", now);
        db.recordPlayerLoginIp(UUID_ALICE, "Alice", "203.0.113.6", now);

        assertEquals(2, countUuidRows("player_ip_history", "uuid", UUID_ALICE), "登录时应顺手补上本人的历史行");
    }

    @Test
    void backfillFillsPunishmentRowsByCurrentAndOldName() throws Exception {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Alice", "staff", end, "旧名封禁", false));
        db.upsertMute(new MuteEntry("Alicia", "staff", end, "新名禁言"));
        assertEquals(0, countUuidRows("bans", "uuid", UUID_ALICE));
        assertEquals(0, countUuidRows("mutes", "uuid", UUID_ALICE));

        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());
        db.recordIdentity(UUID_ALICE, "Alicia", System.currentTimeMillis());
        int updated = db.backfillIdentityUuids(100);

        assertTrue(updated >= 2, "本次应至少补上封禁与禁言两行,实际 " + updated);
        assertEquals(1, countUuidRows("bans", "uuid", UUID_ALICE));
        assertEquals(1, countUuidRows("mutes", "uuid", UUID_ALICE));
    }

    @Test
    void backfillIsIdempotentAndLeavesUnknownNamesAlone() throws Exception {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Alice", "staff", end, "已知", false));
        db.replaceActiveBan(new BanEntry("Ghost", "staff", end, "从未登录", false));
        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());

        assertEquals(1, db.backfillIdentityUuids(100));
        assertEquals(0, db.backfillIdentityUuids(100), "已补齐的行不应被再次统计");
        assertEquals(1, countUuidRows("bans", "uuid", UUID_ALICE));
        assertEquals(1, countUuidRows("bans", "uuid", ""), "解析不到身份的行保持原样");
    }

    @Test
    void backfillWithoutIdentitiesDoesNothing() {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Alice", "staff", end, "无身份记录", false));

        assertEquals(0, db.backfillIdentityUuids(100));
    }

    @Test
    void recordIdentityIgnoresBlankInput() {
        db.recordIdentity(null, "Alice", System.currentTimeMillis());
        db.recordIdentity(UUID_ALICE, null, System.currentTimeMillis());
        db.recordIdentity("  ", "Alice", System.currentTimeMillis());
        db.recordIdentity(UUID_ALICE, "  ", System.currentTimeMillis());

        assertFalse(db.getIdentityByUuid(UUID_ALICE).hasUuid());
        assertEquals(0, db.backfillIdentityUuids(100));
    }

    @Test
    void banQueryMergesOldNameAfterRename() {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Alice", "staff", end, "旧名封禁", false));
        rename(UUID_ALICE, "Alice", "Alicia");

        assertEquals(1, db.getBansByPlayer("Alicia").size(), "改名后按新名应能查到旧名下的封禁");
        assertEquals(1, db.getBansByPlayer("Alice").size(), "按旧名查询同样命中同一条记录");
        assertEquals(1, db.countBanHistory("Alicia"), "统计历史也要跨名归并");
    }

    @Test
    void banQueryFallsBackToNameWithoutIdentity() {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Ghost", "staff", end, "从未登录过", false));

        assertEquals(1, db.getBansByPlayer("ghost").size(), "没有身份记录的名字仍按大小写不敏感的名字命中");
        assertTrue(db.getBansByPlayer("Nobody").isEmpty());
    }

    @Test
    void muteAndWarnQueriesMergeAcrossRename() {
        long end = System.currentTimeMillis() + 60_000L;
        db.upsertMute(new MuteEntry("Alice", "staff", end, "旧名禁言"));
        db.upsertWarning(new WarnEntry("w1", "Alice", "staff", System.currentTimeMillis(), "旧名警告"));
        rename(UUID_ALICE, "Alice", "Alicia");

        assertEquals(1, db.getMutesByPlayer("Alicia").size());
        assertNotNull(db.getMute("Alicia"), "禁言按身份查询时新旧名都要命中");
        assertEquals(1, db.getWarnings("Alicia", false).size());
    }

    @Test
    void reportQueriesMergeAcrossRename() {
        db.upsertReport(new ReportEntry("Bob", "Alice", "作弊", "r1", System.currentTimeMillis(), "未处理"));
        rename(UUID_ALICE, "Alice", "Alicia");

        assertEquals(1, db.getReportsByReporterWithStatus("Alicia", "未处理").size());
        assertEquals(1, db.getReportsByReporterAndTarget("Alicia", "Bob").size());
    }

    @Test
    void ipQueriesMergeAcrossRenameAndKeepLatestFirst() {
        long now = System.currentTimeMillis();
        db.recordPlayerLoginIp(UUID_ALICE, "Alice", "203.0.113.5", now);
        db.recordIdentity(UUID_ALICE, "Alice", now);
        db.recordIdentity(UUID_ALICE, "Alicia", now);
        db.recordPlayerLoginIp(UUID_ALICE, "Alicia", "203.0.113.9", now + 1);

        assertEquals("203.0.113.9", db.getPlayerIp("Alicia"), "取最近一次登录的 IP");
        assertEquals("203.0.113.9", db.getPlayerIp("Alice"));
        assertEquals(2, db.getPlayerIpHistory("Alicia").size(), "两个名字下的 IP 历史要合并");
        assertEquals(2, db.getPlayerIpHistory("Alice").size());
    }

    @Test
    void ambiguousNameIsLeftUnattributedByBackfill() throws Exception {
        long end = System.currentTimeMillis() + 60_000L;
        db.replaceActiveBan(new BanEntry("Shared", "staff", end, "名字被两个身份用过", false));
        db.recordIdentity("11111111-1111-1111-1111-111111111111", "Shared", System.currentTimeMillis());
        db.recordIdentity("22222222-2222-2222-2222-222222222222", "Shared", System.currentTimeMillis());

        assertEquals(0, db.backfillIdentityUuids(100), "同名属于两个身份时不能猜归属");
        assertEquals(1, countUuidRows("bans", "uuid", ""), "猜不出归属的行保持 uuid 为空");
    }

    @Test
    void recordRefreshesResolverCache() {
        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());
        assertEquals(List.of("Alice"), db.getIdentityResolver().resolve("Alice").knownNames());

        db.getIdentityResolver().record(UUID_ALICE, "Alicia", System.currentTimeMillis());

        assertTrue(db.getIdentityResolver().resolve("Alice").knownNames().contains("Alicia"),
                "改名后旧名缓存必须失效，否则曾用名会一直缺最新的那个");
        assertEquals("Alicia", db.getIdentityResolver().resolve(UUID_ALICE).name());
    }

    private void rename(String uuid, String from, String to) {
        db.recordIdentity(uuid, from, System.currentTimeMillis());
        db.recordIdentity(uuid, to, System.currentTimeMillis());
    }
}

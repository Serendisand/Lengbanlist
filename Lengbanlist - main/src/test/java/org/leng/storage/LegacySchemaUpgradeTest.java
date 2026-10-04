package org.leng.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.PlayerIdentity;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class LegacySchemaUpgradeTest {

    private static final String UUID_ALICE = "11111111-1111-1111-1111-111111111111";

    private static final long FUTURE = System.currentTimeMillis() + 600_000L;

    @Mock Lengbanlist plugin;
    @TempDir Path tempDir;

    private File dbFile;
    private DatabaseManager db;

    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("lengbanlist.db").toFile();
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.type", "sqlite");
        config.set("database.sqlite.file", "lengbanlist.db");
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getStorageConfig()).thenReturn(config);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("LegacySchemaUpgradeTest"));
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    private static void execute(File file, List<String> statements) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private DatabaseManager open() throws Exception {
        DatabaseManager opened = new DatabaseManager(plugin);
        opened.initialize();
        return opened;
    }

    private boolean columnExists(String table, String column) throws Exception {
        try (Connection connection = db.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> schemaMeta(String version) {
        return List.of(
                "CREATE TABLE schema_meta (meta_key TEXT PRIMARY KEY, meta_value TEXT NOT NULL)",
                "INSERT INTO schema_meta (meta_key, meta_value) VALUES ('schema.version', '" + version + "')");
    }

    private static List<String> v5Tables() {
        return List.of(
                "CREATE TABLE player_ips (player_name TEXT PRIMARY KEY, ip TEXT NOT NULL, updated_at INTEGER NOT NULL)",
                "CREATE TABLE bans (id INTEGER PRIMARY KEY AUTOINCREMENT, target TEXT NOT NULL, staff TEXT NOT NULL,"
                        + " end_time INTEGER NOT NULL, reason TEXT NOT NULL, is_auto INTEGER NOT NULL DEFAULT 0,"
                        + " active INTEGER NOT NULL DEFAULT 1)",
                "CREATE TABLE ip_bans (id INTEGER PRIMARY KEY AUTOINCREMENT, ip TEXT NOT NULL, staff TEXT NOT NULL,"
                        + " end_time INTEGER NOT NULL, reason TEXT NOT NULL, is_auto INTEGER NOT NULL DEFAULT 0,"
                        + " active INTEGER NOT NULL DEFAULT 1)",
                "CREATE TABLE mutes (target TEXT PRIMARY KEY, staff TEXT NOT NULL, end_time INTEGER NOT NULL,"
                        + " reason TEXT NOT NULL)",
                "CREATE TABLE freezes (target TEXT PRIMARY KEY, staff TEXT NOT NULL, freeze_time INTEGER NOT NULL,"
                        + " reason TEXT NOT NULL)",
                "CREATE TABLE warnings (id TEXT PRIMARY KEY, player TEXT NOT NULL, staff TEXT NOT NULL,"
                        + " warn_time INTEGER NOT NULL, reason TEXT NOT NULL, revoked INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE reports (id TEXT PRIMARY KEY, target TEXT NOT NULL, reporter TEXT NOT NULL,"
                        + " reason TEXT NOT NULL, status VARCHAR(32) NOT NULL DEFAULT '未处理', timestamp INTEGER NOT NULL)",
                "CREATE TABLE audit_log (id INTEGER PRIMARY KEY AUTOINCREMENT, timestamp INTEGER NOT NULL,"
                        + " actor TEXT NOT NULL, action TEXT NOT NULL, target TEXT NOT NULL, reason TEXT NOT NULL,"
                        + " success INTEGER NOT NULL DEFAULT 1, prev_hash VARCHAR(64) NOT NULL DEFAULT '',"
                        + " server TEXT NOT NULL DEFAULT '')",
                "CREATE TABLE player_ip_history (id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + " player_name VARCHAR(191) NOT NULL, ip VARCHAR(191) NOT NULL, first_seen INTEGER NOT NULL,"
                        + " last_seen INTEGER NOT NULL, UNIQUE(player_name, ip))");
    }

    private static List<String> v5Data() {
        return List.of(
                "INSERT INTO bans (target, staff, end_time, reason, is_auto, active)"
                        + " VALUES ('Alice', 'staff', " + FUTURE + ", '旧版封禁', 0, 1)",
                "INSERT INTO mutes (target, staff, end_time, reason)"
                        + " VALUES ('Alice', 'staff', " + FUTURE + ", '旧版禁言')",
                "INSERT INTO warnings (id, player, staff, warn_time, reason, revoked)"
                        + " VALUES ('w1', 'Alice', 'staff', " + System.currentTimeMillis() + ", '旧版警告', 0)",
                "INSERT INTO reports (id, target, reporter, reason, status, timestamp)"
                        + " VALUES ('r1', 'Bob', 'Alice', '旧版举报', '未处理', " + System.currentTimeMillis() + ")",
                "INSERT INTO player_ips (player_name, ip, updated_at)"
                        + " VALUES ('Alice', '203.0.113.5', " + System.currentTimeMillis() + ")",
                "INSERT INTO player_ip_history (player_name, ip, first_seen, last_seen)"
                        + " VALUES ('Alice', '203.0.113.5', " + System.currentTimeMillis() + ", " + System.currentTimeMillis() + ")");
    }

    @Test
    void v5DatabaseUpgradesWithoutLosingPunishments() throws Exception {
        execute(dbFile, concat(schemaMeta("5"), v5Tables(), v5Data()));

        db = open();

        assertEquals(1, db.getBansByPlayer("Alice").size(), "旧封禁必须还在");
        assertEquals("旧版封禁", db.getBansByPlayer("Alice").get(0).getReason());
        assertEquals(1, db.getMutesByPlayer("Alice").size());
        assertEquals(1, db.getWarnings("Alice", false).size());
        assertEquals(1, db.getPendingReports().size());
        assertEquals("203.0.113.5", db.getPlayerIp("Alice"));
        assertEquals(1, db.getPlayerIpHistory("Alice").size());
    }

    @Test
    void v5DatabaseGainsIdentityColumnsAndStaysWritable() throws Exception {
        execute(dbFile, concat(schemaMeta("5"), v5Tables(), v5Data()));
        db = open();

        assertTrue(columnExists("bans", "uuid"), "升级后 bans 应有 uuid 列");
        assertTrue(columnExists("reports", "target_uuid"), "升级后 reports 应有 target_uuid 列");

        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());
        PlayerIdentity identity = db.getIdentityByUuid(UUID_ALICE);
        assertEquals("Alice", identity.name(), "升级后的库要能正常写身份层");

        assertEquals(DatabaseManager.WriteResult.APPLIED,
                db.replaceActiveBan(new BanEntry("Alice", "staff", FUTURE, "新封禁", false)));
        assertTrue(db.isPlayerBannedByUuid(UUID_ALICE), "新写入的封禁要带上 UUID");
    }

    @Test
    void reopenedDatabaseKeepsDataAndDoesNotDuplicateRows() throws Exception {
        execute(dbFile, concat(schemaMeta("5"), v5Tables(), v5Data()));
        db = open();
        db.recordIdentity(UUID_ALICE, "Alice", System.currentTimeMillis());
        db.close();

        db = open();

        assertEquals(1, db.getBansByPlayer("Alice").size(), "再开一次不能把数据搞重复");
        assertEquals(1, db.getMutesByPlayer("Alice").size());
        assertEquals(1, db.getWarnings("Alice", false).size());
        assertEquals(1, db.getPlayerIpHistory("Alice").size());
        assertTrue(db.backfillIdentityUuids(100) > 0, "记录过身份后，历史行应在下一次启动被补上 UUID");
        assertEquals(0, db.backfillIdentityUuids(100), "回填要幂等");
    }

    @Test
    void v6AddsStartTimeColumnsAndBackfillsFromAuditLog() throws Exception {
        long auditAt = System.currentTimeMillis() - 120_000L;
        execute(dbFile, concat(schemaMeta("5"), v5Tables(), v5Data(), List.of(
                "INSERT INTO audit_log (timestamp, actor, action, target, reason, success)"
                        + " VALUES (" + auditAt + ", 'staff', '封禁', 'Alice', '旧版封禁', 1)")));

        db = open();

        assertTrue(columnExists("bans", "start_time"), "v6 迁移应给 bans 加 start_time 列");
        assertTrue(columnExists("ip_bans", "start_time"), "v6 迁移应给 ip_bans 加 start_time 列");
        assertTrue(columnExists("mutes", "start_time"), "v6 迁移应给 mutes 加 start_time 列");
        assertEquals(auditAt, db.getActiveStartTimes("bans").get("alice"), "活跃封禁的起始时间应从审计日志回填");

        long end = System.currentTimeMillis() + 60_000L;
        assertEquals(DatabaseManager.WriteResult.APPLIED,
                db.replaceActiveBan(new BanEntry("Bob", "staff", end, "新封禁", false)));
        Long bobStart = db.getActiveStartTimes("bans").get("bob");
        assertNotNull(bobStart, "升级后的库也要记录新封禁的起始时间");
        assertTrue(end - bobStart > 50_000L, "起始时间应接近写入时刻");
    }

    @Test
    void preV3BanTableIsMigratedToPrimaryKeyForm() throws Exception {
        execute(dbFile, concat(schemaMeta("2"), List.of(
                "CREATE TABLE bans (target TEXT PRIMARY KEY, staff TEXT, end_time INTEGER, reason TEXT,"
                        + " is_auto INTEGER, active INTEGER)",
                "CREATE TABLE ip_bans (ip TEXT PRIMARY KEY, staff TEXT, end_time INTEGER, reason TEXT,"
                        + " is_auto INTEGER, active INTEGER)",
                "INSERT INTO bans (target, staff, end_time, reason, is_auto, active)"
                        + " VALUES ('Alice', 'staff', " + FUTURE + ", '上古封禁', 0, 1)",
                "INSERT INTO ip_bans (ip, staff, end_time, reason, is_auto, active)"
                        + " VALUES ('203.0.113.5', 'staff', " + FUTURE + ", '上古IP封禁', 0, 1)")));

        db = open();

        assertTrue(columnExists("bans", "id"), "v3 迁移应给 bans 加上 id 主键列");
        assertEquals(1, db.getBansByPlayer("Alice").size(), "迁移后旧封禁不能丢");
        assertEquals("上古封禁", db.getBansByPlayer("Alice").get(0).getReason());
        assertTrue(db.getBansByPlayer("Alice").get(0).isActive());
        assertEquals(1, db.getIpBansByIp("203.0.113.5").size());
        assertEquals("203.0.113.5", db.getIpBansByIp("203.0.113.5").get(0).getIp());

        assertNotNull(db.getMeta("schema.version"));
        assertFalse(db.getMeta("schema.version").isEmpty());
    }

    @SafeVarargs
    private static List<String> concat(List<String>... parts) {
        List<String> all = new ArrayList<>();
        for (List<String> part : parts) {
            all.addAll(part);
        }
        return all;
    }
}

package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.object.AuditEntry;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

@SuppressWarnings("SqlResolve")
public class DatabaseManager {

    private static final String ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    public enum WriteResult {
        APPLIED,
        NO_CHANGE,
        DATABASE_ERROR;

        public boolean isApplied() {
            return this == APPLIED;
        }
    }

    private static final long DEFAULT_BAN_CACHE_TTL_MS = 5000L;

    private static final int INDEX_PREFIX_CHARS = 64;

    private final BanCache banCache = new BanCache(new BanCache.Loader() {
        @Override
        public List<BanEntry> loadActiveBans() {
            return queryActiveBans();
        }

        @Override
        public List<BanIpEntry> loadActiveIpBans() {
            return queryActiveIpBans();
        }
    }, DEFAULT_BAN_CACHE_TTL_MS);

    private final Lengbanlist plugin;
    private HikariDataSource dataSource;
    private boolean mysql;

    private boolean sqliteAutoVacuumPending;

    private final Map<String, Set<String>> sqliteColumns = new HashMap<>();
    private final Map<String, Set<String>> sqliteIndexes = new HashMap<>();

    private final Object auditChainLock = new Object();

    public DatabaseManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        String type = plugin.getConfig().getString("database.type", "sqlite");
        if (type == null || type.trim().isEmpty()) {
            type = "sqlite";
        }
        if ("yml".equalsIgnoreCase(type) || "yaml".equalsIgnoreCase(type)) {
            plugin.getLogger().warning("database.type: yml 已废弃，将自动使用 sqlite 并迁移旧 YAML 数据。");
            type = "sqlite";
        }

        if ("sqlite".equalsIgnoreCase(type)) {
            mysql = false;
            String fileName = plugin.getConfig().getString("database.sqlite.file", "lengbanlist.db");
            File dbFile = new File(plugin.getDataFolder(), fileName == null || fileName.trim().isEmpty() ? "lengbanlist.db" : fileName);

            boolean existingDatabase = dbFile.isFile() && dbFile.length() > 0;
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(1);

            config.setConnectionInitSql("PRAGMA foreign_keys = ON");
            dataSource = new HikariDataSource(config);
            execute("PRAGMA journal_mode = WAL");
            execute("PRAGMA busy_timeout = 5000");

            execute("PRAGMA synchronous = " + resolveSqliteSynchronous());

            execute("PRAGMA journal_size_limit = 16777216");

            if (plugin.getConfig().getBoolean("database.sqlite.auto-vacuum", true)) {
                execute("PRAGMA auto_vacuum = INCREMENTAL");
                sqliteAutoVacuumPending = existingDatabase;
            }
        } else if ("mysql".equalsIgnoreCase(type)) {
            mysql = true;
            String host = plugin.getConfig().getString("database.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("database.mysql.port", 3306);
            String database = plugin.getConfig().getString("database.mysql.database", "lengbanlist");
            String username = plugin.getConfig().getString("database.mysql.username", "root");
            String password = plugin.getConfig().getString("database.mysql.password", "");
            if (password == null || password.isEmpty()) {
                throw new SQLException("未配置 MySQL 密码 (database.mysql.password)，请在 config.yml 中显式设置后再启动。");
            }
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8";
            int poolSize = resolveMysqlPoolSize();
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(poolSize);

            config.setMinimumIdle(1);

            config.setPoolName("Lengbanlist-MySQL");
            dataSource = new HikariDataSource(config);
            execute("SELECT 1");
            plugin.getLogger().info("MySQL 连接池已建立，最大连接数 " + poolSize
                    + "（database.mysql.pool-size 可调）");
        } else {
            throw new SQLException("未知 database.type: " + type);
        }

        ensureSchema();
        applyCacheConfig();
    }

    private String resolveSqliteSynchronous() {
        String configured = plugin.getConfig().getString("database.sqlite.synchronous", "NORMAL");
        String upper = configured == null ? "NORMAL" : configured.trim().toUpperCase(Locale.ROOT);
        if (upper.equals("FULL") || upper.equals("NORMAL") || upper.equals("OFF")) {
            return upper;
        }
        plugin.getLogger().warning("database.sqlite.synchronous 取值非法: " + configured
                + "，已按 NORMAL 处理（可选 FULL / NORMAL / OFF）。");
        return "NORMAL";
    }

    private int resolveMysqlPoolSize() {
        int configured = plugin.getConfig().getInt("database.mysql.pool-size", 10);
        if (configured == 10) {
            return 10;
        }
        if (configured < 1) {
            plugin.getLogger().warning("database.mysql.pool-size 配置为 " + configured + "，已按 1 处理。");
            return 1;
        }
        if (configured > 200) {
            plugin.getLogger().warning("database.mysql.pool-size 配置为 " + configured + "，超出上限，已按 200 处理。");
            return 200;
        }
        return configured;
    }

    public void applyCacheConfig() {
        long ttlSeconds = plugin.getConfig().getLong("database.cache.ban-ttl-seconds", 5L);
        banCache.setTtlMillis(ttlSeconds * 1000L);
    }

    public void reloadBanCache() {
        banCache.reload();
    }

    public long getBanCacheTtlMillis() {
        return banCache.getTtlMillis();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public String getDatabaseProductName() {
        try (Connection connection = getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            logSql(e);
            return mysql ? "MySQL" : "SQLite";
        }
    }

    public boolean isMySql() {
        return mysql;
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public void ensureSchema() throws SQLException {
        execute("CREATE TABLE IF NOT EXISTS schema_meta (meta_key " + textPrimaryKey() + ", meta_value " + textType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS player_ips (player_name " + textPrimaryKey() + ", ip " + textType() + " NOT NULL, updated_at " + longType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS bans (id " + integerPrimaryKey() + ", target " + textType() + " NOT NULL, staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, is_auto " + booleanType() + " NOT NULL DEFAULT 0, active " + booleanType() + " NOT NULL DEFAULT 1)");
        execute("CREATE TABLE IF NOT EXISTS ip_bans (id " + integerPrimaryKey() + ", ip " + textType() + " NOT NULL, staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, is_auto " + booleanType() + " NOT NULL DEFAULT 0, active " + booleanType() + " NOT NULL DEFAULT 1)");
        execute("CREATE TABLE IF NOT EXISTS mutes (target " + textPrimaryKey() + ", staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS warnings (id " + textPrimaryKey() + ", player " + textType() + " NOT NULL, staff " + textType() + " NOT NULL, warn_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, revoked " + booleanType() + " NOT NULL DEFAULT 0)");
        execute("CREATE TABLE IF NOT EXISTS reports (id " + textPrimaryKey() + ", target " + textType() + " NOT NULL, reporter " + textType() + " NOT NULL, reason " + textType() + " NOT NULL, status " + varcharType(32) + " NOT NULL DEFAULT '未处理', timestamp " + longType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS audit_log (id " + integerPrimaryKey() + ", timestamp " + longType() + " NOT NULL, actor " + textType() + " NOT NULL, action " + textType() + " NOT NULL, target " + textType() + " NOT NULL, reason " + textType() + " NOT NULL, success " + booleanType() + " NOT NULL DEFAULT 1)");

        addColumnIfMissing("schema_meta", "meta_value", nullableTextType());
        addColumnIfMissing("player_ips", "ip", nullableTextType());
        addColumnIfMissing("player_ips", "updated_at", longType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("bans", "staff", nullableTextType());
        addColumnIfMissing("bans", "end_time", longType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("bans", "reason", nullableTextType());
        addColumnIfMissing("bans", "is_auto", booleanType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("bans", "active", booleanType() + " NOT NULL DEFAULT 1");
        addColumnIfMissing("ip_bans", "staff", nullableTextType());
        addColumnIfMissing("ip_bans", "end_time", longType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("ip_bans", "reason", nullableTextType());
        addColumnIfMissing("ip_bans", "is_auto", booleanType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("ip_bans", "active", booleanType() + " NOT NULL DEFAULT 1");
        addColumnIfMissing("mutes", "staff", nullableTextType());
        addColumnIfMissing("mutes", "end_time", longType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("mutes", "reason", nullableTextType());
        addColumnIfMissing("warnings", "player", nullableTextType());
        addColumnIfMissing("warnings", "staff", nullableTextType());
        addColumnIfMissing("warnings", "warn_time", longType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("warnings", "reason", nullableTextType());
        addColumnIfMissing("warnings", "revoked", booleanType() + " NOT NULL DEFAULT 0");
        addColumnIfMissing("reports", "target", nullableTextType());
        addColumnIfMissing("reports", "reporter", nullableTextType());
        addColumnIfMissing("reports", "reason", nullableTextType());
        addColumnIfMissing("reports", "status", varcharType(32) + " NOT NULL DEFAULT '未处理'");
        addColumnIfMissing("reports", "timestamp", longType() + " NOT NULL DEFAULT 0");

        execute("CREATE TABLE IF NOT EXISTS player_ip_history (id " + integerPrimaryKey() + ", player_name " + varcharType(191) + " NOT NULL, ip " + varcharType(191) + " NOT NULL, first_seen " + longType() + " NOT NULL, last_seen " + longType() + " NOT NULL, UNIQUE(player_name, ip))");

        createIndexIfMissing("warnings", "idx_warnings_player", indexTextColumn("player"));
        createIndexIfMissing("reports", "idx_reports_target", indexTextColumn("target"));
        createIndexIfMissing("reports", "idx_reports_reporter", indexTextColumn("reporter"));
        createIndexIfMissing("audit_log", "idx_audit_log_timestamp", "timestamp");
        createIndexIfMissing("audit_log", "idx_audit_log_actor", indexTextColumn("actor"));
        createIndexIfMissing("audit_log", "idx_audit_log_target", indexTextColumn("target"));

        String currentVersion = getMeta("schema.version");

        SchemaMigrations.runAll(this, currentVersion);
        if (currentVersion == null) {
            setMeta("schema.version", String.valueOf(SchemaMigrations.CURRENT_VERSION));
        }
    }

    void backfillAuditChain() throws SQLException {

        String prevHash = ZERO_HASH;
        AuditEntry prev = null;
        long cursor = 0L;
        List<AuditEntry> batch;
        while (!(batch = getAuditLogsAfter(cursor, 1000)).isEmpty()) {
            for (AuditEntry row : batch) {
                if (prev != null) {
                    prevHash = hashRow(prevHash, prev.getTimestamp(), prev.getActor(), prev.getAction(), prev.getTarget(), prev.getReason(), prev.isSuccess());
                }
                executeUpdate("UPDATE audit_log SET prev_hash = ? WHERE id = ?", prevHash, row.getId());
                prev = row;
            }
            cursor = batch.get(batch.size() - 1).getId();
        }
    }

    Lengbanlist getPlugin() {
        return plugin;
    }

    void migrateBanTableToV3(String table) throws SQLException {
        if (!columnExists(table, "id")) {
            String idCol = mysql ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
            String newTable = table + "_v3";
            String srcCol = table.equals("bans") ? "target" : "ip";

            execute("DROP TABLE IF EXISTS " + newTable);
            execute("CREATE TABLE " + newTable + " (id " + idCol + ", " + srcCol + " " + textType() + " NOT NULL, staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, is_auto " + booleanType() + " NOT NULL DEFAULT 0, active " + booleanType() + " NOT NULL DEFAULT 1)");

            execute("INSERT INTO " + newTable + " (" + srcCol + ", staff, end_time, reason, is_auto, active)"
                    + " SELECT COALESCE(" + srcCol + ", ''), COALESCE(staff, ''), COALESCE(end_time, 0),"
                    + " COALESCE(reason, ''), COALESCE(is_auto, 0), COALESCE(active, 1) FROM " + table);
            execute("DROP TABLE " + table);
            if (mysql) {
                execute("RENAME TABLE " + newTable + " TO " + table);
            } else {
                execute("ALTER TABLE " + newTable + " RENAME TO " + table);
            }
        }

        createIndexIfMissing(table, "idx_" + table + "_target_active", banTableIndexColumns(mysql, table));
    }

    public void upsertPlayerIp(String playerName, String ip, long updatedAt) {
        executeUpdate(upsertSql("player_ips", "player_name", new String[]{"player_name", "ip", "updated_at"}, new String[]{"ip", "updated_at"}), playerName, ip, updatedAt);
    }

    public void recordPlayerLoginIp(String playerName, String ip, long timestamp) {
        Connection connection = null;
        try {
            connection = getConnection();
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        upsertSql("player_ips", "player_name", new String[]{"player_name", "ip", "updated_at"}, new String[]{"ip", "updated_at"}))) {
                    setValues(ps, new Object[]{playerName, ip, timestamp});
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(historyInsertSql())) {
                    setValues(ps, new Object[]{playerName, ip, timestamp, timestamp});
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    logSql(rollbackError);
                }
                logSql(e);
            } finally {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException e) {
                    logSql(e);
                }
            }
        } catch (SQLException e) {
            logSql(e);
        } finally {
            closeConnection(connection);
        }
    }

    private String historyInsertSql() {
        if (mysql) {
            return "INSERT INTO player_ip_history (player_name, ip, first_seen, last_seen) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE last_seen = VALUES(last_seen)";
        }
        return "INSERT INTO player_ip_history (player_name, ip, first_seen, last_seen) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(player_name, ip) DO UPDATE SET last_seen = excluded.last_seen";
    }

    public String getPlayerIp(String playerName) {
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT ip FROM player_ips WHERE player_name = ?")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("ip") : null;
            }
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    public List<String> getPlayersByIp(String ip) {
        List<String> players = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT player_name FROM player_ips WHERE ip = ? ORDER BY player_name")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    players.add(rs.getString("player_name"));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return players;
    }

    public void recordPlayerIp(String playerName, String ip, long timestamp) {
        executeUpdate(historyInsertSql(), playerName, ip, timestamp, timestamp);
    }

    public List<String[]> getPlayerIpHistory(String playerName) {
        List<String[]> history = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT ip, first_seen, last_seen FROM player_ip_history WHERE player_name = ? ORDER BY last_seen DESC")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(new String[]{rs.getString("ip"), String.valueOf(rs.getLong("first_seen")), String.valueOf(rs.getLong("last_seen"))});
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return history;
    }

    public List<String> getPlayersByIpFromHistory(String ip) {
        List<String> players = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT DISTINCT player_name FROM player_ip_history WHERE ip = ? ORDER BY player_name")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    players.add(rs.getString("player_name"));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return players;
    }

    public WriteResult addBan(BanEntry entry) {
        return replaceActiveBan(entry);
    }

    public WriteResult upsertBan(BanEntry entry) {
        return replaceActiveBan(entry);
    }

    public WriteResult replaceActiveBan(BanEntry entry) {
        return invalidateBans(replaceActiveEntry(
                "UPDATE bans SET active = 0 WHERE LOWER(target) = LOWER(?) AND active = 1",
                new Object[]{entry.getTarget()},
                "INSERT INTO bans (target, staff, end_time, reason, is_auto, active) VALUES (?, ?, ?, ?, ?, ?)",
                new Object[]{entry.getTarget(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isAuto(), entry.isActive()}));
    }

    public WriteResult replaceExistingActiveBan(BanEntry entry) {
        return invalidateBans(replaceExistingActiveEntry(
                "UPDATE bans SET active = 0 WHERE LOWER(target) = LOWER(?) AND active = 1",
                new Object[]{entry.getTarget()},
                "INSERT INTO bans (target, staff, end_time, reason, is_auto, active) VALUES (?, ?, ?, ?, ?, ?)",
                new Object[]{entry.getTarget(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isAuto(), entry.isActive()}));
    }

    public WriteResult replaceActiveBanAndUpdateReport(BanEntry banEntry, ReportEntry reportEntry,
                                                       String reportStatus) {
        return invalidateBans(replaceActiveEntry(
                "UPDATE bans SET active = 0 WHERE LOWER(target) = LOWER(?) AND active = 1",
                new Object[]{banEntry.getTarget()},
                "INSERT INTO bans (target, staff, end_time, reason, is_auto, active) VALUES (?, ?, ?, ?, ?, ?)",
                new Object[]{banEntry.getTarget(), banEntry.getStaff(), banEntry.getTime(), banEntry.getReason(),
                        banEntry.isAuto(), banEntry.isActive()},
                "UPDATE reports SET status = ? WHERE id = ? AND status = ?",
                new Object[]{status(reportStatus), reportEntry.getId(), status(reportEntry.getStatus())}));
    }

    public WriteResult deactivateBanForUnban(String target, long now) {
        return invalidateBans(deactivateForUnban(
                "UPDATE bans SET active = 0 WHERE LOWER(target) = LOWER(?) AND active = 1 AND end_time > ?",
                "UPDATE bans SET active = 0 WHERE LOWER(target) = LOWER(?) AND active = 1 AND end_time <= ?",
                target, now));
    }

    public void deleteBan(String target) {
        executeUpdate("DELETE FROM bans WHERE LOWER(target) = LOWER(?)", target);
        banCache.invalidate();
    }

    private WriteResult invalidateBans(WriteResult result) {
        if (result != WriteResult.NO_CHANGE) {
            banCache.invalidate();
        }
        return result;
    }

    public boolean isPlayerBanned(String target) {
        return banCache.isBanned(target);
    }

    public BanEntry getBan(String target) {
        return banCache.getBan(target);
    }

    public List<BanEntry> getBans() {
        return banCache.getUnexpiredBans();
    }

    public List<BanEntry> getBansByPlayer(String player) {
        List<BanEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason, is_auto, active FROM bans WHERE LOWER(target) = LOWER(?) ORDER BY end_time DESC")) {
            ps.setString(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readBan(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public List<BanEntry> getRecentBans(int limit) {
        List<BanEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason, is_auto, active FROM bans ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readBan(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public int countBanHistory(String target) {
        return count("SELECT COUNT(*) FROM bans WHERE LOWER(target) = LOWER(?)", target);
    }

    public List<BanEntry> getAllActiveBans() {
        return banCache.getActiveBans();
    }

    public int countActiveBans() {
        return banCache.countUnexpiredBans();
    }

    public int countActiveIpBans() {
        return banCache.countUnexpiredIpBans();
    }

    private List<BanEntry> queryActiveBans() {
        List<BanEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason, is_auto, active FROM bans WHERE active = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readBan(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    private List<BanIpEntry> queryActiveIpBans() {
        List<BanIpEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT ip, staff, end_time, reason, is_auto, active FROM ip_bans WHERE active = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readIpBan(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public boolean isHealthy() {
        return dataSource != null && !dataSource.isClosed();
    }

    public WriteResult addIpBan(BanIpEntry entry) {
        return replaceActiveIpBan(entry);
    }

    public WriteResult upsertIpBan(BanIpEntry entry) {
        return replaceActiveIpBan(entry);
    }

    public WriteResult replaceActiveIpBan(BanIpEntry entry) {
        return invalidateBans(replaceActiveEntry(
                "UPDATE ip_bans SET active = 0 WHERE ip = ? AND active = 1",
                new Object[]{entry.getIp()},
                "INSERT INTO ip_bans (ip, staff, end_time, reason, is_auto, active) VALUES (?, ?, ?, ?, ?, ?)",
                new Object[]{entry.getIp(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isAuto(), entry.isActive()}));
    }

    public WriteResult replaceExistingActiveIpBan(BanIpEntry entry) {
        return invalidateBans(replaceExistingActiveEntry(
                "UPDATE ip_bans SET active = 0 WHERE ip = ? AND active = 1",
                new Object[]{entry.getIp()},
                "INSERT INTO ip_bans (ip, staff, end_time, reason, is_auto, active) VALUES (?, ?, ?, ?, ?, ?)",
                new Object[]{entry.getIp(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isAuto(), entry.isActive()}));
    }

    public WriteResult deactivateIpBanForUnban(String ip, long now) {
        return invalidateBans(deactivateForUnban(
                "UPDATE ip_bans SET active = 0 WHERE ip = ? AND active = 1 AND end_time > ?",
                "UPDATE ip_bans SET active = 0 WHERE ip = ? AND active = 1 AND end_time <= ?",
                ip, now));
    }

    public void deleteIpBan(String ip) {
        executeUpdate("DELETE FROM ip_bans WHERE ip = ?", ip);
        banCache.invalidate();
    }

    public boolean isIpBanned(String ip) {
        return banCache.isIpBanned(ip);
    }

    public BanIpEntry getIpBan(String ip) {
        return banCache.getIpBan(ip);
    }

    public List<BanIpEntry> getIpBans() {
        return banCache.getUnexpiredIpBans();
    }

    public List<BanIpEntry> getIpBansByIp(String ip) {
        List<BanIpEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT ip, staff, end_time, reason, is_auto, active FROM ip_bans WHERE ip = ? ORDER BY end_time DESC")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readIpBan(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public int countIpBanHistory(String ip) {
        return count("SELECT COUNT(*) FROM ip_bans WHERE ip = ?", ip);
    }

    public void upsertMute(MuteEntry entry) {
        MuteEntry normalized = new MuteEntry(entry.getTarget().toLowerCase(), entry.getStaff(), entry.getTime(), entry.getReason());
        executeUpdate(upsertSql("mutes", "target", new String[]{"target", "staff", "end_time", "reason"}, new String[]{"staff", "end_time", "reason"}), normalized.getTarget(), normalized.getStaff(), normalized.getTime(), normalized.getReason());
    }

    public void deleteMute(String target) {
        executeUpdate("DELETE FROM mutes WHERE LOWER(target) = LOWER(?)", target);
    }

    public void deleteMuteIfExpiresAt(String target, long endTime) {
        executeUpdate("DELETE FROM mutes WHERE LOWER(target) = LOWER(?) AND end_time = ?", target, endTime);
    }

    public MuteEntry getMute(String target) {
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason FROM mutes WHERE LOWER(target) = LOWER(?)")) {
            ps.setString(1, target);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readMute(rs) : null;
            }
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    public List<MuteEntry> getMutes() {
        try {
            return loadMutesForCache();
        } catch (SQLException e) {
            logSql(e);
            return new ArrayList<>();
        }
    }

    public int countActiveMutes() {
        return count("SELECT COUNT(*) FROM mutes WHERE end_time = ? OR end_time > ?", Long.MAX_VALUE, System.currentTimeMillis());
    }

    List<MuteEntry> loadMutesForCache() throws SQLException {
        List<MuteEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason FROM mutes WHERE end_time = ? OR end_time > ? ORDER BY target")) {
            ps.setLong(1, Long.MAX_VALUE);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readMute(rs));
                }
            }
        }
        return entries;
    }

    public List<MuteEntry> getMutesByPlayer(String player) {
        List<MuteEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason FROM mutes WHERE LOWER(target) = LOWER(?) ORDER BY end_time DESC")) {
            ps.setString(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readMute(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public List<MuteEntry> getAllMutes() {
        List<MuteEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason FROM mutes")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readMute(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public void upsertWarning(WarnEntry entry) {
        executeUpdate(upsertSql("warnings", "id", new String[]{"id", "player", "staff", "warn_time", "reason", "revoked"}, new String[]{"player", "staff", "warn_time", "reason", "revoked"}), entry.getId(), entry.getPlayer(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isRevoked());
    }

    public void updateWarningRevoked(String id, boolean revoked) {
        executeUpdate("UPDATE warnings SET revoked = ? WHERE id = ?", revoked, id);
    }

    public List<WarnEntry> getWarnings(String player, boolean activeOnly) {
        List<WarnEntry> entries = new ArrayList<>();
        String sql = "SELECT id, player, staff, warn_time, reason, revoked FROM warnings WHERE LOWER(player) = LOWER(?)" + (activeOnly ? " AND revoked = 0" : "") + " ORDER BY warn_time ASC, id ASC";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readWarning(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public List<String> getWarnedPlayers() {
        List<String> players = new ArrayList<>();
        try (Connection connection = getConnection(); Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT player FROM warnings")) {
            while (rs.next()) {
                players.add(rs.getString("player"));
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return players;
    }

    public void upsertReport(ReportEntry entry) {
        executeUpdate(upsertSql("reports", "id", new String[]{"id", "target", "reporter", "reason", "status", "timestamp"}, new String[]{"target", "reporter", "reason", "status", "timestamp"}), entry.getId(), entry.getTarget(), entry.getReporter(), entry.getReason(), status(entry.getStatus()), entry.getTimestamp());
    }

    public void deleteReport(String id) {
        executeUpdate("DELETE FROM reports WHERE id = ?", id);
    }

    public ReportEntry getReport(String id) {
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readReport(rs) : null;
            }
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    public List<ReportEntry> getPendingReports() {
        List<ReportEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE status IS NULL OR (status <> ? AND status <> ?) ORDER BY timestamp ASC")) {
            ps.setString(1, "已关闭");
            ps.setString(2, "已处理");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readReport(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public List<ReportEntry> getReportsByReporterWithStatus(String reporter, String reportStatus) {
        List<ReportEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE reporter = ? AND status = ? ORDER BY timestamp ASC")) {
            ps.setString(1, reporter);
            ps.setString(2, status(reportStatus));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readReport(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public int getPendingReportCount() {
        return count("SELECT COUNT(*) FROM reports WHERE status IS NULL OR (status <> ? AND status <> ?)", "已关闭", "已处理");
    }

    public int getReportCount(String target) {
        return count("SELECT COUNT(*) FROM reports WHERE target = ?", target);
    }

    public List<ReportEntry> getReportsByReporterAndTarget(String reporter, String target) {
        List<ReportEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE reporter = ? AND target = ? ORDER BY timestamp DESC")) {
            ps.setString(1, reporter);
            ps.setString(2, target);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readReport(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public boolean addAuditLog(String actor, String action, String target, String reason, boolean success) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("INSERT INTO audit_log (timestamp, actor, action, target, reason, success) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, actor);
            ps.setString(3, action);
            ps.setString(4, target);
            ps.setString(5, reason);
            ps.setBoolean(6, success);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logSql(e);
            return false;
        }
    }

    public boolean addAuditLogChained(String actor, String action, String target, String reason, boolean success) {
        synchronized (auditChainLock) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                String prevHash = ZERO_HASH;
                try (PreparedStatement ps = connection.prepareStatement("SELECT id, timestamp, actor, action, target, reason, success, prev_hash FROM audit_log ORDER BY id DESC LIMIT 1")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            prevHash = hashRow(value(rs, "prev_hash"), rs.getLong("timestamp"), value(rs, "actor"), value(rs, "action"), value(rs, "target"), value(rs, "reason"), rs.getBoolean("success"));
                        }
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO audit_log (timestamp, actor, action, target, reason, success, prev_hash) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, actor == null ? "" : actor);
                    ps.setString(3, action == null ? "" : action);
                    ps.setString(4, target == null ? "" : target);
                    ps.setString(5, reason == null ? "" : reason);
                    ps.setBoolean(6, success);
                    ps.setString(7, prevHash);
                    ps.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    logSql(rollbackError);
                }
                logSql(e);
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logSql(e);
            return false;
        }
        } 
    }

    public List<AuditEntry> getAuditLogsAfter(long afterId, int limit) {
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT id, timestamp, actor, action, target, reason, success, prev_hash FROM audit_log WHERE id > ? ORDER BY id ASC LIMIT ?")) {
            ps.setLong(1, afterId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readAudit(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public static String hashRow(String prevHash, long timestamp, String actor, String action, String target, String reason, boolean success) {
        String safePrevHash = prevHash == null ? "" : prevHash;
        String safeActor = actor == null ? "" : actor;
        String safeAction = action == null ? "" : action;
        String safeTarget = target == null ? "" : target;
        String safeReason = reason == null ? "" : reason;
        String data = safePrevHash + timestamp + "[" + safeActor.length() + "]" + safeActor + "[" + safeAction.length() + "]" + safeAction + "[" + safeTarget.length() + "]" + safeTarget + "[" + safeReason.length() + "]" + safeReason + "[" + (success ? 1 : 0) + "]";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public List<AuditEntry> getAuditLogs(String actorOrTarget, int limit) {
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection()) {
            if (actorOrTarget == null || actorOrTarget.isEmpty()) {
                try (PreparedStatement ps = connection.prepareStatement("SELECT id, timestamp, actor, action, target, reason, success, prev_hash FROM audit_log ORDER BY timestamp DESC LIMIT ?")) {
                    ps.setInt(1, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) entries.add(readAudit(rs));
                    }
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement("SELECT id, timestamp, actor, action, target, reason, success, prev_hash FROM audit_log WHERE actor = ? OR target = ? ORDER BY timestamp DESC LIMIT ?")) {
                    ps.setString(1, actorOrTarget);
                    ps.setString(2, actorOrTarget);
                    ps.setInt(3, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) entries.add(readAudit(rs));
                    }
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public List<AuditEntry> getAuditLogsByActor(String actor, int limit) {
        if (actor == null || actor.isEmpty()) {
            return getAuditLogs(null, limit);
        }
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT id, timestamp, actor, action, target, reason, success, prev_hash FROM audit_log WHERE actor = ? ORDER BY timestamp DESC LIMIT ?")) {
                ps.setString(1, actor);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) entries.add(readAudit(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public List<AuditEntry> getAuditLogsByActorInRange(String actor, long from, long to) {
        List<AuditEntry> entries = new ArrayList<>();
        if (actor == null || actor.trim().isEmpty()) {
            return entries;
        }
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(
                "SELECT id, timestamp, actor, action, target, reason, success, prev_hash FROM audit_log " +
                        "WHERE actor = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp ASC, id ASC")) {
            ps.setString(1, actor.trim());
            ps.setLong(2, from);
            ps.setLong(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) entries.add(readAudit(rs));
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    private AuditEntry readAudit(ResultSet rs) throws SQLException {
        return new AuditEntry(rs.getLong("id"), rs.getLong("timestamp"), value(rs, "actor"), value(rs, "action"), value(rs, "target"), value(rs, "reason"), rs.getBoolean("success"), value(rs, "prev_hash"));
    }

    public String getMeta(String key) {
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT meta_value FROM schema_meta WHERE meta_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("meta_value") : null;
            }
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    public void setMeta(String key, String value) {
        executeUpdate(upsertSql("schema_meta", "meta_key", new String[]{"meta_key", "meta_value"}, new String[]{"meta_value"}), key, value);
    }

    private BanEntry readBan(ResultSet rs) throws SQLException {
        return new BanEntry(value(rs, "target"), value(rs, "staff"), rs.getLong("end_time"), value(rs, "reason"), rs.getBoolean("is_auto"), rs.getBoolean("active"));
    }

    private BanIpEntry readIpBan(ResultSet rs) throws SQLException {
        return new BanIpEntry(value(rs, "ip"), value(rs, "staff"), rs.getLong("end_time"), value(rs, "reason"), rs.getBoolean("is_auto"), rs.getBoolean("active"));
    }

    public List<BanEntry> getBansExpiringBefore(long deadline) {
        List<BanEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason, is_auto, active FROM bans WHERE active = 1 AND end_time <= ? ORDER BY end_time ASC")) {
            ps.setLong(1, deadline);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readBan(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public List<MuteEntry> getMutesExpiringBefore(long deadline) {
        List<MuteEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT target, staff, end_time, reason FROM mutes WHERE end_time <= ? ORDER BY end_time ASC")) {
            ps.setLong(1, deadline);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(readMute(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return entries;
    }

    public boolean cleanupOldData(int retentionDays) {
        long cutoff = System.currentTimeMillis() - (retentionDays * 86400000L);
        boolean removed = false;
        removed |= executeUpdateAffected("DELETE FROM bans WHERE active = 0 AND end_time < ?", cutoff) > 0;
        removed |= executeUpdateAffected("DELETE FROM ip_bans WHERE active = 0 AND end_time < ?", cutoff) > 0;
        removed |= executeUpdateAffected("DELETE FROM mutes WHERE end_time != " + Long.MAX_VALUE + " AND end_time < ?", cutoff) > 0;
        removed |= executeUpdateAffected("DELETE FROM warnings WHERE revoked = 1 AND warn_time < ?", cutoff) > 0;
        removed |= executeUpdateAffected("DELETE FROM reports WHERE status != '未处理' AND timestamp < ?", cutoff) > 0;
        removed |= executeUpdateAffected("DELETE FROM audit_log WHERE timestamp < ?", cutoff) > 0;
        int ipHistoryDays = plugin.getConfig().getInt("database.retention.ip-history-days", 0);
        if (ipHistoryDays > 0) {
            long ipCutoff = System.currentTimeMillis() - (ipHistoryDays * 86400000L);
            removed |= executeUpdateAffected("DELETE FROM player_ip_history WHERE last_seen < ?", ipCutoff) > 0;
        }
        if (removed) {
            banCache.invalidate();
        }
        return removed;
    }

    public boolean deactivateExpiredBans() {
        long now = System.currentTimeMillis();
        boolean changed = executeUpdateAffected("UPDATE bans SET active = 0 WHERE active = 1 AND end_time <= ? AND end_time != " + Long.MAX_VALUE, now) > 0;
        changed |= executeUpdateAffected("UPDATE ip_bans SET active = 0 WHERE active = 1 AND end_time <= ? AND end_time != " + Long.MAX_VALUE, now) > 0;
        if (changed) {
            banCache.invalidate();
        }
        return changed;
    }

    public void reclaimSpace() {
        if (mysql || dataSource == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("database.sqlite.auto-vacuum", true)) {
            return;
        }
        try {
            long freePages = pragmaLong("freelist_count");
            if (sqliteAutoVacuumPending) {

                if (freePages <= 0) {
                    return;
                }
                long start = System.currentTimeMillis();
                plugin.getLogger().info("SQLite 首次空间整理开始（重建数据库文件以启用 auto_vacuum）...");
                convertToIncrementalAutoVacuum();
                sqliteAutoVacuumPending = false;
                plugin.getLogger().info("SQLite 首次空间整理完成，耗时 " + (System.currentTimeMillis() - start) + " 毫秒");
                return;
            }
            if (freePages > 0) {

                execute("PRAGMA incremental_vacuum(2000)");
            }
        } catch (SQLException e) {
            logSql(e);
        }
    }

    private void convertToIncrementalAutoVacuum() throws SQLException {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA auto_vacuum = INCREMENTAL");
            statement.execute("VACUUM");
        }
    }

    private long pragmaLong(String pragma) {
        String value = firstPragmaValue(pragma);
        if (value == null) {
            return -1L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String firstPragmaValue(String pragma) {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA " + pragma)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            logSql(e);
            return null;
        }
    }

    private MuteEntry readMute(ResultSet rs) throws SQLException {
        return new MuteEntry(value(rs, "target"), value(rs, "staff"), rs.getLong("end_time"), value(rs, "reason"));
    }

    private WarnEntry readWarning(ResultSet rs) throws SQLException {
        WarnEntry entry = new WarnEntry(value(rs, "id"), value(rs, "player"), value(rs, "staff"), rs.getLong("warn_time"), value(rs, "reason"));
        if (rs.getBoolean("revoked")) {
            entry = entry.revoke();
        }
        return entry;
    }

    private ReportEntry readReport(ResultSet rs) throws SQLException {
        return new ReportEntry(value(rs, "target"), value(rs, "reporter"), value(rs, "reason"), value(rs, "id"), rs.getLong("timestamp"), status(rs.getString("status")));
    }

    private String value(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    private String upsertSql(String table, String keyColumn, String[] columns, String[] updateColumns) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(table).append(" (").append(join(columns)).append(") VALUES (").append(placeholders(columns.length)).append(") ");
        if (mysql) {
            sql.append("ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < updateColumns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(updateColumns[i]).append(" = VALUES(").append(updateColumns[i]).append(")");
            }
        } else {
            sql.append("ON CONFLICT(").append(keyColumn).append(") DO UPDATE SET ");
            for (int i = 0; i < updateColumns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(updateColumns[i]).append(" = excluded.").append(updateColumns[i]);
            }
        }
        return sql.toString();
    }

    private WriteResult replaceActiveEntry(String deactivateSql, Object[] deactivateValues,
                                           String insertSql, Object[] insertValues) {
        return replaceActiveEntry(
                deactivateSql, deactivateValues, insertSql, insertValues, false, null, null);
    }

    private WriteResult replaceExistingActiveEntry(String deactivateSql, Object[] deactivateValues,
                                                   String insertSql, Object[] insertValues) {
        return replaceActiveEntry(
                deactivateSql, deactivateValues, insertSql, insertValues, true, null, null);
    }

    private WriteResult replaceActiveEntry(String deactivateSql, Object[] deactivateValues,
                                           String insertSql, Object[] insertValues,
                                           String followUpSql, Object[] followUpValues) {
        return replaceActiveEntry(
                deactivateSql, deactivateValues, insertSql, insertValues, false,
                followUpSql, followUpValues);
    }

    private WriteResult replaceActiveEntry(String deactivateSql, Object[] deactivateValues,
                                           String insertSql, Object[] insertValues,
                                           boolean requireExistingActive,
                                           String followUpSql, Object[] followUpValues) {
        Connection connection = null;
        try {
            connection = getConnection();
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean autoCommitChanged = false;
            boolean restoreAutoCommit = false;
            try {
                connection.setAutoCommit(false);
                autoCommitChanged = true;
                try (PreparedStatement deactivate = connection.prepareStatement(deactivateSql);
                     PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    setValues(deactivate, deactivateValues);
                    int deactivateCount = deactivate.executeUpdate();
                    setValues(insert, insertValues);
                    int insertCount = insert.executeUpdate();
                    if (insertCount != 1) {
                        connection.rollback();
                        restoreAutoCommit = true;
                        return WriteResult.DATABASE_ERROR;
                    }
                    if (requireExistingActive && deactivateCount == 0) {
                        connection.rollback();
                        restoreAutoCommit = true;
                        return WriteResult.NO_CHANGE;
                    }
                }
                if (followUpSql != null) {
                    try (PreparedStatement followUp = connection.prepareStatement(followUpSql)) {
                        setValues(followUp, followUpValues);
                        if (followUp.executeUpdate() == 0) {
                            connection.rollback();
                            restoreAutoCommit = true;
                            return WriteResult.NO_CHANGE;
                        }
                    }
                }
                connection.commit();
                restoreAutoCommit = true;
                return WriteResult.APPLIED;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                    restoreAutoCommit = true;
                } catch (SQLException rollbackError) {
                    logSql(rollbackError);
                }
                logSql(e);
                return WriteResult.DATABASE_ERROR;
            } finally {
                if (autoCommitChanged && restoreAutoCommit) {
                    try {
                        connection.setAutoCommit(originalAutoCommit);
                    } catch (SQLException e) {
                        logSql(e);
                    }
                }
            }
        } catch (SQLException e) {
            logSql(e);
            return WriteResult.DATABASE_ERROR;
        } finally {
            closeConnection(connection);
        }
    }

    private WriteResult deactivateForUnban(String effectiveSql, String expiredSql,
                                           Object... values) {
        Connection connection = null;
        try {
            connection = getConnection();
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean autoCommitChanged = false;
            boolean restoreAutoCommit = false;
            try {
                connection.setAutoCommit(false);
                autoCommitChanged = true;
                int effectiveCount;
                try (PreparedStatement effective = connection.prepareStatement(effectiveSql);
                     PreparedStatement expired = connection.prepareStatement(expiredSql)) {
                    setValues(effective, values);
                    effectiveCount = effective.executeUpdate();
                    setValues(expired, values);
                    expired.executeUpdate();
                }
                connection.commit();
                restoreAutoCommit = true;
                return effectiveCount > 0 ? WriteResult.APPLIED : WriteResult.NO_CHANGE;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                    restoreAutoCommit = true;
                } catch (SQLException rollbackError) {
                    logSql(rollbackError);
                }
                logSql(e);
                return WriteResult.DATABASE_ERROR;
            } finally {
                if (autoCommitChanged && restoreAutoCommit) {
                    try {
                        connection.setAutoCommit(originalAutoCommit);
                    } catch (SQLException e) {
                        logSql(e);
                    }
                }
            }
        } catch (SQLException e) {
            logSql(e);
            return WriteResult.DATABASE_ERROR;
        } finally {
            closeConnection(connection);
        }
    }

    private void closeStatement(PreparedStatement statement) {
        if (statement == null) {
            return;
        }
        try {
            statement.close();
        } catch (SQLException e) {
            logSql(e);
        }
    }

    private void closeConnection(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            logSql(e);
        }
    }

    private void executeUpdate(String sql, Object... values) {
        executeUpdateAffected(sql, values);
    }

    private int executeUpdateAffected(String sql, Object... values) {
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            setValues(ps, values);
            return ps.executeUpdate();
        } catch (SQLException e) {
            logSql(e);
            return -1;
        }
    }

    private int count(String sql, Object... values) {
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            setValues(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            logSql(e);
            return 0;
        }
    }

    private void setValues(PreparedStatement ps, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Boolean) {
                ps.setBoolean(i + 1, (Boolean) value);
            } else {
                ps.setObject(i + 1, value);
            }
        }
    }

    void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        if (!columnExists(table, column)) {
            execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        if (!mysql) {
            return sqliteColumnsOf(table).contains(column.toLowerCase(Locale.ROOT));
        }
        return mysqlCatalogHas("COLUMNS", "COLUMN_NAME", table, column);
    }

    private Set<String> sqliteColumnsOf(String table) throws SQLException {
        Set<String> cached = sqliteColumns.get(table);
        if (cached != null) {
            return cached;
        }
        Set<String> columns = new HashSet<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        sqliteColumns.put(table, columns);
        return columns;
    }

    private String indexTextColumn(String column) {
        return indexTextColumn(mysql, column);
    }

    static String indexTextColumn(boolean mySql, String column) {
        return mySql ? column + "(" + INDEX_PREFIX_CHARS + ")" : column;
    }

    static String banTableIndexColumns(boolean mySql, String table) {
        return indexTextColumn(mySql, table.equals("bans") ? "target" : "ip") + ", active";
    }

    private void createIndexIfMissing(String table, String index, String column) throws SQLException {
        if (indexExists(table, index)) {
            return;
        }
        try {
            execute("CREATE INDEX " + index + " ON " + table + " (" + column + ")");
        } catch (SQLException e) {

            if (indexExists(table, index)) {
                return;
            }
            throw e;
        }
    }

    private boolean indexExists(String table, String index) throws SQLException {
        if (!mysql) {
            return sqliteIndexesOf(table).contains(index.toLowerCase(Locale.ROOT));
        }
        return mysqlCatalogHas("STATISTICS", "INDEX_NAME", table, index);
    }

    private boolean mysqlCatalogHas(String catalogTable, String nameColumn, String table, String name) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema." + catalogTable
                + " WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = LOWER(?)"
                + " AND LOWER(" + nameColumn + ") = LOWER(?)";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private Set<String> sqliteIndexesOf(String table) throws SQLException {
        Set<String> cached = sqliteIndexes.get(table);
        if (cached != null) {
            return cached;
        }
        Set<String> indexes = new HashSet<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA index_list(" + table + ")")) {
            while (rs.next()) {
                indexes.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        sqliteIndexes.put(table, indexes);
        return indexes;
    }

    void execute(String sql) throws SQLException {

        sqliteColumns.clear();
        sqliteIndexes.clear();
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String textPrimaryKey() {
        return mysql ? "VARCHAR(191) PRIMARY KEY" : "TEXT PRIMARY KEY";
    }

    private String textType() {
        return mysql ? "TEXT" : "TEXT";
    }

    String varcharType(int length) {
        return mysql ? "VARCHAR(" + length + ")" : "TEXT";
    }

    private String nullableTextType() {
        return mysql ? "TEXT" : "TEXT NOT NULL DEFAULT ''";
    }

    private String longType() {
        return mysql ? "BIGINT" : "INTEGER";
    }

    private String booleanType() {
        return mysql ? "BOOLEAN" : "INTEGER";
    }

    private String integerPrimaryKey() {
        return mysql ? "INT AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
    }

    private String placeholders(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(", ");
            builder.append("?");
        }
        return builder.toString();
    }

    private String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) builder.append(", ");
            builder.append(values[i]);
        }
        return builder.toString();
    }

    private String status(String status) {
        return status == null || status.trim().isEmpty() ? "未处理" : status;
    }

    private void logSql(SQLException e) {
        plugin.getLogger().log(Level.SEVERE, "数据库操作失败", e);
    }

}

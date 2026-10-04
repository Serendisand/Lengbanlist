package org.leng.storage;

import org.leng.Lengbanlist;
import org.leng.object.AppealEntry;
import org.leng.object.AuditEntry;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.FreezeEntry;
import org.leng.object.MuteEntry;
import org.leng.object.PlayerIdentity;
import org.leng.object.ReportEntry;
import org.leng.object.SyncEvent;
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

@SuppressWarnings("SqlResolve")
public class DatabaseManager {

    private static final String ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private static final String AUDIT_TAIL_KEY = "audit.tail";

    private static final String AUDIT_SELECT =
            "SELECT id, timestamp, actor, action, target, reason, success, prev_hash, server FROM audit_log ";

    private static final String WARN_SELECT =
            "SELECT id, player, staff, warn_time, reason, revoked FROM warnings ";

    private static final String BAN_INSERT =
            "INSERT INTO bans (target, uuid, staff, end_time, reason, is_auto, active, start_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String STATUS_PENDING = "未处理";
    private static final String STATUS_CLOSED = "已关闭";
    private static final String STATUS_HANDLED = "已处理";

    public enum WriteResult {
        APPLIED,
        NO_CHANGE,
        DATABASE_ERROR;

        public boolean isApplied() {
            return this == APPLIED;
        }
    }

    private static final long DEFAULT_BAN_CACHE_TTL_MS = 5000L;

    private final BanCache banCache;

    private final WarnCache warnCache;

    private final PlayerIdentityResolver identityResolver;

    private final Lengbanlist plugin;
    private HikariDataSource dataSource;
    private DatabaseDialect dialect = DatabaseDialect.SQLITE;
    private boolean auditTailEnsured;

    private boolean sqliteAutoVacuumPending;

    private final Map<String, Set<String>> sqliteColumns = new HashMap<>();
    private final Map<String, Set<String>> sqliteIndexes = new HashMap<>();

    private final Object auditChainLock = new Object();

    public DatabaseManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.identityResolver = new PlayerIdentityResolver(this);
        this.banCache = new BanCache(new BanCache.Loader() {
            @Override
            public List<BanCache.BanRow> loadActiveBans() {
                return queryActiveBans();
            }

            @Override
            public List<BanIpEntry> loadActiveIpBans() {
                return queryActiveIpBans();
            }
        }, DEFAULT_BAN_CACHE_TTL_MS, plugin.getLogger());
        this.warnCache = new WarnCache(this::loadWarnsForCache, DEFAULT_BAN_CACHE_TTL_MS, plugin.getLogger());
    }

    public void initialize() throws SQLException {
        String type = plugin.getStorageConfig().getString("database.type", "sqlite");
        if (type == null || type.trim().isEmpty()) {
            type = "sqlite";
        }
        if ("yml".equalsIgnoreCase(type) || "yaml".equalsIgnoreCase(type)) {
            plugin.getLogger().warning("database.type: yml 已废弃，将自动使用 sqlite 并迁移旧 YAML 数据。");
            type = "sqlite";
        }
        DatabaseDialect parsed = DatabaseDialect.parse(type);
        if (parsed == null) {
            throw new SQLException("未知 database.type: " + type + "（可选 sqlite / mysql / mariadb / postgresql）");
        }
        dialect = parsed;

        if (dialect == DatabaseDialect.SQLITE) {
            initializeSqlite();
        } else {
            initializeNetwork();
        }

        ensureSchema();
        applyCacheConfig();
    }

    private void initializeSqlite() throws SQLException {
        String fileName = plugin.getStorageConfig().getString("database.sqlite.file", "lengbanlist.db");
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

        if (plugin.getStorageConfig().getBoolean("database.sqlite.auto-vacuum", true)) {
            execute("PRAGMA auto_vacuum = INCREMENTAL");
            sqliteAutoVacuumPending = existingDatabase;
        }
    }

    private void initializeNetwork() throws SQLException {
        String host = connectionSetting("host", "localhost");
        int port = connectionSetting("port", defaultPort());
        String database = connectionSetting("database", "lengbanlist");
        String username = connectionSetting("username", defaultUsername());
        String password = connectionSetting("password", "");
        if (password == null || password.isEmpty()) {
            throw new SQLException("未配置 " + dialect.displayName() + " 密码 (database."
                    + dialect.configKey() + ".password)，请在 storage.yml 中显式设置后再启动。");
        }
        int poolSize = resolvePoolSize();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl(host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);

        config.setMinimumIdle(1);

        config.setPoolName(dialect.poolName());
        if (dialect == DatabaseDialect.POSTGRESQL) {
            config.addDataSourceProperty("ApplicationName", "Lengbanlist");
        }
        dataSource = new HikariDataSource(config);
        execute("SELECT 1");
        plugin.getLogger().info(dialect.displayName() + " 连接池已建立，最大连接数 " + poolSize
                + "（database." + dialect.configKey() + ".pool-size 可调）");
    }

    private String jdbcUrl(String host, int port, String database) {
        switch (dialect) {
            case MARIADB:
                return "jdbc:mariadb://" + host + ":" + port + "/" + database + "?sslMode=disable";
            case POSTGRESQL:
                return "jdbc:postgresql://" + host + ":" + port + "/" + database;
            default:
                return "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8";
        }
    }

    private int defaultPort() {
        switch (dialect) {
            case POSTGRESQL:
                return 5432;
            default:
                return 3306;
        }
    }

    private String defaultUsername() {
        return dialect == DatabaseDialect.POSTGRESQL ? "postgres" : "root";
    }

    private String connectionSetting(String key, String fallback) {
        String path = "database." + dialect.configKey() + "." + key;
        String value = plugin.getStorageConfig().getString(path, null);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        if (dialect == DatabaseDialect.MARIADB) {
            String mysqlValue = plugin.getStorageConfig().getString("database.mysql." + key, fallback);
            return mysqlValue == null || mysqlValue.trim().isEmpty() ? fallback : mysqlValue.trim();
        }
        return fallback;
    }

    private int connectionSetting(String key, int fallback) {
        String path = "database." + dialect.configKey() + "." + key;
        if (plugin.getStorageConfig().contains(path)) {
            return plugin.getStorageConfig().getInt(path, fallback);
        }
        if (dialect == DatabaseDialect.MARIADB) {
            return plugin.getStorageConfig().getInt("database.mysql." + key, fallback);
        }
        return fallback;
    }

    private String resolveSqliteSynchronous() {
        String configured = plugin.getStorageConfig().getString("database.sqlite.synchronous", "NORMAL");
        String upper = configured == null ? "NORMAL" : configured.trim().toUpperCase(Locale.ROOT);
        if (upper.equals("FULL") || upper.equals("NORMAL") || upper.equals("OFF")) {
            return upper;
        }
        plugin.getLogger().warning("database.sqlite.synchronous 取值非法: " + configured
                + "，已按 NORMAL 处理（可选 FULL / NORMAL / OFF）。");
        return "NORMAL";
    }

    private int resolvePoolSize() {
        int configured = connectionSetting("pool-size", 10);
        if (configured < 1) {
            plugin.getLogger().warning("database." + dialect.configKey() + ".pool-size 配置为 " + configured + "，已按 1 处理。");
            return 1;
        }
        if (configured > 200) {
            plugin.getLogger().warning("database." + dialect.configKey() + ".pool-size 配置为 " + configured + "，超出上限，已按 200 处理。");
            return 200;
        }
        return configured;
    }

    public void applyCacheConfig() {
        long ttlSeconds = plugin.getStorageConfig().getLong("database.cache.ban-ttl-seconds", 5L);
        banCache.setTtlMillis(ttlSeconds * 1000L);
        long warnTtlSeconds = plugin.getStorageConfig().getLong("database.cache.warn-ttl-seconds", ttlSeconds);
        warnCache.setTtlMillis(warnTtlSeconds * 1000L);
    }

    public void reloadBanCache() {
        banCache.reload();
    }

    public void reloadWarnCache() {
        warnCache.invalidateAll();
    }

    public void invalidateWarn(String player) {
        warnCache.invalidate(player);
    }

    public long getBanCacheTtlMillis() {
        return banCache.getTtlMillis();
    }

    public long getWarnCacheTtlMillis() {
        return warnCache.getTtlMillis();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public String getDatabaseProductName() {
        try (Connection connection = getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            logSql(e);
            return dialect.displayName();
        }
    }

    public boolean isMySql() {
        return dialect.isMySqlFamily();
    }

    public boolean isSqlite() {
        return dialect == DatabaseDialect.SQLITE;
    }

    public boolean isNetworkDatabase() {
        return dialect.isNetwork();
    }

    DatabaseDialect getDialect() {
        return dialect;
    }

    public PlayerIdentityResolver getIdentityResolver() {
        return identityResolver;
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public void ensureSchema() throws SQLException {
        execute("CREATE TABLE IF NOT EXISTS schema_meta (meta_key " + textPrimaryKey() + ", meta_value " + textType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS player_ips (player_name " + textPrimaryKey() + ", ip " + textType() + " NOT NULL, updated_at " + longType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS bans (id " + integerPrimaryKey() + ", target " + textType() + " NOT NULL, staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, is_auto " + booleanType() + " NOT NULL DEFAULT 0, active " + booleanType() + " NOT NULL DEFAULT 1, start_time " + longType() + " NOT NULL DEFAULT 0)");
        execute("CREATE TABLE IF NOT EXISTS ip_bans (id " + integerPrimaryKey() + ", ip " + textType() + " NOT NULL, staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, is_auto " + booleanType() + " NOT NULL DEFAULT 0, active " + booleanType() + " NOT NULL DEFAULT 1, start_time " + longType() + " NOT NULL DEFAULT 0)");
        execute("CREATE TABLE IF NOT EXISTS mutes (target " + textPrimaryKey() + ", staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, start_time " + longType() + " NOT NULL DEFAULT 0)");
        execute("CREATE TABLE IF NOT EXISTS freezes (target " + textPrimaryKey() + ", staff " + textType() + " NOT NULL, freeze_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS warnings (id " + textPrimaryKey() + ", player " + textType() + " NOT NULL, staff " + textType() + " NOT NULL, warn_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, revoked " + booleanType() + " NOT NULL DEFAULT 0)");
        execute("CREATE TABLE IF NOT EXISTS reports (id " + textPrimaryKey() + ", target " + textType() + " NOT NULL, reporter " + textType() + " NOT NULL, reason " + textType() + " NOT NULL, status " + varcharType(32) + " NOT NULL DEFAULT '" + STATUS_PENDING + "', timestamp " + longType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS audit_log (id " + integerPrimaryKey() + ", timestamp " + longType() + " NOT NULL, actor " + textType() + " NOT NULL, action " + textType() + " NOT NULL, target " + textType() + " NOT NULL, reason " + textType() + " NOT NULL, success " + booleanType() + " NOT NULL DEFAULT 1, server " + textType() + " NOT NULL DEFAULT '')");

        addColumnIfMissing("audit_log", "server", textType() + " NOT NULL DEFAULT ''");
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
        addColumnIfMissing("reports", "status", varcharType(32) + " NOT NULL DEFAULT '" + STATUS_PENDING + "'");
        addColumnIfMissing("reports", "timestamp", longType() + " NOT NULL DEFAULT 0");

        execute("CREATE TABLE IF NOT EXISTS player_ip_history (id " + integerPrimaryKey() + ", player_name " + varcharType(191) + " NOT NULL, ip " + varcharType(191) + " NOT NULL, first_seen " + longType() + " NOT NULL, last_seen " + longType() + " NOT NULL, UNIQUE(player_name, ip))");
        execute("CREATE TABLE IF NOT EXISTS sync_events (id " + integerPrimaryKey() + ", timestamp " + longType() + " NOT NULL, server " + textType() + " NOT NULL DEFAULT '', scope " + varcharType(32) + " NOT NULL, target " + textType() + " NOT NULL, uuid " + varcharType(36) + " NOT NULL DEFAULT '', action " + varcharType(32) + " NOT NULL DEFAULT '')");
        execute("CREATE TABLE IF NOT EXISTS player_identity (uuid " + varcharType(36) + " PRIMARY KEY, name " + varcharType(191) + " NOT NULL, first_seen " + longType() + " NOT NULL, last_seen " + longType() + " NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS name_history (uuid " + varcharType(36) + " NOT NULL, name " + varcharType(191) + " NOT NULL, changed_at " + longType() + " NOT NULL, PRIMARY KEY (uuid, name))");
        execute("CREATE TABLE IF NOT EXISTS appeals (id " + varcharType(64) + " PRIMARY KEY, ban_id " + longType() + " NOT NULL DEFAULT 0, target " + varcharType(191) + " NOT NULL, uuid " + varcharType(36) + " NOT NULL DEFAULT '', contact " + varcharType(191) + " NOT NULL DEFAULT '', reason " + textType() + " NOT NULL, status " + varcharType(32) + " NOT NULL DEFAULT '" + AppealEntry.STATUS_PENDING + "', created_at " + longType() + " NOT NULL, handled_by " + textType() + " NOT NULL DEFAULT '', handled_at " + longType() + " NOT NULL DEFAULT 0, response " + textType() + " NOT NULL DEFAULT '', ticket " + varcharType(64) + " NOT NULL DEFAULT '', notified " + booleanType() + " NOT NULL DEFAULT 0)");

        addColumnIfMissing("bans", "uuid", varcharType(36) + " NOT NULL DEFAULT ''");
        addColumnIfMissing("mutes", "uuid", varcharType(36) + " NOT NULL DEFAULT ''");
        addColumnIfMissing("warnings", "uuid", varcharType(36) + " NOT NULL DEFAULT ''");
        addColumnIfMissing("freezes", "uuid", varcharType(36) + " NOT NULL DEFAULT ''");
        addColumnIfMissing("reports", "target_uuid", varcharType(36) + " NOT NULL DEFAULT ''");
        addColumnIfMissing("reports", "reporter_uuid", varcharType(36) + " NOT NULL DEFAULT ''");
        addColumnIfMissing("player_ips", "uuid", varcharType(36) + " NOT NULL DEFAULT ''");
        addColumnIfMissing("player_ip_history", "uuid", varcharType(36) + " NOT NULL DEFAULT ''");

        createIndexIfMissing("warnings", "idx_warnings_player", indexTextColumn("player"));
        createIndexIfMissing("reports", "idx_reports_target", indexTextColumn("target"));
        createIndexIfMissing("reports", "idx_reports_reporter", indexTextColumn("reporter"));
        createIndexIfMissing("reports", "idx_reports_target_uuid", "target_uuid");
        createIndexIfMissing("reports", "idx_reports_reporter_uuid", "reporter_uuid");
        createIndexIfMissing("audit_log", "idx_audit_log_timestamp", "timestamp");
        createIndexIfMissing("audit_log", "idx_audit_log_actor", indexTextColumn("actor"));
        createIndexIfMissing("audit_log", "idx_audit_log_target", indexTextColumn("target"));
        createIndexIfMissing("bans", "idx_bans_active_end", "active, end_time");
        createIndexIfMissing("ip_bans", "idx_ip_bans_active_end", "active, end_time");
        createIndexIfMissing("mutes", "idx_mutes_end_time", "end_time");
        createIndexIfMissing("player_ips", "idx_player_ips_ip", indexTextColumn("ip"));
        createIndexIfMissing("player_ip_history", "idx_player_ip_history_ip", "ip");
        createIndexIfMissing("sync_events", "idx_sync_events_timestamp", "timestamp");
        createIndexIfMissing("bans", "idx_bans_uuid", "uuid");
        createIndexIfMissing("mutes", "idx_mutes_uuid", "uuid");
        createIndexIfMissing("freezes", "idx_freezes_uuid", "uuid");
        createIndexIfMissing("warnings", "idx_warnings_uuid", "uuid");
        createIndexIfMissing("player_ips", "idx_player_ips_uuid", "uuid");
        createIndexIfMissing("player_ip_history", "idx_player_ip_history_uuid", "uuid");
        createIndexIfMissing("name_history", "idx_name_history_name", indexTextColumn("name"));

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
            String idCol = dialect.integerPrimaryKey();
            String newTable = table + "_v3";
            String srcCol = table.equals("bans") ? "target" : "ip";
            boolean withUuid = table.equals("bans") && columnExists(table, "uuid");

            execute("DROP TABLE IF EXISTS " + newTable);
            execute("CREATE TABLE " + newTable + " (id " + idCol + ", " + srcCol + " " + textType() + " NOT NULL, staff " + textType() + " NOT NULL, end_time " + longType() + " NOT NULL, reason " + textType() + " NOT NULL, is_auto " + booleanType() + " NOT NULL DEFAULT 0, active " + booleanType() + " NOT NULL DEFAULT 1"
                    + (withUuid ? ", uuid " + varcharType(36) + " NOT NULL DEFAULT ''" : "") + ")");

            execute("INSERT INTO " + newTable + " (" + srcCol + ", staff, end_time, reason, is_auto, active" + (withUuid ? ", uuid" : "") + ")"
                    + " SELECT COALESCE(" + srcCol + ", ''), COALESCE(staff, ''), COALESCE(end_time, 0),"
                    + " COALESCE(reason, ''), COALESCE(is_auto, 0), COALESCE(active, 1)"
                    + (withUuid ? ", COALESCE(uuid, '')" : "") + " FROM " + table);
            execute("DROP TABLE " + table);
            execute(dialect.renameTable(newTable, table));
        }

        createIndexIfMissing(table, "idx_" + table + "_target_active",
                dialect.indexColumn(table.equals("bans") ? "target" : "ip") + ", active");
    }

    public boolean upsertPlayerIp(String playerName, String ip, long updatedAt) {
        return executeUpdateAffected(upsertSql("player_ips", "player_name", new String[]{"player_name", "ip", "updated_at"}, new String[]{"ip", "updated_at"}), playerName, ip, updatedAt) > 0;
    }

    public void recordPlayerLoginIp(String uuid, String playerName, String ip, long timestamp) {
        String id = uuid == null ? "" : uuid.trim().toLowerCase(Locale.ROOT);
        String upsertPlayer = upsertSql("player_ips", "player_name",
                new String[]{"player_name", "uuid", "ip", "updated_at"}, new String[]{"uuid", "ip", "updated_at"});
        String history = historyInsertSql();
        inTransaction(null, connection -> {
            update(connection, upsertPlayer, new Object[]{playerName, id, ip, timestamp});
            update(connection, history, new Object[]{playerName, id, ip, timestamp, timestamp});
            if (!id.isEmpty()) {
                update(connection, "UPDATE player_ip_history SET uuid = ? WHERE player_name = ? AND uuid = ''",
                        new Object[]{id, playerName});
            }
            return null;
        });
    }

    private String historyInsertSql() {
        return "INSERT INTO player_ip_history (player_name, uuid, ip, first_seen, last_seen) VALUES (?, ?, ?, ?, ?) "
                + dialect.upsertTail("player_name, ip", new String[]{"last_seen"});
    }

    public String getPlayerIp(String playerName) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("player_name", "uuid", identityResolver.resolve(playerName), args);
        return queryOne("SELECT ip FROM player_ips WHERE " + where + " ORDER BY updated_at DESC",
                rs -> rs.getString("ip"), args.toArray());
    }

    public List<String> getPlayersByIp(String ip) {
        return query("SELECT player_name FROM player_ips WHERE ip = ? ORDER BY player_name",
                rs -> rs.getString("player_name"), ip);
    }

    public void recordPlayerIp(String playerName, String ip, long timestamp) {
        executeUpdate(historyInsertSql(), playerName, "", ip, timestamp, timestamp);
    }

    public List<String[]> getPlayerIpHistory(String playerName) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("player_name", "uuid", identityResolver.resolve(playerName), args);
        return query("SELECT ip, MIN(first_seen) AS first_seen, MAX(last_seen) AS last_seen FROM player_ip_history WHERE "
                        + where + " GROUP BY ip ORDER BY MAX(last_seen) DESC",
                rs -> new String[]{rs.getString("ip"), String.valueOf(rs.getLong("first_seen")), String.valueOf(rs.getLong("last_seen"))},
                args.toArray());
    }

    public List<String> getPlayersByIpFromHistory(String ip) {
        return query("SELECT DISTINCT player_name FROM player_ip_history WHERE ip = ? ORDER BY player_name",
                rs -> rs.getString("player_name"), ip);
    }

    public void recordIdentity(String uuid, String name, long time) {
        if (uuid == null || uuid.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            return;
        }
        String id = uuid.trim().toLowerCase(Locale.ROOT);
        String current = name.trim();
        inTransaction(null, connection -> {
            String previous = null;
            try (PreparedStatement ps = connection.prepareStatement("SELECT name FROM player_identity WHERE uuid = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        previous = value(rs, "name");
                    }
                }
            }
            if (previous == null) {
                update(connection, "INSERT INTO player_identity (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?)",
                        new Object[]{id, current, time, time});
                update(connection, nameHistoryInsertSql(), new Object[]{id, current, time});
                return null;
            }
            if (!previous.equalsIgnoreCase(current)) {
                update(connection, nameHistoryInsertSql(), new Object[]{id, previous, time});
            }
            update(connection, "UPDATE player_identity SET name = ?, last_seen = ? WHERE uuid = ?",
                    new Object[]{current, time, id});
            return null;
        });
    }

    private String nameHistoryInsertSql() {
        return dialect.insertIgnorePrefix() + "name_history (uuid, name, changed_at) VALUES (?, ?, ?)"
                + dialect.insertIgnoreTail("uuid, name");
    }

    public PlayerIdentity getIdentityByUuid(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return PlayerIdentity.EMPTY;
        }
        String id = uuid.trim().toLowerCase(Locale.ROOT);
        String current = queryOne("SELECT name FROM player_identity WHERE uuid = ?", rs -> value(rs, "name"), id);
        if (current == null || current.isEmpty()) {
            return PlayerIdentity.EMPTY;
        }
        List<String> names = new ArrayList<>();
        names.add(current);
        for (String old : query("SELECT name FROM name_history WHERE uuid = ? ORDER BY changed_at ASC",
                rs -> value(rs, "name"), id)) {
            if (!old.isEmpty() && !containsIgnoreCase(names, old)) {
                names.add(old);
            }
        }
        return new PlayerIdentity(id, current, names);
    }

    private static boolean containsIgnoreCase(List<String> names, String candidate) {
        for (String name : names) {
            if (name.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    public PlayerIdentity resolveIdentity(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.trim().isEmpty()) {
            return PlayerIdentity.EMPTY;
        }
        String input = nameOrUuid.trim();
        if (looksLikeUuid(input)) {
            PlayerIdentity byUuid = getIdentityByUuid(input);
            return byUuid.hasUuid() ? byUuid : PlayerIdentity.ofName(input);
        }
        String uuid = queryOne("SELECT uuid FROM player_identity WHERE LOWER(name) = LOWER(?)",
                rs -> value(rs, "uuid"), input);
        if (uuid == null || uuid.isEmpty()) {
            uuid = queryOne("SELECT uuid FROM name_history WHERE LOWER(name) = LOWER(?)",
                    rs -> value(rs, "uuid"), input);
        }
        if (uuid == null || uuid.isEmpty()) {
            return PlayerIdentity.ofName(input);
        }
        PlayerIdentity identity = getIdentityByUuid(uuid);
        return identity.hasUuid() ? identity : PlayerIdentity.ofName(input);
    }

    private static boolean looksLikeUuid(String value) {
        return value.length() == 36 && value.charAt(8) == '-' && value.charAt(13) == '-'
                && value.charAt(18) == '-' && value.charAt(23) == '-';
    }

    public int backfillIdentityUuids(int batchSize) {
        Map<String, String> nameToUuid = loadNameToUuidMap();
        if (nameToUuid.isEmpty()) {
            return 0;
        }
        int size = Math.max(50, batchSize);
        int updated = 0;
        updated += backfillUuidColumn("bans", "id", "target", "uuid", nameToUuid, size);
        updated += backfillUuidColumn("mutes", "target", "target", "uuid", nameToUuid, size);
        updated += backfillUuidColumn("freezes", "target", "target", "uuid", nameToUuid, size);
        updated += backfillUuidColumn("warnings", "id", "player", "uuid", nameToUuid, size);
        updated += backfillUuidColumn("reports", "id", "target", "target_uuid", nameToUuid, size);
        updated += backfillUuidColumn("reports", "id", "reporter", "reporter_uuid", nameToUuid, size);
        updated += backfillUuidColumn("player_ips", "player_name", "player_name", "uuid", nameToUuid, size);
        updated += backfillUuidColumn("player_ip_history", "id", "player_name", "uuid", nameToUuid, size);
        return updated;
    }

    private Map<String, String> loadNameToUuidMap() {
        Map<String, String> map = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (String[] row : query("SELECT uuid, name FROM player_identity", this::readUuidAndName)) {
            putNameToUuid(map, ambiguous, row[1], row[0]);
        }
        for (String[] row : query("SELECT uuid, name FROM name_history", this::readUuidAndName)) {
            putNameToUuid(map, ambiguous, row[1], row[0]);
        }
        for (String name : ambiguous) {
            map.remove(name);
        }
        return map;
    }

    private static void putNameToUuid(Map<String, String> map, Set<String> ambiguous, String name, String uuid) {
        if (name == null || name.isEmpty() || uuid == null || uuid.isEmpty()) {
            return;
        }
        String key = name.toLowerCase(Locale.ROOT);
        String existing = map.putIfAbsent(key, uuid);
        if (existing != null && !existing.equals(uuid)) {
            ambiguous.add(key);
        }
    }

    private String[] readUuidAndName(ResultSet rs) throws SQLException {
        return new String[]{value(rs, "uuid"), value(rs, "name")};
    }

    private int backfillUuidColumn(String table, String keyColumn, String nameColumn, String uuidColumn,
                                   Map<String, String> nameToUuid, int batchSize) {
        int updated = 0;
        Object cursor = null;
        String select = "SELECT " + keyColumn + ", " + nameColumn + " FROM " + table + " WHERE " + uuidColumn + " = ''";
        while (true) {
            String sql = select + (cursor == null ? "" : " AND " + keyColumn + " > ?")
                    + " ORDER BY " + keyColumn + " ASC LIMIT ?";
            List<Object[]> rows = cursor == null
                    ? query(sql, this::readKeyAndName, batchSize)
                    : query(sql, this::readKeyAndName, cursor, batchSize);
            if (rows.isEmpty()) {
                return updated;
            }
            for (Object[] row : rows) {
                cursor = row[0];
                String name = row[1] == null ? "" : row[1].toString();
                String uuid = nameToUuid.get(name.toLowerCase(Locale.ROOT));
                if (uuid == null) {
                    continue;
                }
                updated += Math.max(0, executeUpdateAffected("UPDATE " + table + " SET " + uuidColumn
                        + " = ? WHERE " + keyColumn + " = ? AND " + uuidColumn + " = ''", uuid, row[0]));
            }
            if (rows.size() < batchSize) {
                return updated;
            }
        }
    }

    private Object[] readKeyAndName(ResultSet rs) throws SQLException {
        return new Object[]{rs.getObject(1), rs.getString(2)};
    }

    public WriteResult addBan(BanEntry entry) {
        return replaceActiveBan(entry);
    }

    public WriteResult upsertBan(BanEntry entry) {
        return replaceActiveBan(entry);
    }

    public WriteResult replaceActiveBan(BanEntry entry) {
        List<Object> args = new ArrayList<>();
        WriteResult result = invalidateBans(replaceActiveEntry(
                banDeactivateSql(args, entry.getTarget()), args.toArray(),
                BAN_INSERT, banInsertValues(entry)));
        publishIfApplied(result, SyncEvent.SCOPE_BAN, entry.getTarget(), SyncEvent.ACTION_ADD);
        return result;
    }

    public WriteResult replaceExistingActiveBan(BanEntry entry) {
        List<Object> args = new ArrayList<>();
        WriteResult result = invalidateBans(replaceExistingActiveEntry(
                banDeactivateSql(args, entry.getTarget()), args.toArray(),
                BAN_INSERT, banInsertValues(entry)));
        publishIfApplied(result, SyncEvent.SCOPE_BAN, entry.getTarget(), SyncEvent.ACTION_UPDATE);
        return result;
    }

    public WriteResult replaceActiveBanAndUpdateReport(BanEntry banEntry, ReportEntry reportEntry,
                                                       String reportStatus) {
        List<Object> args = new ArrayList<>();
        WriteResult result = invalidateBans(replaceActiveEntry(
                banDeactivateSql(args, banEntry.getTarget()), args.toArray(),
                BAN_INSERT, banInsertValues(banEntry),
                "UPDATE reports SET status = ? WHERE id = ? AND status = ?",
                new Object[]{status(reportStatus), reportEntry.getId(), status(reportEntry.getStatus())}));
        publishIfApplied(result, SyncEvent.SCOPE_BAN, banEntry.getTarget(), SyncEvent.ACTION_ADD);
        return result;
    }

    public WriteResult deactivateBanForUnban(String target, long now) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("target", "uuid", identityResolver.resolve(target), args);
        args.add(now);
        WriteResult result = invalidateBans(deactivateForUnban(
                "UPDATE bans SET active = 0 WHERE " + where + " AND active = 1 AND end_time > ?",
                "UPDATE bans SET active = 0 WHERE " + where + " AND active = 1 AND end_time <= ?",
                args.toArray()));
        publishIfApplied(result, SyncEvent.SCOPE_BAN, target, SyncEvent.ACTION_REMOVE);
        return result;
    }

    public void deleteBan(String target) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("target", "uuid", identityResolver.resolve(target), args);
        executeUpdate("DELETE FROM bans WHERE " + where, args.toArray());
        banCache.invalidate();
        publishSyncEvent(SyncEvent.SCOPE_BAN, target, SyncEvent.ACTION_REMOVE);
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

    public boolean isPlayerBannedByUuid(String uuid) {
        return banCache.isBannedByUuid(uuid);
    }

    public BanEntry getBan(String target) {
        return banCache.getBan(target);
    }

    public BanEntry getBanByUuid(String uuid) {
        return banCache.getBanByUuid(uuid);
    }

    public List<BanEntry> getBans() {
        return banCache.getUnexpiredBans();
    }

    public List<BanEntry> getBansByPlayer(String player) {
        return getBansByPlayer(identityResolver.resolve(player));
    }

    public List<BanEntry> getBansByPlayer(PlayerIdentity identity) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("target", "uuid", identity, args);
        return query("SELECT target, staff, end_time, reason, is_auto, active FROM bans WHERE " + where + " ORDER BY end_time DESC",
                this::readBan, args.toArray());
    }

    private static final List<String> PUNISHMENT_TABLES = List.of("bans", "ip_bans", "mutes");

    public Map<String, Long> getActiveStartTimes(String table) {
        Map<String, Long> result = new HashMap<>();
        if (!PUNISHMENT_TABLES.contains(table)) {
            return result;
        }
        String keyColumn = table.equals("ip_bans") ? "ip" : "target";
        String filter = table.equals("mutes") ? "" : " WHERE active = 1";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT LOWER(" + keyColumn + ") AS k, start_time FROM " + table + filter);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("k"), rs.getLong("start_time"));
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return result;
    }

    public Map<Long, Long> getStartTimesByPlayer(String table, String player) {
        Map<Long, Long> result = new HashMap<>();
        if (!PUNISHMENT_TABLES.contains(table)) {
            return result;
        }
        List<Object> args = new ArrayList<>();
        String where;
        if (table.equals("ip_bans")) {
            where = "LOWER(ip) = LOWER(?)";
            args.add(player);
        } else {
            where = identityPredicate("target", "uuid", identityResolver.resolve(player), args);
        }
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT end_time, MAX(start_time) AS start_time FROM " + table + " WHERE " + where + " GROUP BY end_time")) {
            setValues(ps, args.toArray());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getLong("end_time"), rs.getLong("start_time"));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return result;
    }

    void backfillPunishmentStartTimes() {
        int bans = executeUpdateAffected("UPDATE bans SET start_time = COALESCE("
                + "(SELECT MAX(timestamp) FROM audit_log WHERE LOWER(audit_log.target) = LOWER(bans.target) AND audit_log.action = ?), 0)"
                + " WHERE active = 1 AND start_time <= 0", "封禁");
        int ipBans = executeUpdateAffected("UPDATE ip_bans SET start_time = COALESCE("
                + "(SELECT MAX(timestamp) FROM audit_log WHERE LOWER(audit_log.target) = LOWER(ip_bans.ip) AND audit_log.action = ?), 0)"
                + " WHERE active = 1 AND start_time <= 0", "封禁IP");
        int mutes = executeUpdateAffected("UPDATE mutes SET start_time = COALESCE("
                + "(SELECT MAX(timestamp) FROM audit_log WHERE LOWER(audit_log.target) = LOWER(mutes.target) AND audit_log.action = ?), 0)"
                + " WHERE start_time <= 0", "禁言");
        plugin.getLogger().info("判罚起始时间回填：封禁 " + bans + " 条、IP 封禁 " + ipBans + " 条、禁言 " + mutes + " 条");
    }

    public List<BanEntry> getRecentBans(int limit) {
        return query("SELECT target, staff, end_time, reason, is_auto, active FROM bans ORDER BY id DESC LIMIT ?",
                this::readBan, limit);
    }

    public int countBanHistory(String target) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("target", "uuid", identityResolver.resolve(target), args);
        return count("SELECT COUNT(*) FROM bans WHERE " + where, args.toArray());
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

    private List<BanCache.BanRow> queryActiveBans() {
        List<BanCache.BanRow> rows = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT target, uuid, staff, end_time, reason, is_auto, active FROM bans WHERE active = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BanCache.BanRow(readBan(rs), value(rs, "uuid")));
                }
            }
        } catch (SQLException e) {
            throw new BanCache.LoadFailure(e);
        }
        return rows;
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
            throw new BanCache.LoadFailure(e);
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
        WriteResult result = invalidateBans(replaceActiveEntry(
                "UPDATE ip_bans SET active = 0 WHERE ip = ? AND active = 1",
                new Object[]{entry.getIp()},
                "INSERT INTO ip_bans (ip, staff, end_time, reason, is_auto, active, start_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                new Object[]{entry.getIp(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isAuto(), entry.isActive(), System.currentTimeMillis()}));
        publishIfApplied(result, SyncEvent.SCOPE_IP_BAN, entry.getIp(), SyncEvent.ACTION_ADD);
        return result;
    }

    public WriteResult replaceExistingActiveIpBan(BanIpEntry entry) {
        WriteResult result = invalidateBans(replaceExistingActiveEntry(
                "UPDATE ip_bans SET active = 0 WHERE ip = ? AND active = 1",
                new Object[]{entry.getIp()},
                "INSERT INTO ip_bans (ip, staff, end_time, reason, is_auto, active, start_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                new Object[]{entry.getIp(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isAuto(), entry.isActive(), System.currentTimeMillis()}));
        publishIfApplied(result, SyncEvent.SCOPE_IP_BAN, entry.getIp(), SyncEvent.ACTION_UPDATE);
        return result;
    }

    public WriteResult deactivateIpBanForUnban(String ip, long now) {
        WriteResult result = invalidateBans(deactivateForUnban(
                "UPDATE ip_bans SET active = 0 WHERE ip = ? AND active = 1 AND end_time > ?",
                "UPDATE ip_bans SET active = 0 WHERE ip = ? AND active = 1 AND end_time <= ?",
                ip, now));
        publishIfApplied(result, SyncEvent.SCOPE_IP_BAN, ip, SyncEvent.ACTION_REMOVE);
        return result;
    }

    public void deleteIpBan(String ip) {
        executeUpdate("DELETE FROM ip_bans WHERE ip = ?", ip);
        banCache.invalidate();
        publishSyncEvent(SyncEvent.SCOPE_IP_BAN, ip, SyncEvent.ACTION_REMOVE);
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
        return query("SELECT ip, staff, end_time, reason, is_auto, active FROM ip_bans WHERE ip = ? ORDER BY end_time DESC",
                this::readIpBan, ip);
    }

    public int countIpBanHistory(String ip) {
        return count("SELECT COUNT(*) FROM ip_bans WHERE ip = ?", ip);
    }

    public boolean upsertMute(MuteEntry entry) {
        return upsertMute(entry, System.currentTimeMillis());
    }

    public boolean upsertMute(MuteEntry entry, long startTime) {
        MuteEntry normalized = new MuteEntry(entry.getTarget().toLowerCase(Locale.ROOT), entry.getStaff(), entry.getTime(), entry.getReason());
        boolean applied = executeUpdateAffected(upsertSql("mutes", "target", new String[]{"target", "staff", "end_time", "reason", "start_time"}, new String[]{"staff", "end_time", "reason"}), normalized.getTarget(), normalized.getStaff(), normalized.getTime(), normalized.getReason(), startTime) > 0;
        publishSyncEvent(SyncEvent.SCOPE_MUTE, normalized.getTarget(), SyncEvent.ACTION_ADD);
        return applied;
    }

    public void deleteMute(String target) {
        executeUpdate("DELETE FROM mutes WHERE LOWER(target) = LOWER(?)", target);
        publishSyncEvent(SyncEvent.SCOPE_MUTE, target, SyncEvent.ACTION_REMOVE);
    }

    public void deleteMuteIfExpiresAt(String target, long endTime) {
        executeUpdate("DELETE FROM mutes WHERE LOWER(target) = LOWER(?) AND end_time = ?", target, endTime);
    }

    public boolean saveFreeze(FreezeEntry entry) {
        boolean saved = executeUpdateAffected(upsertSql("freezes", "target",
                new String[]{"target", "staff", "freeze_time", "reason"},
                new String[]{"staff", "freeze_time", "reason"}),
                entry.player(), entry.staff(), entry.time(), entry.reason()) > 0;
        if (saved) {
            publishSyncEvent(SyncEvent.SCOPE_FREEZE, entry.player(), SyncEvent.ACTION_ADD);
        }
        return saved;
    }

    public boolean deleteFreeze(String target) {
        boolean removed = executeUpdateAffected("DELETE FROM freezes WHERE LOWER(target) = LOWER(?)", target) > 0;
        if (removed) {
            publishSyncEvent(SyncEvent.SCOPE_FREEZE, target, SyncEvent.ACTION_REMOVE);
        }
        return removed;
    }

    public int deleteAllFreezes() {
        int removed = executeUpdateAffected("DELETE FROM freezes");
        if (removed > 0) {
            publishSyncEvent(SyncEvent.SCOPE_FREEZE, SyncEvent.TARGET_ALL, SyncEvent.ACTION_REMOVE);
        }
        return removed;
    }

    public List<FreezeEntry> loadFreezes() {
        return query("SELECT target, staff, freeze_time, reason FROM freezes ORDER BY target", this::readFreeze);
    }

    private FreezeEntry readFreeze(ResultSet rs) throws SQLException {
        return new FreezeEntry(value(rs, "target"), value(rs, "staff"),
                rs.getLong("freeze_time"), value(rs, "reason"));
    }

    public MuteEntry getMute(String target) {
        return getMute(identityResolver.resolve(target));
    }

    public MuteEntry getMute(PlayerIdentity identity) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("target", "uuid", identity, args);
        return queryOne("SELECT target, staff, end_time, reason FROM mutes WHERE " + where + " ORDER BY end_time DESC",
                this::readMute, args.toArray());
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

    public List<MuteEntry> loadMutesForCache() throws SQLException {
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
        return getMutesByPlayer(identityResolver.resolve(player));
    }

    public List<MuteEntry> getMutesByPlayer(PlayerIdentity identity) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("target", "uuid", identity, args);
        return query("SELECT target, staff, end_time, reason FROM mutes WHERE " + where + " ORDER BY end_time DESC",
                this::readMute, args.toArray());
    }

    public List<String> getMuteTargets(PlayerIdentity identity) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("target", "uuid", identity, args);
        return query("SELECT target FROM mutes WHERE " + where, rs -> value(rs, "target"), args.toArray());
    }

    public List<MuteEntry> getAllMutes() {
        return query("SELECT target, staff, end_time, reason FROM mutes", this::readMute);
    }

    public boolean upsertWarning(WarnEntry entry) {
        boolean applied = executeUpdateAffected(upsertSql("warnings", "id", new String[]{"id", "player", "staff", "warn_time", "reason", "revoked"}, new String[]{"player", "staff", "warn_time", "reason", "revoked"}), entry.getId(), entry.getPlayer(), entry.getStaff(), entry.getTime(), entry.getReason(), entry.isRevoked()) > 0;
        warnCache.invalidate(entry.getPlayer());
        publishSyncEvent(SyncEvent.SCOPE_WARN, entry.getPlayer(), SyncEvent.ACTION_ADD);
        return applied;
    }

    public boolean updateWarningRevoked(String id, boolean revoked, String player) {
        int updated = executeUpdateAffected("UPDATE warnings SET revoked = ? WHERE id = ?", revoked, id);
        warnCache.invalidateAll();
        if (updated > 0) {
            publishSyncEvent(SyncEvent.SCOPE_WARN, player, revoked ? SyncEvent.ACTION_REMOVE : SyncEvent.ACTION_ADD);
        }
        return updated > 0;
    }

    public List<WarnEntry> getWarnings(String player, boolean activeOnly) {
        return warnCache.get(player, activeOnly);
    }

    List<WarnEntry> loadWarnsForCache(String player) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("player", "uuid", identityResolver.resolve(player), args);
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(
                WARN_SELECT + "WHERE " + where + " ORDER BY warn_time ASC, id ASC")) {
            setValues(ps, args.toArray());
            try (ResultSet rs = ps.executeQuery()) {
                List<WarnEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(readWarning(rs));
                }
                return entries;
            }
        } catch (SQLException e) {
            throw new WarnCache.LoadFailure(e);
        }
    }

    public List<String> getWarnedPlayers() {
        return query("SELECT DISTINCT player FROM warnings", rs -> rs.getString("player"));
    }

    public boolean upsertReport(ReportEntry entry) {
        return executeUpdateAffected(upsertSql("reports", "id", new String[]{"id", "target", "reporter", "reason", "status", "timestamp"}, new String[]{"target", "reporter", "reason", "status", "timestamp"}), entry.getId(), entry.getTarget(), entry.getReporter(), entry.getReason(), status(entry.getStatus()), entry.getTimestamp()) > 0;
    }

    public void deleteReport(String id) {
        executeUpdate("DELETE FROM reports WHERE id = ?", id);
    }

    public ReportEntry getReport(String id) {
        return queryOne("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE id = ?",
                this::readReport, id);
    }

    private static final String PENDING_REPORT_FILTER =
            "status IS NULL OR (status <> ? AND status <> ?)";

    public List<ReportEntry> getPendingReports() {
        return query("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE " + PENDING_REPORT_FILTER
                + " ORDER BY timestamp ASC", this::readReport, STATUS_CLOSED, STATUS_HANDLED);
    }

    public List<ReportEntry> getReportsByReporterWithStatus(String reporter, String reportStatus) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("reporter", "reporter_uuid", identityResolver.resolve(reporter), args);
        args.add(status(reportStatus));
        return query("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE " + where
                        + " AND status = ? ORDER BY timestamp ASC",
                this::readReport, args.toArray());
    }

    public int getPendingReportCount() {
        return count("SELECT COUNT(*) FROM reports WHERE " + PENDING_REPORT_FILTER, STATUS_CLOSED, STATUS_HANDLED);
    }

    public int getReportCount(String target) {
        List<Object> args = new ArrayList<>();
        String where = identityPredicate("target", "target_uuid", identityResolver.resolve(target), args);
        return count("SELECT COUNT(*) FROM reports WHERE " + where, args.toArray());
    }

    public List<ReportEntry> getReportsByReporterAndTarget(String reporter, String target) {
        List<Object> args = new ArrayList<>();
        String reporterWhere = identityPredicate("reporter", "reporter_uuid", identityResolver.resolve(reporter), args);
        String targetWhere = identityPredicate("target", "target_uuid", identityResolver.resolve(target), args);
        return query("SELECT id, target, reporter, reason, status, timestamp FROM reports WHERE " + reporterWhere
                        + " AND " + targetWhere + " ORDER BY timestamp DESC",
                this::readReport, args.toArray());
    }

    private static final String APPEAL_SELECT =
            "SELECT id, ban_id, target, uuid, contact, reason, status, created_at, handled_by, handled_at, response, ticket, notified FROM appeals ";

    public void upsertAppeal(AppealEntry entry) {
        executeUpdate(upsertSql("appeals", "id",
                new String[]{"id", "ban_id", "target", "uuid", "contact", "reason", "status", "created_at",
                        "handled_by", "handled_at", "response", "ticket", "notified"},
                new String[]{"ban_id", "target", "uuid", "contact", "reason", "status", "handled_by",
                        "handled_at", "response", "ticket", "notified"}),
                entry.id(), entry.banId(), entry.target(), entry.uuid(), entry.contact(), entry.reason(),
                entry.status(), entry.createdAt(), entry.handledBy(), entry.handledAt(), entry.response(),
                entry.ticket(), entry.notified());
    }

    public AppealEntry getAppeal(String id) {
        return queryOne(APPEAL_SELECT + "WHERE id = ?", this::readAppeal, id);
    }

    public AppealEntry getAppealByTicket(String ticket) {
        if (ticket == null || ticket.trim().isEmpty()) {
            return null;
        }
        return queryOne(APPEAL_SELECT + "WHERE ticket = ?", this::readAppeal, ticket.trim());
    }

    public List<AppealEntry> getAppeals(String status, int limit) {
        int size = Math.max(1, Math.min(limit, 500));
        if (status == null || status.trim().isEmpty()) {
            return query(APPEAL_SELECT + "ORDER BY created_at DESC LIMIT ?", this::readAppeal, size);
        }
        return query(APPEAL_SELECT + "WHERE status = ? ORDER BY created_at DESC LIMIT ?",
                this::readAppeal, status, size);
    }

    public List<AppealEntry> getAppealsByTarget(String target, int limit) {
        if (target == null || target.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return query(APPEAL_SELECT + "WHERE LOWER(target) = LOWER(?) ORDER BY created_at DESC LIMIT ?",
                this::readAppeal, target.trim(), Math.max(1, Math.min(limit, 500)));
    }

    public int countAppealsByTargetAndStatus(String target, String status) {
        if (target == null || target.trim().isEmpty()) {
            return 0;
        }
        return count("SELECT COUNT(*) FROM appeals WHERE LOWER(target) = LOWER(?) AND status = ?",
                target.trim(), status);
    }

    public int countAppealsByTargetSince(String target, long since) {
        if (target == null || target.trim().isEmpty()) {
            return 0;
        }
        return count("SELECT COUNT(*) FROM appeals WHERE LOWER(target) = LOWER(?) AND created_at >= ?",
                target.trim(), since);
    }

    public int countPendingAppeals() {
        return count("SELECT COUNT(*) FROM appeals WHERE status = ?", AppealEntry.STATUS_PENDING);
    }

    public void markAppealNotified(String id) {
        executeUpdate("UPDATE appeals SET notified = ? WHERE id = ?", true, id);
    }

    public long getActiveBanId(String target) {
        if (target == null || target.trim().isEmpty()) {
            return 0L;
        }
        Long id = queryOne("SELECT id FROM bans WHERE LOWER(target) = LOWER(?) AND active = 1 ORDER BY end_time DESC LIMIT 1",
                rs -> rs.getLong("id"), target.trim());
        return id == null ? 0L : id;
    }

    private AppealEntry readAppeal(ResultSet rs) throws SQLException {
        return new AppealEntry(value(rs, "id"), rs.getLong("ban_id"), value(rs, "target"), value(rs, "uuid"),
                value(rs, "contact"), value(rs, "reason"), value(rs, "status"), rs.getLong("created_at"),
                value(rs, "handled_by"), rs.getLong("handled_at"), value(rs, "response"), value(rs, "ticket"),
                readBoolean(rs, "notified"));
    }

    public boolean addAuditLog(String actor, String action, String target, String reason, boolean success) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("INSERT INTO audit_log (timestamp, actor, action, target, reason, success, server) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, actor);
            ps.setString(3, action);
            ps.setString(4, target);
            ps.setString(5, reason);
            dialect.bindBoolean(ps, 6, success);
            ps.setString(7, serverTag());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logSql(e);
            return false;
        }
    }

    public boolean addAuditLogChained(String actor, String action, String target, String reason, boolean success) {
        synchronized (auditChainLock) {
            ensureAuditTailRow();
            return inTransaction(false, connection -> {
                lockAuditTail(connection);
                String prevHash = ZERO_HASH;
                try (PreparedStatement ps = connection.prepareStatement(
                        AUDIT_SELECT + "ORDER BY id DESC LIMIT 1" + dialect.lockingReadSuffix())) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            prevHash = hashRow(value(rs, "prev_hash"), rs.getLong("timestamp"), value(rs, "actor"), value(rs, "action"), value(rs, "target"), value(rs, "reason"), readBoolean(rs, "success"));
                        }
                    }
                }
                String chainHash = prevHash;
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO audit_log (timestamp, actor, action, target, reason, success, prev_hash, server) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, actor == null ? "" : actor);
                    ps.setString(3, action == null ? "" : action);
                    ps.setString(4, target == null ? "" : target);
                    ps.setString(5, reason == null ? "" : reason);
                    dialect.bindBoolean(ps, 6, success);
                    ps.setString(7, chainHash);
                    ps.setString(8, serverTag());
                    ps.executeUpdate();
                }
                return true;
            });
        }
    }

    private void ensureAuditTailRow() {
        if (auditTailEnsured) {
            return;
        }
        auditTailEnsured = insertIgnoreInto("schema_meta", "meta_key",
                new String[]{"meta_key", "meta_value"}, AUDIT_TAIL_KEY, "");
    }

    private void lockAuditTail(Connection connection) throws SQLException {
        if (!dialect.locksRows()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT meta_value FROM schema_meta WHERE meta_key = ? FOR UPDATE")) {
            ps.setString(1, AUDIT_TAIL_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
        }
    }

    public List<AuditEntry> getAuditLogsAfter(long afterId, int limit) {
        return query(AUDIT_SELECT + "WHERE id > ? ORDER BY id ASC LIMIT ?", this::readAudit, afterId, limit);
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
        if (actorOrTarget == null || actorOrTarget.isEmpty()) {
            return query(AUDIT_SELECT + "ORDER BY timestamp DESC LIMIT ?", this::readAudit, limit);
        }
        return query(AUDIT_SELECT + "WHERE actor = ? OR target = ? ORDER BY timestamp DESC LIMIT ?",
                this::readAudit, actorOrTarget, actorOrTarget, limit);
    }

    public List<AuditEntry> getAuditLogsByActor(String actor, int limit) {
        if (actor == null || actor.isEmpty()) {
            return getAuditLogs(null, limit);
        }
        return query(AUDIT_SELECT + "WHERE actor = ? ORDER BY timestamp DESC LIMIT ?", this::readAudit, actor, limit);
    }

    public List<AuditEntry> getAuditLogsByActorInRange(String actor, long from, long to) {
        if (actor == null || actor.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return query(AUDIT_SELECT + "WHERE actor = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp ASC, id ASC",
                this::readAudit, actor.trim(), from, to);
    }

    private AuditEntry readAudit(ResultSet rs) throws SQLException {
        return new AuditEntry(rs.getLong("id"), rs.getLong("timestamp"), value(rs, "actor"), value(rs, "action"), value(rs, "target"), value(rs, "reason"), readBoolean(rs, "success"), value(rs, "prev_hash"), value(rs, "server"));
    }

    public String serverTag() {
        String name = plugin.getServerName();
        return name == null ? "" : name;
    }

    private void publishIfApplied(WriteResult result, String scope, String target, String action) {
        if (result != WriteResult.APPLIED) {
            return;
        }
        publishSyncEvent(scope, target, action);
    }

    private void publishSyncEvent(String scope, String target, String action) {
        executeUpdate("INSERT INTO sync_events (timestamp, server, scope, target, uuid, action) VALUES (?, ?, ?, ?, ?, ?)",
                System.currentTimeMillis(), serverTag(), scope, target == null ? "" : target, "", action);
    }

    public List<SyncEvent> getSyncEventsAfter(long afterId, int limit) {
        return query("SELECT id, timestamp, server, scope, target, action FROM sync_events WHERE id > ? ORDER BY id ASC LIMIT ?",
                this::readSyncEvent, afterId, limit);
    }

    public long getLatestSyncEventId() {
        Long latest = queryOne("SELECT MAX(id) FROM sync_events", rs -> rs.getLong(1));
        return latest == null ? 0L : latest;
    }

    public int countSyncEventsAfter(long afterId) {
        return count("SELECT COUNT(*) FROM sync_events WHERE id > ?", afterId);
    }

    public int cleanupSyncEvents(int retentionMinutes) {
        if (retentionMinutes <= 0) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - (retentionMinutes * 60000L);
        return Math.max(0, executeUpdateAffected("DELETE FROM sync_events WHERE timestamp < ?", cutoff));
    }

    private SyncEvent readSyncEvent(ResultSet rs) throws SQLException {
        return new SyncEvent(rs.getLong("id"), rs.getLong("timestamp"), value(rs, "server"),
                value(rs, "scope"), value(rs, "target"), value(rs, "action"));
    }

    public String getMeta(String key) {
        return queryOne("SELECT meta_value FROM schema_meta WHERE meta_key = ?", rs -> rs.getString("meta_value"), key);
    }

    public void setMeta(String key, String value) {
        executeUpdate(upsertSql("schema_meta", "meta_key", new String[]{"meta_key", "meta_value"}, new String[]{"meta_value"}), key, value);
    }

    private BanEntry readBan(ResultSet rs) throws SQLException {
        return new BanEntry(value(rs, "target"), value(rs, "staff"), rs.getLong("end_time"), value(rs, "reason"), readBoolean(rs, "is_auto"), readBoolean(rs, "active"));
    }

    private BanIpEntry readIpBan(ResultSet rs) throws SQLException {
        return new BanIpEntry(value(rs, "ip"), value(rs, "staff"), rs.getLong("end_time"), value(rs, "reason"), readBoolean(rs, "is_auto"), readBoolean(rs, "active"));
    }

    public List<BanEntry> getBansExpiringBefore(long deadline) {
        return query("SELECT target, staff, end_time, reason, is_auto, active FROM bans WHERE active = 1 AND end_time <= ? ORDER BY end_time ASC",
                this::readBan, deadline);
    }

    public List<MuteEntry> getMutesExpiringBefore(long deadline) {
        return query("SELECT target, staff, end_time, reason FROM mutes WHERE end_time <= ? ORDER BY end_time ASC",
                this::readMute, deadline);
    }

    public boolean cleanupOldData(int retentionDays) {
        long cutoff = System.currentTimeMillis() - (retentionDays * 86400000L);
        boolean removed = false;
        removed |= executeUpdateAffected("DELETE FROM bans WHERE active = 0 AND end_time < ?", cutoff) > 0;
        removed |= executeUpdateAffected("DELETE FROM ip_bans WHERE active = 0 AND end_time < ?", cutoff) > 0;
        removed |= executeUpdateAffected("DELETE FROM mutes WHERE end_time != " + Long.MAX_VALUE + " AND end_time < ?", cutoff) > 0;
        boolean warnsRemoved = executeUpdateAffected("DELETE FROM warnings WHERE revoked = 1 AND warn_time < ?", cutoff) > 0;
        removed |= warnsRemoved;
        removed |= executeUpdateAffected("DELETE FROM reports WHERE status != '" + STATUS_PENDING + "' AND timestamp < ?", cutoff) > 0;
        removed |= executeUpdateAffected("DELETE FROM appeals WHERE status != '" + AppealEntry.STATUS_PENDING + "' AND created_at < ?", cutoff) > 0;
        removed |= executeUpdateAffected("DELETE FROM audit_log WHERE timestamp < ?", cutoff) > 0;
        int ipHistoryDays = plugin.getStorageConfig().getInt("database.retention.ip-history-days", 0);
        if (ipHistoryDays > 0) {
            long ipCutoff = System.currentTimeMillis() - (ipHistoryDays * 86400000L);
            removed |= executeUpdateAffected("DELETE FROM player_ip_history WHERE last_seen < ?", ipCutoff) > 0;
        }
        cleanupSyncEvents(plugin.getStorageConfig() == null ? 60
                : plugin.getStorageConfig().getInt("sync.event-retention-minutes", 60));
        if (removed) {
            banCache.invalidate();
        }
        if (warnsRemoved) {
            warnCache.invalidateAll();
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
        if (!isSqlite() || dataSource == null) {
            return;
        }
        if (!plugin.getStorageConfig().getBoolean("database.sqlite.auto-vacuum", true)) {
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
        if (readBoolean(rs, "revoked")) {
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
        sql.append(dialect.upsertTail(keyColumn, updateColumns));
        return sql.toString();
    }

    boolean insertIgnoreInto(String table, String keyColumn, String[] columns, Object... values) {
        String sql = dialect.insertIgnorePrefix() + table + " (" + join(columns) + ") VALUES ("
                + placeholders(columns.length) + ")" + dialect.insertIgnoreTail(keyColumn);
        return executeUpdateAffected(sql, values) >= 0;
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
        try {
            return inTransaction(WriteResult.DATABASE_ERROR, connection -> {
                int deactivateCount = update(connection, deactivateSql, deactivateValues);
                if (update(connection, insertSql, insertValues) != 1) {
                    throw new Abort(WriteResult.DATABASE_ERROR);
                }
                if (requireExistingActive && deactivateCount == 0) {
                    throw new Abort(WriteResult.NO_CHANGE);
                }
                if (followUpSql != null && update(connection, followUpSql, followUpValues) == 0) {
                    throw new Abort(WriteResult.NO_CHANGE);
                }
                return WriteResult.APPLIED;
            });
        } catch (Abort abort) {
            return abort.result;
        }
    }

    private WriteResult deactivateForUnban(String effectiveSql, String expiredSql, Object... values) {
        return inTransaction(WriteResult.DATABASE_ERROR, connection -> {
            int effectiveCount = update(connection, effectiveSql, values);
            update(connection, expiredSql, values);
            return effectiveCount > 0 ? WriteResult.APPLIED : WriteResult.NO_CHANGE;
        });
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

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            logSql(e);
        }
    }

    private void restoreAutoCommit(Connection connection, boolean originalAutoCommit) {
        try {
            connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException e) {
            logSql(e);
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private static final class Abort extends RuntimeException {
        private final transient WriteResult result;

        private Abort(WriteResult result) {
            super(null, null, false, false);
            this.result = result;
        }
    }

    private <T> List<T> query(String sql, RowMapper<T> mapper, Object... values) {
        List<T> results = new ArrayList<>();
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            setValues(ps, values);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
            }
        } catch (SQLException e) {
            logSql(e);
        }
        return results;
    }

    private <T> T queryOne(String sql, RowMapper<T> mapper, Object... values) {
        List<T> results = query(sql, mapper, values);
        return results.isEmpty() ? null : results.get(0);
    }

    private int update(Connection connection, String sql, Object[] values) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setValues(ps, values);
            return ps.executeUpdate();
        }
    }

    private <T> T inTransaction(T fallback, SqlWork<T> work) {
        Connection connection = null;
        try {
            connection = getConnection();
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException e) {
                rollbackQuietly(connection);
                logSql(e);
                return fallback;
            } catch (RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
        } catch (SQLException e) {
            logSql(e);
            return fallback;
        } finally {
            closeConnection(connection);
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
                dialect.bindBoolean(ps, i + 1, (Boolean) value);
            } else {
                ps.setObject(i + 1, value);
            }
        }
    }

    private boolean readBoolean(ResultSet rs, String column) throws SQLException {
        return dialect.readBoolean(rs.getObject(column));
    }

    void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        if (!columnExists(table, column)) {
            execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        if (dialect == DatabaseDialect.SQLITE) {
            return sqliteColumnsOf(table).contains(column.toLowerCase(Locale.ROOT));
        }
        return catalogHas(dialect.columnExistsSql(), table, column);
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
        return dialect.indexColumn(column);
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
        if (dialect == DatabaseDialect.SQLITE) {
            return sqliteIndexesOf(table).contains(index.toLowerCase(Locale.ROOT));
        }
        return catalogHas(dialect.indexExistsSql(), table, index);
    }

    private boolean catalogHas(String sql, String table, String name) throws SQLException {
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

    public void execute(String sql) throws SQLException {

        sqliteColumns.clear();
        sqliteIndexes.clear();
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String textPrimaryKey() {
        return dialect.textPrimaryKey();
    }

    String textType() {
        return dialect.textType();
    }

    String varcharType(int length) {
        return dialect.varchar(length);
    }

    private String nullableTextType() {
        return dialect.nullableText();
    }

    String longType() {
        return dialect.longType();
    }

    private String booleanType() {
        return dialect.booleanType();
    }

    private String integerPrimaryKey() {
        return dialect.integerPrimaryKey();
    }

    private String banDeactivateSql(List<Object> args, String target) {
        String where = identityPredicate("target", "uuid", identityResolver.resolve(target), args);
        return "UPDATE bans SET active = 0 WHERE " + where + " AND active = 1";
    }

    private Object[] banInsertValues(BanEntry entry) {
        return new Object[]{entry.getTarget(), resolvedUuid(entry.getTarget()), entry.getStaff(), entry.getTime(),
                entry.getReason(), entry.isAuto(), entry.isActive(), System.currentTimeMillis()};
    }

    private String resolvedUuid(String target) {
        if (target == null || target.trim().isEmpty()) {
            return "";
        }
        PlayerIdentity identity = identityResolver.resolve(target);
        return identity == null ? "" : identity.uuid();
    }

    private String identityPredicate(String nameColumn, String uuidColumn, PlayerIdentity identity, List<Object> args) {
        List<String> names = identity == null ? List.<String>of() : identity.lowerNames();
        StringBuilder sql = new StringBuilder("(");
        boolean matched = false;
        if (!names.isEmpty()) {
            sql.append("LOWER(").append(nameColumn).append(") IN (").append(placeholders(names.size())).append(")");
            args.addAll(names);
            matched = true;
        }
        if (identity != null && identity.hasUuid()) {
            if (matched) {
                sql.append(" OR ");
            }
            sql.append(uuidColumn).append(" = ?");
            args.add(identity.uuid());
            matched = true;
        }
        if (!matched) {
            sql.append("1 = 0");
        }
        return sql.append(")").toString();
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
        return status == null || status.trim().isEmpty() ? STATUS_PENDING : status;
    }

    private void logSql(SQLException e) {
        plugin.getLogger().warning("数据库操作失败：" + e.getMessage()
                + "（详细堆栈见 " + org.leng.util.ErrorLog.fileName(plugin) + "）");
        org.leng.util.ErrorLog.record(plugin, "数据库操作失败", e);
    }

}

package org.leng.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class SchemaMigrations {

    public static final int CURRENT_VERSION = 4;

    private static final TreeMap<Integer, Migration> MIGRATIONS = new TreeMap<>();

    static {
        register(3, "bans/ip_bans 表加 id 主键列",
                db -> {
                    db.migrateBanTableToV3("bans");
                    db.migrateBanTableToV3("ip_bans");
                });
        register(4, "audit_log 加 prev_hash 列,初始化链式哈希",
                db -> {
                    db.addColumnIfMissing("audit_log", "prev_hash",
                            db.varcharType(64) + " NOT NULL DEFAULT ''");
                    db.insertIgnoreInto("schema_meta", "meta_key",
                            new String[]{"meta_key", "meta_value"}, "audit.tail", "");
                    db.backfillAuditChain();
                });
    }

    private SchemaMigrations() {}

    private static void register(int version, String description, MigrationAction action) {
        MIGRATIONS.put(version, new Migration(version, description, action));
    }

    public static void runAll(DatabaseManager db, String currentVersion) {
        int startVersion = 0;
        if (currentVersion != null) {
            try {
                startVersion = Integer.parseInt(currentVersion);
            } catch (NumberFormatException ignored) {
                startVersion = 0;
            }
        }
        for (Migration m : MIGRATIONS.values()) {
            if (m.version() > startVersion) {
                try {
                    db.getPlugin().getLogger().info("正在执行 schema v" + m.version() + " 迁移: " + m.description());
                    m.action().run(db);
                    db.setMeta("schema.version", String.valueOf(m.version()));
                } catch (Exception e) {
                    db.getPlugin().getLogger().severe("schema v" + m.version() + " 迁移失败: " + e.getMessage());
                    throw new RuntimeException("Migration v" + m.version() + " failed", e);
                }
            }
        }
    }

    public static List<Migration> list() {
        return new ArrayList<>(MIGRATIONS.values());
    }

    public record Migration(int version, String description, MigrationAction action) {}

    @FunctionalInterface
    public interface MigrationAction {

        void run(DatabaseManager db) throws Exception;
    }
}

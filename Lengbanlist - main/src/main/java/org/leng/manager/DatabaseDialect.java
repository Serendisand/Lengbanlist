package org.leng.manager;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;

enum DatabaseDialect {

    SQLITE, MYSQL, MARIADB, POSTGRESQL;

    static final int INDEX_PREFIX_CHARS = 64;

    static DatabaseDialect parse(String type) {
        if (type == null) {
            return null;
        }
        switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "sqlite":
                return SQLITE;
            case "mysql":
                return MYSQL;
            case "mariadb":
                return MARIADB;
            case "postgresql":
            case "postgres":
            case "pgsql":
                return POSTGRESQL;
            default:
                return null;
        }
    }

    boolean isMySqlFamily() {
        return this == MYSQL || this == MARIADB;
    }

    boolean isNetwork() {
        return this != SQLITE;
    }

    boolean locksRows() {
        return this != SQLITE;
    }

    String configKey() {
        switch (this) {
            case MYSQL:
                return "mysql";
            case MARIADB:
                return "mariadb";
            case POSTGRESQL:
                return "postgresql";
            default:
                return "sqlite";
        }
    }

    String displayName() {
        switch (this) {
            case MYSQL:
                return "MySQL";
            case MARIADB:
                return "MariaDB";
            case POSTGRESQL:
                return "PostgreSQL";
            default:
                return "SQLite";
        }
    }

    String poolName() {
        return "Lengbanlist-" + displayName();
    }

    String textPrimaryKey() {
        return this == SQLITE ? "TEXT PRIMARY KEY" : "VARCHAR(191) PRIMARY KEY";
    }

    String textType() {
        return "TEXT";
    }

    String varchar(int length) {
        return this == SQLITE ? "TEXT" : "VARCHAR(" + length + ")";
    }

    String nullableText() {
        return this == SQLITE ? "TEXT NOT NULL DEFAULT ''" : "TEXT";
    }

    String longType() {
        return this == SQLITE ? "INTEGER" : "BIGINT";
    }

    String booleanType() {
        switch (this) {
            case SQLITE:
                return "INTEGER";
            case POSTGRESQL:
                return "SMALLINT";
            default:
                return "BOOLEAN";
        }
    }

    String integerPrimaryKey() {
        switch (this) {
            case SQLITE:
                return "INTEGER PRIMARY KEY AUTOINCREMENT";
            case POSTGRESQL:
                return "SERIAL PRIMARY KEY";
            default:
                return "INT AUTO_INCREMENT PRIMARY KEY";
        }
    }

    String indexColumn(String column) {
        return isMySqlFamily() ? column + "(" + INDEX_PREFIX_CHARS + ")" : column;
    }

    String renameTable(String from, String to) {
        return isMySqlFamily() ? "RENAME TABLE " + from + " TO " + to : "ALTER TABLE " + from + " RENAME TO " + to;
    }

    String upsertTail(String conflictTarget, String[] updateColumns) {
        StringBuilder sql = new StringBuilder();
        if (isMySqlFamily()) {
            sql.append("ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < updateColumns.length; i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(updateColumns[i]).append(" = VALUES(").append(updateColumns[i]).append(")");
            }
        } else {
            sql.append("ON CONFLICT(").append(conflictTarget).append(") DO UPDATE SET ");
            for (int i = 0; i < updateColumns.length; i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(updateColumns[i]).append(" = excluded.").append(updateColumns[i]);
            }
        }
        return sql.toString();
    }

    String insertIgnorePrefix() {
        if (isMySqlFamily()) {
            return "INSERT IGNORE INTO ";
        }
        return this == POSTGRESQL ? "INSERT INTO " : "INSERT OR IGNORE INTO ";
    }

    String insertIgnoreTail(String keyColumn) {
        return this == POSTGRESQL ? " ON CONFLICT(" + keyColumn + ") DO NOTHING" : "";
    }

    String lockingReadSuffix() {
        return locksRows() ? " FOR UPDATE" : "";
    }

    String columnExistsSql() {
        if (this == POSTGRESQL) {
            return "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?";
        }
        return "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)";
    }

    String indexExistsSql() {
        if (this == POSTGRESQL) {
            return "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() AND tablename = ? AND indexname = ?";
        }
        return "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = LOWER(?) AND LOWER(INDEX_NAME) = LOWER(?)";
    }

    void bindBoolean(PreparedStatement ps, int index, boolean value) throws SQLException {
        if (this == POSTGRESQL) {
            ps.setInt(index, value ? 1 : 0);
        } else {
            ps.setBoolean(index, value);
        }
    }

    boolean readBoolean(Object raw) {
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        String text = String.valueOf(raw).trim();
        return "1".equals(text) || text.equalsIgnoreCase("true") || text.equalsIgnoreCase("t");
    }
}

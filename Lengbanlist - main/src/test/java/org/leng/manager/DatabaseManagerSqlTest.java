package org.leng.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerSqlTest {

    @Test
    void parse_acceptsEverySupportedAlias() {
        assertEquals(DatabaseDialect.SQLITE, DatabaseDialect.parse("sqlite"));
        assertEquals(DatabaseDialect.MYSQL, DatabaseDialect.parse("MySQL"));
        assertEquals(DatabaseDialect.MARIADB, DatabaseDialect.parse(" mariadb "));
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.parse("postgresql"));
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.parse("postgres"));
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.parse("pgsql"));
        assertNull(DatabaseDialect.parse("oracle"));
        assertNull(DatabaseDialect.parse(null));
    }

    @Test
    void onlySqliteIsLocal() {
        assertFalse(DatabaseDialect.SQLITE.isNetwork());
        assertTrue(DatabaseDialect.MYSQL.isNetwork());
        assertTrue(DatabaseDialect.MARIADB.isNetwork());
        assertTrue(DatabaseDialect.POSTGRESQL.isNetwork());

        assertTrue(DatabaseDialect.MYSQL.isMySqlFamily());
        assertTrue(DatabaseDialect.MARIADB.isMySqlFamily());
        assertFalse(DatabaseDialect.POSTGRESQL.isMySqlFamily());
        assertFalse(DatabaseDialect.SQLITE.isMySqlFamily());
    }

    @Test
    void indexColumn_prefixesOnlyForMySqlFamily() {
        assertEquals("target(" + DatabaseDialect.INDEX_PREFIX_CHARS + ")", DatabaseDialect.MYSQL.indexColumn("target"));
        assertEquals("actor(" + DatabaseDialect.INDEX_PREFIX_CHARS + ")", DatabaseDialect.MARIADB.indexColumn("actor"));
        assertEquals("target", DatabaseDialect.SQLITE.indexColumn("target"));
        assertEquals("target", DatabaseDialect.POSTGRESQL.indexColumn("target"));
    }

    @Test
    void upsertTail_usesNativeQuoteStylePerDialect() {
        assertEquals("ON DUPLICATE KEY UPDATE staff = VALUES(staff), reason = VALUES(reason)",
                DatabaseDialect.MYSQL.upsertTail("target", new String[]{"staff", "reason"}));
        assertEquals("ON DUPLICATE KEY UPDATE staff = VALUES(staff), reason = VALUES(reason)",
                DatabaseDialect.MARIADB.upsertTail("target", new String[]{"staff", "reason"}));
        assertEquals("ON CONFLICT(target) DO UPDATE SET staff = excluded.staff, reason = excluded.reason",
                DatabaseDialect.POSTGRESQL.upsertTail("target", new String[]{"staff", "reason"}));
        assertEquals("ON CONFLICT(target) DO UPDATE SET staff = excluded.staff",
                DatabaseDialect.SQLITE.upsertTail("target", new String[]{"staff"}));
    }

    @Test
    void upsertTail_supportsCompositeConflictTarget() {
        assertEquals("ON CONFLICT(player_name, ip) DO UPDATE SET last_seen = excluded.last_seen",
                DatabaseDialect.POSTGRESQL.upsertTail("player_name, ip", new String[]{"last_seen"}));
    }

    @Test
    void insertIgnore_syntaxDiffersPerDialect() {
        assertEquals("INSERT IGNORE INTO ", DatabaseDialect.MYSQL.insertIgnorePrefix());
        assertEquals("", DatabaseDialect.MYSQL.insertIgnoreTail("meta_key"));

        assertEquals("INSERT OR IGNORE INTO ", DatabaseDialect.SQLITE.insertIgnorePrefix());
        assertEquals("", DatabaseDialect.SQLITE.insertIgnoreTail("meta_key"));

        assertEquals("INSERT INTO ", DatabaseDialect.POSTGRESQL.insertIgnorePrefix());
        assertEquals(" ON CONFLICT(meta_key) DO NOTHING", DatabaseDialect.POSTGRESQL.insertIgnoreTail("meta_key"));
    }

    @Test
    void columnDefinition_matchesDialectTypeSystem() {
        assertEquals("INTEGER PRIMARY KEY AUTOINCREMENT", DatabaseDialect.SQLITE.integerPrimaryKey());
        assertEquals("INT AUTO_INCREMENT PRIMARY KEY", DatabaseDialect.MYSQL.integerPrimaryKey());
        assertEquals("INT AUTO_INCREMENT PRIMARY KEY", DatabaseDialect.MARIADB.integerPrimaryKey());
        assertEquals("SERIAL PRIMARY KEY", DatabaseDialect.POSTGRESQL.integerPrimaryKey());

        assertEquals("INTEGER", DatabaseDialect.SQLITE.booleanType());
        assertEquals("BOOLEAN", DatabaseDialect.MYSQL.booleanType());
        assertEquals("BOOLEAN", DatabaseDialect.MARIADB.booleanType());
        assertEquals("SMALLINT", DatabaseDialect.POSTGRESQL.booleanType());

        assertEquals("INTEGER", DatabaseDialect.SQLITE.longType());
        assertEquals("BIGINT", DatabaseDialect.MARIADB.longType());
        assertEquals("BIGINT", DatabaseDialect.POSTGRESQL.longType());

        assertEquals("TEXT", DatabaseDialect.SQLITE.varchar(191));
        assertEquals("VARCHAR(191)", DatabaseDialect.POSTGRESQL.varchar(191));
        assertEquals("TEXT PRIMARY KEY", DatabaseDialect.SQLITE.textPrimaryKey());
        assertEquals("VARCHAR(191) PRIMARY KEY", DatabaseDialect.POSTGRESQL.textPrimaryKey());
    }

    @Test
    void postgresBooleanColumns_useNumericStorage() {
        assertEquals("SMALLINT", DatabaseDialect.POSTGRESQL.booleanType(),
                "PostgreSQL 原生 BOOLEAN 不接受 DEFAULT 0 与 active = 1 这类写法，布尔列必须以 0/1 存放");
        assertEquals("INTEGER", DatabaseDialect.SQLITE.booleanType());
        assertEquals("BOOLEAN", DatabaseDialect.MYSQL.booleanType());
        assertEquals("BOOLEAN", DatabaseDialect.MARIADB.booleanType());
    }

    @Test
    void renameTable_syntaxDiffersPerDialect() {
        assertEquals("RENAME TABLE bans_v3 TO bans", DatabaseDialect.MYSQL.renameTable("bans_v3", "bans"));
        assertEquals("RENAME TABLE bans_v3 TO bans", DatabaseDialect.MARIADB.renameTable("bans_v3", "bans"));
        assertEquals("ALTER TABLE bans_v3 RENAME TO bans", DatabaseDialect.SQLITE.renameTable("bans_v3", "bans"));
        assertEquals("ALTER TABLE bans_v3 RENAME TO bans", DatabaseDialect.POSTGRESQL.renameTable("bans_v3", "bans"));
    }

    @Test
    void lockingRead_onlyOnDatabasesThatSupportIt() {
        assertEquals(" FOR UPDATE", DatabaseDialect.MYSQL.lockingReadSuffix());
        assertEquals(" FOR UPDATE", DatabaseDialect.MARIADB.lockingReadSuffix());
        assertEquals(" FOR UPDATE", DatabaseDialect.POSTGRESQL.lockingReadSuffix());
        assertEquals("", DatabaseDialect.SQLITE.lockingReadSuffix());
        assertFalse(DatabaseDialect.SQLITE.locksRows());
    }

    @Test
    void catalogQueries_neverUseMySqlOnlyFunctionsOnPostgres() {
        assertTrue(DatabaseDialect.MYSQL.columnExistsSql().contains("DATABASE()"));
        assertTrue(DatabaseDialect.MARIADB.columnExistsSql().contains("DATABASE()"));
        assertFalse(DatabaseDialect.POSTGRESQL.columnExistsSql().contains("DATABASE()"));
        assertTrue(DatabaseDialect.POSTGRESQL.columnExistsSql().contains("current_schema()"));

        assertTrue(DatabaseDialect.MYSQL.indexExistsSql().contains("STATISTICS"));
        assertFalse(DatabaseDialect.POSTGRESQL.indexExistsSql().contains("STATISTICS"));
        assertTrue(DatabaseDialect.POSTGRESQL.indexExistsSql().contains("pg_indexes"));
    }

    @Test
    void readBoolean_normalizesEveryStorageForm() {
        assertTrue(DatabaseDialect.SQLITE.readBoolean(1));
        assertFalse(DatabaseDialect.SQLITE.readBoolean(0));
        assertTrue(DatabaseDialect.POSTGRESQL.readBoolean((short) 1));
        assertTrue(DatabaseDialect.MYSQL.readBoolean(Boolean.TRUE));
        assertTrue(DatabaseDialect.MYSQL.readBoolean("true"));
        assertTrue(DatabaseDialect.MYSQL.readBoolean("t"));
        assertTrue(DatabaseDialect.MYSQL.readBoolean("1"));
        assertFalse(DatabaseDialect.MYSQL.readBoolean("0"));
        assertFalse(DatabaseDialect.MYSQL.readBoolean(null));
        assertFalse(DatabaseDialect.MYSQL.readBoolean(0L));
    }

    @Test
    void poolName_identifiesBackendInLogs() {
        assertEquals("Lengbanlist-MySQL", DatabaseDialect.MYSQL.poolName());
        assertEquals("Lengbanlist-MariaDB", DatabaseDialect.MARIADB.poolName());
        assertEquals("Lengbanlist-PostgreSQL", DatabaseDialect.POSTGRESQL.poolName());
    }
}

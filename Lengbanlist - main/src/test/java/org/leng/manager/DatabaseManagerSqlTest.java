package org.leng.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseManagerSqlTest {

    @Test
    void indexTextColumn_mysql_addsPrefixLength() {
        assertEquals("target(64)", DatabaseManager.indexTextColumn(true, "target"));
        assertEquals("actor(64)", DatabaseManager.indexTextColumn(true, "actor"));
    }

    @Test
    void indexTextColumn_sqlite_keepsColumnBare() {

        assertEquals("target", DatabaseManager.indexTextColumn(false, "target"));
        assertEquals("actor", DatabaseManager.indexTextColumn(false, "actor"));
    }

    @Test
    void banTableIndexColumns_mysql_prefixesOnlyTheTextColumn() {

        assertEquals("target(64), active", DatabaseManager.banTableIndexColumns(true, "bans"));
        assertEquals("ip(64), active", DatabaseManager.banTableIndexColumns(true, "ip_bans"));
    }

    @Test
    void banTableIndexColumns_sqlite_unchanged() {
        assertEquals("target, active", DatabaseManager.banTableIndexColumns(false, "bans"));
        assertEquals("ip, active", DatabaseManager.banTableIndexColumns(false, "ip_bans"));
    }

    @Test
    void indexPrefixLength_coversLongestRealValue() {

        int prefix = 64;
        assertEquals("target(" + prefix + ")", DatabaseManager.indexTextColumn(true, "target"));
    }
}

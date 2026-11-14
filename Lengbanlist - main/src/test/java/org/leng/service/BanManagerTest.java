package org.leng.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.leng.object.BanIpEntry;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import org.leng.storage.DatabaseManager;

@ExtendWith(MockitoExtension.class)
class BanManagerTest {

    @Mock Lengbanlist plugin;
    @Mock DatabaseManager db;
    BanManager manager;

    @BeforeEach
    void setUp() {
        when(plugin.getDatabaseManager()).thenReturn(db);
        manager = new BanManager(plugin);
    }

    @Test
    void isValidIp_validIpv4_returnsTrue() {
        assertTrue(manager.isValidIp("192.168.1.1"));
        assertTrue(manager.isValidIp("8.8.8.8"));
        assertTrue(manager.isValidIp("255.255.255.255"));
    }

    @Test
    void isValidIp_outOfRangeOctet_returnsFalse() {
        assertFalse(manager.isValidIp("256.0.0.1"));
        assertFalse(manager.isValidIp("1.2.3.999"));
    }

    @Test
    void isValidIp_wrongSegmentCount_returnsFalse() {
        assertFalse(manager.isValidIp("1.2.3"));
        assertFalse(manager.isValidIp("1.2.3.4.5"));
    }

    @Test
    void isValidIp_nonNumericSegment_returnsFalse() {
        assertFalse(manager.isValidIp("1.2.3.x"));
    }

    @Test
    void isValidIp_nullOrEmpty_returnsFalse() {
        assertFalse(manager.isValidIp(null));
        assertFalse(manager.isValidIp(""));
    }

    @Test
    void isValidIp_ipv6_returnsTrue() {
        assertTrue(manager.isValidIp("::1"));
        assertTrue(manager.isValidIp("2001:db8::1"));
        assertFalse(manager.isValidIp("abc:def"));
    }

    @Test
    void mapWriteResult_appliedReturnsApplied() {

        when(db.replaceActiveBan(org.mockito.ArgumentMatchers.any()))
                .thenReturn(DatabaseManager.WriteResult.APPLIED);

        org.mockito.Mockito.lenient()
                .when(plugin.getModelManager()).thenReturn(null);

        when(db.replaceActiveBan(org.mockito.ArgumentMatchers.any()))
                .thenReturn(DatabaseManager.WriteResult.NO_CHANGE);
        org.leng.object.BanEntry entry = new org.leng.object.BanEntry(
                "test", "staff", Long.MAX_VALUE, "reason", false);
        assertEquals(BanManager.BanMutationResult.NOT_ACTIVE, manager.tryBanPlayer(entry, true));
    }

    @Test
    void mapWriteResult_databaseErrorPropagates() {
        when(db.replaceActiveBan(org.mockito.ArgumentMatchers.any()))
                .thenReturn(DatabaseManager.WriteResult.DATABASE_ERROR);
        org.leng.object.BanEntry entry = new org.leng.object.BanEntry(
                "test", "staff", Long.MAX_VALUE, "reason", false);
        assertEquals(BanManager.BanMutationResult.DATABASE_ERROR, manager.tryBanPlayer(entry, true));
    }

    @Test
    void getMatchingIpBan_nullIp_returnsNull() {
        assertNull(manager.getMatchingIpBan(null));
    }

    @Test
    void getMatchingIpBan_exactMatch_returnsEntry() {
        BanIpEntry entry = new BanIpEntry("192.168.1.100", "staff", Long.MAX_VALUE, "reason", false);
        when(db.findMatchingIpBan("192.168.1.100")).thenReturn(entry);
        assertSame(entry, manager.getMatchingIpBan("192.168.1.100"));
    }

    @Test
    void getMatchingIpBan_noMatch_returnsNull() {
        assertNull(manager.getMatchingIpBan("8.8.8.8"));
    }

    @Test
    void getMatchingIpBan_cidrMatch_returnsEntry() {
        BanIpEntry entry = new BanIpEntry("192.168.1.0/24", "staff", Long.MAX_VALUE, "reason", false);
        when(db.findMatchingIpBan("192.168.1.42")).thenReturn(entry);
        assertSame(entry, manager.getMatchingIpBan("192.168.1.42"));
    }

    @Test
    void getMatchingIpBan_firstMatchWins() {
        BanIpEntry exact = new BanIpEntry("192.168.1.100", "staff1", Long.MAX_VALUE, "reason1", false);
        when(db.findMatchingIpBan("192.168.1.100")).thenReturn(exact);
        assertSame(exact, manager.getMatchingIpBan("192.168.1.100"));
    }
}

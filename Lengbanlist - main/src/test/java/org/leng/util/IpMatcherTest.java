package org.leng.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IpMatcherTest {

    @Test
    void isIpv4_acceptsValidAddresses() {
        assertTrue(IpMatcher.isIpv4("0.0.0.0"));
        assertTrue(IpMatcher.isIpv4("255.255.255.255"));
        assertTrue(IpMatcher.isIpv4("192.168.1.1"));
        assertTrue(IpMatcher.isIpv4("127.0.0.1"));
    }

    @Test
    void isIpv4_rejectsInvalid() {
        assertFalse(IpMatcher.isIpv4(null));
        assertFalse(IpMatcher.isIpv4(""));
        assertFalse(IpMatcher.isIpv4("256.0.0.1"));
        assertFalse(IpMatcher.isIpv4("1.2.3"));
        assertFalse(IpMatcher.isIpv4("1.2.3.4.5"));
        assertFalse(IpMatcher.isIpv4("abc.def.ghi.jkl"));
        assertFalse(IpMatcher.isIpv4("-1.2.3.4"));
    }

    @Test
    void isCidr_acceptsValid() {
        assertTrue(IpMatcher.isCidr("10.0.0.0/8"));
        assertTrue(IpMatcher.isCidr("192.168.1.0/24"));
        assertTrue(IpMatcher.isCidr("172.16.0.0/12"));
        assertTrue(IpMatcher.isCidr("0.0.0.0/0"));
        assertTrue(IpMatcher.isCidr("1.2.3.4/32"));
    }

    @Test
    void isCidr_rejectsInvalid() {
        assertFalse(IpMatcher.isCidr(null));
        assertFalse(IpMatcher.isCidr(""));
        assertFalse(IpMatcher.isCidr("10.0.0.0"));
        assertFalse(IpMatcher.isCidr("10.0.0.0/"));
        assertFalse(IpMatcher.isCidr("10.0.0.0/33"));
        assertFalse(IpMatcher.isCidr("10.0.0.0/-1"));
        assertFalse(IpMatcher.isCidr("256.0.0.0/24"));
        assertFalse(IpMatcher.isCidr("/24"));
    }

    @Test
    void isWildcardIp_acceptsValid() {
        assertTrue(IpMatcher.isWildcardIp("172.198.2.x"));
        assertTrue(IpMatcher.isWildcardIp("192.168.x.x"));
        assertTrue(IpMatcher.isWildcardIp("10.0.x.x"));
        assertTrue(IpMatcher.isWildcardIp("x.x.x.x"));
        assertTrue(IpMatcher.isWildcardIp("172.198.2.x")); 
    }

    @Test
    void isWildcardIp_rejectsInvalid() {
        assertFalse(IpMatcher.isWildcardIp(null));
        assertFalse(IpMatcher.isWildcardIp(""));
        assertFalse(IpMatcher.isWildcardIp("192.168.1.1"));

        assertFalse(IpMatcher.isWildcardIp("172.x.2.1"));
        assertFalse(IpMatcher.isWildcardIp("x.168.1.1"));
    }

    @Test
    void wildcardToCidr_convertsCorrectly() {
        assertEquals("172.198.2.0/24", IpMatcher.wildcardToCidr("172.198.2.x"));
        assertEquals("192.168.0.0/16", IpMatcher.wildcardToCidr("192.168.x.x"));
        assertEquals("10.0.0.0/8", IpMatcher.wildcardToCidr("10.x.x.x"));
    }

    @Test
    void wildcardToCidr_rejectsLeadingWildcard() {
        assertNull(IpMatcher.wildcardToCidr("x.198.2.1"));
    }

    @Test
    void normalizeIpOrCidr_passesThroughIpAndCidr() {
        assertEquals("192.168.1.1", IpMatcher.normalizeIpOrCidr("192.168.1.1"));
        assertEquals("10.0.0.0/8", IpMatcher.normalizeIpOrCidr("10.0.0.0/8"));
    }

    @Test
    void normalizeIpOrCidr_convertsWildcard() {
        assertEquals("172.198.2.0/24", IpMatcher.normalizeIpOrCidr("172.198.2.x"));
    }

    @Test
    void normalizeIpOrCidr_returnsNullForInvalid() {
        assertNull(IpMatcher.normalizeIpOrCidr(null));
        assertNull(IpMatcher.normalizeIpOrCidr("not-an-ip"));
        assertNull(IpMatcher.normalizeIpOrCidr(""));
    }

    @Test
    void isPrivateOrReserved_blocksRfc1918() {
        assertTrue(IpMatcher.isPrivateOrReserved("10.0.0.1"));
        assertTrue(IpMatcher.isPrivateOrReserved("172.16.5.5"));
        assertTrue(IpMatcher.isPrivateOrReserved("192.168.1.1"));
        assertTrue(IpMatcher.isPrivateOrReserved("127.0.0.1"));
        assertTrue(IpMatcher.isPrivateOrReserved("0.0.0.0"));
        assertTrue(IpMatcher.isPrivateOrReserved("169.254.1.1"));
    }

    @Test
    void isPrivateOrReserved_blocksCidrOverlap() {

        assertTrue(IpMatcher.isPrivateOrReserved("10.255.255.255/16"));

        assertTrue(IpMatcher.isPrivateOrReserved("172.20.0.0/16"));
    }

    @Test
    void isPrivateOrReserved_allowsPublicIps() {
        assertFalse(IpMatcher.isPrivateOrReserved("8.8.8.8"));
        assertFalse(IpMatcher.isPrivateOrReserved("1.1.1.1"));
        assertFalse(IpMatcher.isPrivateOrReserved("172.32.0.1"));
    }

    @Test
    void isPrivateOrReserved_blocksIPv6Ula() {

        assertTrue(IpMatcher.isPrivateOrReserved("fc00::1"));
        assertTrue(IpMatcher.isPrivateOrReserved("fd12::1"));
        assertTrue(IpMatcher.isPrivateOrReserved("fe80::1"));
        assertTrue(IpMatcher.isPrivateOrReserved("::1"));
        assertTrue(IpMatcher.isPrivateOrReserved("::ffff:192.168.1.1"));
        assertFalse(IpMatcher.isPrivateOrReserved("2001:db8::1"));
    }

    @Test
    void cidrMatches_basicSubnetMatching() {
        assertTrue(IpMatcher.cidrMatches("10.0.0.5", "10.0.0.0/24"));
        assertTrue(IpMatcher.cidrMatches("10.0.0.255", "10.0.0.0/24"));
        assertFalse(IpMatcher.cidrMatches("10.0.1.0", "10.0.0.0/24"));
    }

    @Test
    void cidrMatches_slash8() {
        assertTrue(IpMatcher.cidrMatches("172.198.5.5", "172.0.0.0/8"));
        assertFalse(IpMatcher.cidrMatches("173.0.0.0", "172.0.0.0/8"));
    }

    @Test
    void cidrMatches_slash32() {
        assertTrue(IpMatcher.cidrMatches("1.2.3.4", "1.2.3.4/32"));
        assertFalse(IpMatcher.cidrMatches("1.2.3.5", "1.2.3.4/32"));
    }

    @Test
    void cidrMatches_rejectsInvalid() {
        assertFalse(IpMatcher.cidrMatches(null, "10.0.0.0/8"));
        assertFalse(IpMatcher.cidrMatches("10.0.0.1", null));
        assertFalse(IpMatcher.cidrMatches("not-ip", "10.0.0.0/8"));
        assertFalse(IpMatcher.cidrMatches("10.0.0.1", "10.0.0.0"));
    }

    @Test
    void isLoopback_detectsLocalhost() {
        assertTrue(IpMatcher.isLoopback("127.0.0.1"));
        assertTrue(IpMatcher.isLoopback("127.0.0.1/32"));
        assertTrue(IpMatcher.isLoopback("0.0.0.0"));
    }

    @Test
    void isLoopback_rejectsPublic() {
        assertFalse(IpMatcher.isLoopback("8.8.8.8"));
        assertFalse(IpMatcher.isLoopback(null));
    }

    @Test
    void isIpv6_acceptsValidAddresses() {
        assertTrue(IpMatcher.isIpv6("::1"));
        assertTrue(IpMatcher.isIpv6("2001:db8::1"));
        assertTrue(IpMatcher.isIpv6("2001:0db8:0000:0000:0000:0000:0000:0001"));
        assertTrue(IpMatcher.isIpv6("[::1]"));
        assertTrue(IpMatcher.isIpv6("::ffff:192.168.1.1"));
        assertTrue(IpMatcher.isIpv6("fe80::1%eth0"));
    }

    @Test
    void isIpv6_rejectsInvalid() {
        assertFalse(IpMatcher.isIpv6(null));
        assertFalse(IpMatcher.isIpv6(""));
        assertFalse(IpMatcher.isIpv6("1.2.3.4"));
        assertFalse(IpMatcher.isIpv6("abc:def"));
        assertFalse(IpMatcher.isIpv6("1:2:3:4:5:6:7:8:9"));
        assertFalse(IpMatcher.isIpv6("1:2:3:4:5:6:7"));
        assertFalse(IpMatcher.isIpv6("1:::2"));
        assertFalse(IpMatcher.isIpv6("gggg::1"));
    }

    @Test
    void isIpv6Cidr_acceptsValid() {
        assertTrue(IpMatcher.isIpv6Cidr("2001:db8::/32"));
        assertTrue(IpMatcher.isIpv6Cidr("::1/128"));
        assertFalse(IpMatcher.isIpv6Cidr("2001:db8::/129"));
        assertFalse(IpMatcher.isIpv6Cidr("1.2.3.4/24"));
    }

    @Test
    void normalizeIpOrCidr_canonicalizesIpv6() {
        assertEquals("2001:db8:0:0:0:0:0:1", IpMatcher.normalizeIpOrCidr("2001:DB8::1"));
        assertEquals("0:0:0:0:0:0:0:1", IpMatcher.normalizeIpOrCidr("::1"));
        assertEquals("2001:db8:0:0:0:0:0:0/32", IpMatcher.normalizeIpOrCidr("2001:db8::/32"));
    }

    @Test
    void normalizeIpOrCidr_keepsIpv4MappedIpv6CidrSemantics() {
        // prefix < 96 时不能折叠成 IPv4，否则 /90 会被错误缩成 /0 覆盖整个 IPv4 空间
        assertEquals("0:0:0:0:0:ffff:808:808/90", IpMatcher.normalizeIpOrCidr("::ffff:8.8.8.8/90"));
        assertEquals("8.8.8.8/24", IpMatcher.normalizeIpOrCidr("::ffff:8.8.8.8/120"));
    }

    @Test
    void looksLikeIp_rejectsFloodgateDotNames() {
        assertFalse(IpMatcher.looksLikeIp(".Steve"));
        assertFalse(IpMatcher.looksLikeIp("Steve."));
        assertTrue(IpMatcher.looksLikeIp("192.168.1.1"));
        assertTrue(IpMatcher.looksLikeIp("172.198.2.x"));
        assertTrue(IpMatcher.looksLikeIp("2001:db8::1"));
        assertTrue(IpMatcher.looksLikeIp("2001:db8::/32"));
    }

    @Test
    void cidrMatches_ipv6() {
        assertTrue(IpMatcher.cidrMatches("2001:db8::1", "2001:db8::/32"));
        assertTrue(IpMatcher.cidrMatches("::1", "::1/128"));
        assertFalse(IpMatcher.cidrMatches("2001:db9::1", "2001:db8::/32"));
    }

    @Test
    void sameIp_handlesBothFamilies() {
        assertTrue(IpMatcher.sameIp("192.168.1.1", "192.168.001.1"));
        assertTrue(IpMatcher.sameIp("2001:db8::1", "2001:0db8:0:0:0:0:0:1"));
        assertFalse(IpMatcher.sameIp("2001:db8::1", "2001:db8::2"));
    }

    @Test
    void isLoopback_detectsIpv6Loopback() {
        assertTrue(IpMatcher.isLoopback("::1"));
        assertTrue(IpMatcher.isLoopback("0:0:0:0:0:0:0:1"));
        assertFalse(IpMatcher.isLoopback("2001:db8::1"));
    }
}

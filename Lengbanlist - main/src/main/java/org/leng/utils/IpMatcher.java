package org.leng.utils;

public class IpMatcher {
    private IpMatcher() {
    }

    public static boolean isIpv4(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean isCidr(String value) {
        if (value == null) return false;
        int slash = value.indexOf('/');
        if (slash <= 0 || slash == value.length() - 1) return false;
        if (!isIpv4(value.substring(0, slash))) return false;
        try {
            int prefix = Integer.parseInt(value.substring(slash + 1));
            return prefix >= 0 && prefix <= 32;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidIpOrCidr(String value) {
        return isIpv4(value) || isCidr(value);
    }

    public static long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | Long.parseLong(parts[i]);
        }
        return result;
    }

    public static boolean cidrMatches(String ip, String cidr) {
        if (ip == null || cidr == null || !isIpv4(ip) || !isCidr(cidr)) return false;
        int slash = cidr.indexOf('/');
        String base = cidr.substring(0, slash);
        int prefix = Integer.parseInt(cidr.substring(slash + 1));
        long ipLong = ipToLong(ip);
        long baseLong = ipToLong(base);
        long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (ipLong & mask) == (baseLong & mask);
    }
}

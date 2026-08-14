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

    public static boolean isWildcardIp(String value) {
        if (value == null || value.isEmpty()) return false;
        String[] parts = value.split("\\.");
        if (parts.length != 4) return false;
        boolean inWildcard = false;
        boolean hasWildcard = false;
        for (String part : parts) {
            if (part.equalsIgnoreCase("x")) {
                inWildcard = true;
                hasWildcard = true;
            } else {
                if (inWildcard) return false;
                try {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) return false;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }
        return hasWildcard;
    }

    public static String wildcardToCidr(String value) {
        if (!isWildcardIp(value)) return null;
        String[] parts = value.split("\\.");
        int firstX = -1;
        for (int i = 0; i < 4; i++) {
            if (parts[i].equalsIgnoreCase("x")) {
                firstX = i;
                break;
            }
        }
        if (firstX == 0) return null;
        int prefix = (firstX == 3) ? 24 : (firstX == 2) ? 16 : (firstX == 1) ? 8 : 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i < firstX) {
                sb.append(parts[i]);
            } else {
                sb.append("0");
            }
            if (i < 3) sb.append(".");
        }
        sb.append("/").append(prefix);
        return sb.toString();
    }

    public static String normalizeIpOrCidr(String value) {
        if (value == null) return null;
        if (isWildcardIp(value)) {
            return wildcardToCidr(value);
        }
        if (isCidr(value) || isIpv4(value)) return value;
        return null;
    }

    public static boolean isValidIpOrCidrOrWildcard(String value) {
        if (isWildcardIp(value)) {
            return wildcardToCidr(value) != null;
        }
        return isIpv4(value) || isCidr(value);
    }

    public static boolean isValidIpOrCidr(String value) {
        return isIpv4(value) || isCidr(value);
    }

    public static boolean isLoopback(String value) {
        if (value == null) return false;
        String normalized = normalizeIpOrCidr(value);
        if (normalized == null) return false;
        String base = normalized;
        if (isCidr(normalized)) base = normalized.substring(0, normalized.indexOf('/'));
        return base.startsWith("127.") || base.equals("0.0.0.0") || base.equals("::1");
    }

    /** 是否私有/保留地址（禁止封禁，避免误封整个内网）。 */
    public static boolean isPrivateOrReserved(String value) {
        if (value == null) return false;
        String normalized = normalizeIpOrCidr(value);
        if (normalized == null) return false;
        String base = normalized;
        if (isCidr(normalized)) base = normalized.substring(0, normalized.indexOf('/'));
        if (base.startsWith("127.") || base.equals("0.0.0.0") || base.equals("::1")) return true;
        if (base.startsWith("10.")) return true;
        if (base.startsWith("192.168.")) return true;
        if (base.startsWith("169.254.")) return true; // 链路本地
        if (base.startsWith("172.")) {
            int dot = base.indexOf('.', 4);
            if (dot > 0) {
                try {
                    int secondOctet = Integer.parseInt(base.substring(4, dot));
                    if (secondOctet >= 16 && secondOctet <= 31) return true;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (base.startsWith("fd") || base.startsWith("fc")) return true; // IPv6 唯一本地
        return false;
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

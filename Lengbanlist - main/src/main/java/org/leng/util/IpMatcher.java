package org.leng.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class IpMatcher {

    private static final String[] PRIVATE_OR_RESERVED_CIDRS = {
            "0.0.0.0/8",
            "10.0.0.0/8",
            "127.0.0.0/8",
            "169.254.0.0/16",
            "172.16.0.0/12",
            "192.168.0.0/16"
    };

    private static final byte[][] IPV6_RESERVED_BASES;
    private static final int[] IPV6_RESERVED_PREFIXES = {128, 128, 7, 10, 10};

    static {
        IPV6_RESERVED_BASES = new byte[][]{
                parseIpv6Bytes("::"),
                parseIpv6Bytes("::1"),
                parseIpv6Bytes("fc00::"),
                parseIpv6Bytes("fe80::"),
                parseIpv6Bytes("fec0::")
        };
    }

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

    /** 无斜杠的 IPv6 地址（支持 {@code [::1]} 方括号与 {@code %scope} 后缀）。 */
    public static boolean isIpv6(String value) {
        return parseIpv6Bytes(value) != null;
    }

    public static boolean isIp(String value) {
        return isIpv4(value) || isIpv6(value);
    }

    /** 命令输入是否"看起来像 IP"：含点或含冒号。用于区分玩家名与 IP/CIDR 参数。 */
    public static boolean looksLikeIp(String value) {
        return value != null && (value.indexOf('.') >= 0 || value.indexOf(':') >= 0);
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

    public static boolean isIpv6Cidr(String value) {
        if (value == null) return false;
        int slash = value.indexOf('/');
        if (slash <= 0 || slash == value.length() - 1) return false;
        if (!isIpv6(value.substring(0, slash))) return false;
        try {
            int prefix = Integer.parseInt(value.substring(slash + 1));
            return prefix >= 0 && prefix <= 128;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** IPv4 / IPv6 的 CIDR。 */
    public static boolean isCidrLike(String value) {
        return isCidr(value) || isIpv6Cidr(value);
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

    /**
     * 统一成可直接存库、可直接比较的形态：
     * 通配符转 CIDR，IPv6 转 Java 的完整 8 段小写形式（与
     * {@code InetAddress#getHostAddress()} 的输出一致，运行期玩家 IP 也走同一形态）。
     */
    public static String normalizeIpOrCidr(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (isWildcardIp(trimmed)) {
            return wildcardToCidr(trimmed);
        }
        if (isCidr(trimmed) || isIpv4(trimmed)) return trimmed;
        if (isIpv6Cidr(trimmed)) {
            int slash = trimmed.indexOf('/');
            byte[] bytes = parseIpv6Bytes(trimmed.substring(0, slash));
            if (bytes == null) return null;
            int prefix = Integer.parseInt(trimmed.substring(slash + 1));
            String mapped = ipv4MappedAddress(bytes);
            if (mapped != null) {
                return mapped + "/" + Math.max(0, prefix - 96);
            }
            return normalizeIpv6(trimmed.substring(0, slash)) + "/" + prefix;
        }
        if (isIpv6(trimmed)) {
            return normalizeIpv6(trimmed);
        }
        return null;
    }

    public static String normalizeIpv6(String value) {
        byte[] bytes = parseIpv6Bytes(value);
        if (bytes == null) return null;
        String mapped = ipv4MappedAddress(bytes);
        if (mapped != null) {
            return mapped;
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** 两个地址是否相同（IPv4/IPv6 均按规范化形态比较）。 */
    public static boolean sameIp(String first, String second) {
        if (isIpv4(first) && isIpv4(second)) {
            return ipToLong(first) == ipToLong(second);
        }
        String left = normalizeIpOrCidr(first);
        String right = normalizeIpOrCidr(second);
        return left != null && left.equalsIgnoreCase(right);
    }

    public static boolean isValidIpOrCidrOrWildcard(String value) {
        if (isWildcardIp(value)) {
            return wildcardToCidr(value) != null;
        }
        return isIpv4(value) || isCidr(value) || isIpv6(value) || isIpv6Cidr(value);
    }

    public static boolean isValidIpOrCidr(String value) {
        return isIpv4(value) || isCidr(value) || isIpv6(value) || isIpv6Cidr(value);
    }

    public static boolean isLoopback(String value) {
        String normalized = normalizeIpOrCidr(value);
        if (normalized == null) return false;
        boolean cidr = isCidrLike(normalized);
        String base = cidr ? normalized.substring(0, normalized.indexOf('/')) : normalized;
        if (isIpv4(base)) {
            return base.startsWith("127.") || base.equals("0.0.0.0");
        }
        byte[] bytes = parseIpv6Bytes(base);
        if (bytes == null) return false;
        boolean allZero = true;
        for (int i = 0; i < bytes.length; i++) {
            boolean lastIsOne = i == bytes.length - 1 && bytes[i] == 1;
            if (bytes[i] != 0 && !lastIsOne) {
                allZero = false;
                break;
            }
        }
        return allZero;
    }

    public static boolean isPrivateOrReserved(String value) {
        String normalized = normalizeIpOrCidr(value);
        if (normalized == null) return false;

        boolean cidr = isCidrLike(normalized);
        String base = cidr ? normalized.substring(0, normalized.indexOf('/')) : normalized;

        if (isIpv4(base)) {
            int v4Prefix = cidr ? Integer.parseInt(normalized.substring(normalized.indexOf('/') + 1)) : 32;
            return isPrivateOrReservedIpv4(base, v4Prefix);
        }

        byte[] bytes = parseIpv6Bytes(base);
        if (bytes == null) return false;

        int prefix = cidr ? Integer.parseInt(normalized.substring(normalized.indexOf('/') + 1)) : 128;

        String mappedIpv4 = ipv4MappedAddress(bytes);
        if (mappedIpv4 != null) {
            // ::ffff:0:0/96 映射到 IPv4，前缀要减去 96 位再按 IPv4 保留段判断
            return isPrivateOrReservedIpv4(mappedIpv4, Math.max(0, prefix - 96));
        }

        for (int i = 0; i < IPV6_RESERVED_BASES.length; i++) {
            if (ipv6Overlaps(bytes, prefix, IPV6_RESERVED_BASES[i], IPV6_RESERVED_PREFIXES[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrivateOrReservedIpv4(String base, int prefix) {
        String candidateCidr = base + "/" + prefix;
        for (String restrictedCidr : PRIVATE_OR_RESERVED_CIDRS) {
            if (cidrOverlaps(candidateCidr, restrictedCidr)) return true;
        }
        return false;
    }

    private static boolean cidrOverlaps(String first, String second) {
        String firstBase = first.substring(0, first.indexOf('/'));
        String secondBase = second.substring(0, second.indexOf('/'));
        return cidrMatches(firstBase, second) || cidrMatches(secondBase, first);
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
        if (ip == null || cidr == null) return false;
        int slash = cidr.indexOf('/');
        if (slash <= 0 || slash == cidr.length() - 1) return false;
        String base = cidr.substring(0, slash);
        int prefix;
        try {
            prefix = Integer.parseInt(cidr.substring(slash + 1));
        } catch (NumberFormatException e) {
            return false;
        }

        if (isIpv4(ip) && isIpv4(base)) {
            if (prefix < 0 || prefix > 32) return false;
            long ipLong = ipToLong(ip);
            long baseLong = ipToLong(base);
            long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            return (ipLong & mask) == (baseLong & mask);
        }

        byte[] ipBytes = parseIpv6Bytes(ip);
        byte[] baseBytes = parseIpv6Bytes(base);
        if (ipBytes == null || baseBytes == null) return false;
        if (prefix < 0 || prefix > 128) return false;
        return prefixMatches(ipBytes, baseBytes, prefix);
    }

    private static boolean prefixMatches(byte[] first, byte[] second, int prefix) {
        int fullBytes = prefix / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (first[i] != second[i]) return false;
        }
        int remainder = prefix % 8;
        if (remainder == 0) return true;
        int mask = (0xFF << (8 - remainder)) & 0xFF;
        return (first[fullBytes] & mask) == (second[fullBytes] & mask);
    }

    private static boolean ipv6Overlaps(byte[] first, int firstPrefix, byte[] second, int secondPrefix) {
        return prefixMatches(first, second, Math.min(firstPrefix, secondPrefix));
    }

    /** {@code ::ffff:a.b.c.d} 形式的 IPv4 映射地址，返回点分十进制；否则 null。 */
    private static String ipv4MappedAddress(byte[] bytes) {
        if (bytes == null || bytes.length != 16) return null;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) return null;
        }
        if ((bytes[10] & 0xFF) != 0xFF || (bytes[11] & 0xFF) != 0xFF) return null;
        return (bytes[12] & 0xFF) + "." + (bytes[13] & 0xFF) + "."
                + (bytes[14] & 0xFF) + "." + (bytes[15] & 0xFF);
    }

    /**
     * 手写 IPv6 解析，避免 {@link InetAddress#getByName(String)} 在异常输入上做 DNS。
     *
     * @return 16 字节地址；非法输入返回 {@code null}
     */
    public static byte[] parseIpv6Bytes(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.length() >= 2 && text.charAt(0) == '[' && text.charAt(text.length() - 1) == ']') {
            text = text.substring(1, text.length() - 1);
        }
        if (text.isEmpty() || text.indexOf(':') < 0) return null;

        int zone = text.indexOf('%');
        if (zone >= 0) {
            if (zone == text.length() - 1) return null;
            text = text.substring(0, zone);
        }

        int doubleColon = text.indexOf("::");
        if (doubleColon >= 0 && text.indexOf("::", doubleColon + 1) >= 0) return null;

        String head = doubleColon >= 0 ? text.substring(0, doubleColon) : text;
        String tail = doubleColon >= 0 ? text.substring(doubleColon + 2) : null;
        // 点分 IPv4 只能出现在整个地址的末尾，不能出现在 "::" 前面
        if (doubleColon >= 0 && head.indexOf('.') >= 0) return null;

        List<Integer> headGroups = parseIpv6Groups(head);
        List<Integer> tailGroups = tail == null ? List.of() : parseIpv6Groups(tail);
        if (headGroups == null || tailGroups == null) return null;

        int total = headGroups.size() + tailGroups.size();
        if (doubleColon >= 0) {
            if (total >= 8) return null;
        } else if (total != 8) {
            return null;
        }

        int[] groups = new int[8];
        int index = 0;
        for (int group : headGroups) {
            groups[index++] = group;
        }
        index += 8 - total;
        for (int group : tailGroups) {
            groups[index++] = group;
        }

        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            bytes[i * 2] = (byte) (groups[i] >> 8);
            bytes[i * 2 + 1] = (byte) groups[i];
        }
        return bytes;
    }

    private static List<Integer> parseIpv6Groups(String part) {
        List<Integer> groups = new ArrayList<>();
        if (part.isEmpty()) return groups;
        String[] tokens = part.split(":", -1);
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.isEmpty()) return null;
            if (token.indexOf('.') >= 0) {
                if (i != tokens.length - 1) return null;
                String[] v4 = token.split("\\.", -1);
                if (v4.length != 4) return null;
                int[] nums = new int[4];
                for (int j = 0; j < 4; j++) {
                    try {
                        nums[j] = Integer.parseInt(v4[j]);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    if (nums[j] < 0 || nums[j] > 255) return null;
                }
                groups.add((nums[0] << 8) | nums[1]);
                groups.add((nums[2] << 8) | nums[3]);
            } else {
                if (token.length() > 4) return null;
                int group = 0;
                for (int j = 0; j < token.length(); j++) {
                    int digit = Character.digit(token.charAt(j), 16);
                    if (digit < 0) return null;
                    group = (group << 4) | digit;
                }
                groups.add(group);
            }
        }
        return groups;
    }
}

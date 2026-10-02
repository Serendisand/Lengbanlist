package org.leng.api;

import java.util.Locale;

/**
 * 契约版本比较与范围判断。
 *
 * <p>扩展在 {@link LengbanlistExtension#requiredApi()} 里写一个范围，核心在启用前校验，
 * 不满足就拒绝启用并给出可读原因——而不是等到运行时抛出 {@code NoSuchMethodError}。
 *
 * <p>支持的写法：
 *
 * <ul>
 *   <li>空字符串 —— 不校验，一律通过</li>
 *   <li>精确：{@code 2.1.6}（三位及以上按精确匹配）</li>
 *   <li>部分版本：{@code 2} 视为 {@code 2.x}，{@code 2.1} 视为 {@code 2.1.x}</li>
 *   <li>通配：{@code 2.x}、{@code 2.*}、{@code 2.1.x}、{@code 2.1.*}</li>
 *   <li>区间：{@code [2.0,3.0)}、{@code [2.0,3.0]}、{@code (2.0,3.0)}、{@code (2.0,3.0]}</li>
 * </ul>
 *
 * <p>版本号按数字段逐个比较，缺失的段视为 0；{@code -SNAPSHOT} 一类后缀会被忽略。
 *
 * <p>把 {@code 2.1} 当作 {@code 2.1.x} 而不是"正好 2.1.0"，是因为后者是明显的陷阱：
 * 作者写 {@code requiredApi: "2.1"} 的本意绝不会是"只兼容 2.1.0"，而精确语义会让扩展
 * 在核心每次修 bug 后都被拒绝启用。想要"2.1 以上"请写区间 {@code [2.1,3.0)}。
 */
public final class ApiVersion {

    private ApiVersion() {
    }

    /** 判断 {@code actualVersion} 是否落在 {@code requiredRange} 内。 */
    public static boolean satisfies(String requiredRange, String actualVersion) {
        if (requiredRange == null || requiredRange.isBlank()) {
            return true;
        }
        String required = requiredRange.trim();
        int[] actual = parse(actualVersion);
        if (actual.length == 0) {
            return false;
        }
        char first = required.charAt(0);
        if (first == '[' || first == '(') {
            return satisfiesRange(required, actual);
        }
        String lower = required.toLowerCase(Locale.ROOT);
        if (lower.indexOf('x') >= 0 || lower.indexOf('*') >= 0) {
            return satisfiesWildcard(lower, actual);
        }
        int[] want = parse(required);
        if (want.length == 0) {
            return false;
        }
        if (want.length < 3) {
            // 段数不足三位按前缀匹配：2 -> 2.x，2.1 -> 2.1.x
            return satisfiesPrefix(want, actual);
        }
        return compare(actual, want) == 0;
    }

    private static boolean satisfiesPrefix(int[] want, int[] actual) {
        for (int i = 0; i < want.length; i++) {
            int value = i < actual.length ? actual[i] : 0;
            if (value != want[i]) {
                return false;
            }
        }
        return true;
    }

    /** 逐段比较：a &lt; b 返回负数，相等返回 0，a &gt; b 返回正数。 */
    public static int compare(String a, String b) {
        return compare(parse(a), parse(b));
    }

    static int compare(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    /** 解析成数字段；遇到没有数字的段即停止。 */
    static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return new int[0];
        }
        String cleaned = version.trim();
        int dash = cleaned.indexOf('-');
        if (dash > 0) {
            cleaned = cleaned.substring(0, dash);
        }
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }
        String[] parts = cleaned.split("\\.");
        int[] out = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            String digits = leadingDigits(part);
            if (digits.isEmpty()) {
                break;
            }
            try {
                out[count++] = Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                break;
            }
        }
        if (count == parts.length) {
            return out;
        }
        int[] trimmed = new int[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return trimmed;
    }

    private static String leadingDigits(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        return part.substring(0, end);
    }

    private static boolean satisfiesRange(String required, int[] actual) {
        int comma = required.indexOf(',');
        if (comma < 0) {
            return false;
        }
        char open = required.charAt(0);
        char close = required.charAt(required.length() - 1);
        if (close != ']' && close != ')') {
            return false;
        }
        String minText = required.substring(1, comma).trim();
        String maxText = required.substring(comma + 1, required.length() - 1).trim();

        int[] min = parse(minText);
        int[] max = parse(maxText);
        if (!minText.isEmpty()) {
            int low = compare(actual, min);
            if (open == '[' ? low < 0 : low <= 0) {
                return false;
            }
        }
        if (!maxText.isEmpty()) {
            int high = compare(actual, max);
            if (close == ']' ? high > 0 : high >= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean satisfiesWildcard(String required, int[] actual) {
        String[] parts = required.split("\\.");
        if (parts.length > actual.length) {
            return false;
        }
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if ("x".equals(part) || "*".equals(part)) {
                return true;
            }
            String digits = leadingDigits(part);
            if (digits.isEmpty()) {
                return false;
            }
            if (Integer.parseInt(digits) != (i < actual.length ? actual[i] : 0)) {
                return false;
            }
        }
        return true;
    }
}

package org.leng.api;

/**
 * 时长字符串解析：{@code 30s}、{@code 10m}、{@code 7d}、{@code forever}……
 *
 * <p>与核心的处罚时长输入是同一套规则——核心的 {@code TimeUtils} 也委托到这里，
 * 扩展不必再抄一份：
 *
 * <ul>
 *   <li>单位：{@code s} 秒、{@code m} 分、{@code h} 时、{@code d} 天、
 *       {@code w} 周、{@code M} 月、{@code y} 年（{@code m} 与 {@code M} 不是一回事）；</li>
 *   <li>{@code forever} / {@code perm} / {@code permanent} 返回 {@link Long#MAX_VALUE}；</li>
 *   <li>解析失败或数值溢出返回 {@code -1}。</li>
 * </ul>
 */
public final class DurationParser {

    private DurationParser() {
    }

    /** 解析时长字符串；失败返回 {@code -1}。 */
    public static long parseMillis(String text) {
        if (text == null || text.isEmpty()) {
            return -1L;
        }
        if (text.equalsIgnoreCase("forever") || text.equalsIgnoreCase("perm")
                || text.equalsIgnoreCase("permanent")) {
            return Long.MAX_VALUE;
        }
        try {
            char unit = text.charAt(text.length() - 1);
            long amount = Long.parseLong(text.substring(0, text.length() - 1));
            return switch (unit) {
                case 's', 'S' -> secondsToMillis(amount);
                case 'm' -> minutesToMillis(amount);
                case 'h', 'H' -> hoursToMillis(amount);
                case 'd', 'D' -> daysToMillis(amount);
                case 'w', 'W' -> weeksToMillis(amount);
                case 'M' -> monthsToMillis(amount);
                case 'y', 'Y' -> yearsToMillis(amount);
                default -> -1L;
            };
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public static long secondsToMillis(long seconds) {
        return multiplyExactOrInvalid(seconds, 1_000L);
    }

    public static long minutesToMillis(long minutes) {
        return multiplyExactOrInvalid(minutes, 60_000L);
    }

    public static long hoursToMillis(long hours) {
        return multiplyExactOrInvalid(hours, 3_600_000L);
    }

    public static long daysToMillis(long days) {
        return multiplyExactOrInvalid(days, 86_400_000L);
    }

    public static long weeksToMillis(long weeks) {
        return multiplyExactOrInvalid(weeks, 604_800_000L);
    }

    public static long monthsToMillis(long months) {
        return multiplyExactOrInvalid(months, 2_592_000_000L);
    }

    public static long yearsToMillis(long years) {
        return multiplyExactOrInvalid(years, 31_536_000_000L);
    }

    private static long multiplyExactOrInvalid(long value, long factor) {
        try {
            return Math.multiplyExact(value, factor);
        } catch (ArithmeticException e) {
            return -1L;
        }
    }
}

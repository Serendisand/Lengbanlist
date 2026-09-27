package org.leng.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimeUtils {
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    public static long secondsToMillis(long seconds) {
        return multiplyExactOrInvalid(seconds, 1000L);
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

    public static long parseTime(String timeStr) {
        return parseDurationToMillis(timeStr);
    }

    public static boolean isValidTime(String timeStr) {
        return isValidTimeFormat(timeStr);
    }

    public static long parseDurationToMillis(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return -1L;
        }

        if (timeStr.equalsIgnoreCase("forever") || timeStr.equalsIgnoreCase("perm") || timeStr.equalsIgnoreCase("permanent")) {
            return Long.MAX_VALUE;
        }

        try {
            char unit = timeStr.charAt(timeStr.length() - 1);
            long amount = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));

            switch (unit) {
                case 's':
                case 'S': return secondsToMillis(amount);
                case 'm': return minutesToMillis(amount);
                case 'h':
                case 'H': return hoursToMillis(amount);
                case 'd':
                case 'D': return daysToMillis(amount);
                case 'w':
                case 'W': return weeksToMillis(amount);
                case 'M': return monthsToMillis(amount);
                case 'y':
                case 'Y': return yearsToMillis(amount);
                default: return -1L;
            }
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public static long ceilToSecond(long millis) {
        if (millis <= 0 || millis == Long.MAX_VALUE) return millis;
        long remainder = millis % 1000L;
        return remainder == 0 ? millis : millis + (1000L - remainder);
    }

    public static String formatDuration(long millis) {
        return formatDuration(millis, false);
    }

    public static String formatDuration(long millis, boolean english) {
        if (millis == Long.MAX_VALUE) return english ? "permanently" : "永久";
        if (millis <= 0) return english ? "0 seconds" : "0秒";

        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        if (seconds < 60) return unit(seconds, "秒", "second", english);

        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (minutes < 60) return unit(minutes, "分钟", "minute", english);

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours < 24) return unit(hours, "小时", "hour", english);

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        if (days < 7) return unit(days, "天", "day", english);

        long weeks = days / 7;
        if (weeks < 4) return unit(weeks, "周", "week", english);

        long months = days / 30;
        if (months < 12) return unit(months, "个月", "month", english);

        return unit(days / 365, "年", "year", english);
    }

    private static String unit(long value, String chineseUnit, String englishUnit, boolean english) {
        if (!english) return value + chineseUnit;
        return value + " " + englishUnit + (value == 1 ? "" : "s");
    }

    public static boolean isEnglishLocale() {
        try {
            String name = org.leng.manager.ModelManager.getInstance().getCurrentModelName();
            return name != null && name.equalsIgnoreCase("english");
        } catch (Exception e) {
            return false;
        }
    }

    public static String timestampToReadable(long timestamp) {
        if (timestamp == Long.MAX_VALUE) return "永久";
        return DATE_FORMAT.get().format(new Date(timestamp));
    }

    public static String getRemainingTime(long endTime) {
        if (endTime == Long.MAX_VALUE) return "永久";

        long remaining = endTime - System.currentTimeMillis();
        if (remaining <= 0) return "已过期";

        return formatDuration(remaining);
    }

    public static String formatRemaining(long endTime) {
        return formatRemaining(endTime, isEnglishLocale());
    }

    public static String formatRemaining(long endTime, boolean english) {
        if (endTime == Long.MAX_VALUE) return english ? "permanently" : "永久";

        long remaining = endTime - System.currentTimeMillis();
        if (remaining <= 0) return english ? "expired" : "已到期";

        return formatDuration(remaining, english);
    }

    public static String formatIssuedDuration(long startTime, long endTime) {
        return formatIssuedDuration(startTime, endTime, isEnglishLocale());
    }

    public static String formatIssuedDuration(long startTime, long endTime, boolean english) {
        if (endTime == Long.MAX_VALUE) return english ? "permanently" : "永久";
        if (startTime <= 0 || startTime >= endTime) return english ? "unknown" : "未知";

        return formatDuration(endTime - startTime, english);
    }

    public static boolean isValidTimeFormat(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return false;

        if (timeStr.equalsIgnoreCase("forever") ||
            timeStr.equalsIgnoreCase("perm") ||
            timeStr.equalsIgnoreCase("permanent")) {
            return true;
        }

        if (timeStr.equalsIgnoreCase("auto")) return true;

        return timeStr.matches("^\\d+[smhdwMy]$");
    }

    public static long currentTime() {
        return System.currentTimeMillis();
    }

    public static long calculateEndTime(long durationMillis) {
        if (durationMillis == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (durationMillis <= 0) {
            return System.currentTimeMillis();
        }
        long now = System.currentTimeMillis();
        if (Long.MAX_VALUE - now < durationMillis) {
            return Long.MAX_VALUE;
        }
        return now + durationMillis;
    }
}

package org.leng.util;

import org.leng.api.DurationParser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimeUtils {
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    // 单位换算与解析都委托给契约模块，确保扩展看到的是同一套规则
    public static long secondsToMillis(long seconds) {
        return DurationParser.secondsToMillis(seconds);
    }

    public static long minutesToMillis(long minutes) {
        return DurationParser.minutesToMillis(minutes);
    }

    public static long hoursToMillis(long hours) {
        return DurationParser.hoursToMillis(hours);
    }

    public static long daysToMillis(long days) {
        return DurationParser.daysToMillis(days);
    }

    public static long weeksToMillis(long weeks) {
        return DurationParser.weeksToMillis(weeks);
    }

    public static long monthsToMillis(long months) {
        return DurationParser.monthsToMillis(months);
    }

    public static long yearsToMillis(long years) {
        return DurationParser.yearsToMillis(years);
    }

    public static long parseTime(String timeStr) {
        return parseDurationToMillis(timeStr);
    }

    public static boolean isValidTime(String timeStr) {
        return isValidTimeFormat(timeStr);
    }

    public static long parseDurationToMillis(String timeStr) {
        return DurationParser.parseMillis(timeStr);
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
            String name = org.leng.integration.ModelManager.getInstance().getCurrentModelName();
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

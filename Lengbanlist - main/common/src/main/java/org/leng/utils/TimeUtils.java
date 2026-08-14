package org.leng.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimeUtils {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    public static long secondsToMillis(long seconds) {
        return seconds * 1000L;
    }

    public static long minutesToMillis(long minutes) {
        return minutes * 60L * 1000;
    }

    public static long hoursToMillis(long hours) {
        return hours * 60L * 60 * 1000;
    }

    public static long daysToMillis(long days) {
        return days * 24L * 60 * 60 * 1000;
    }

    public static long weeksToMillis(long weeks) {
        return weeks * 7L * 24 * 60 * 60 * 1000;
    }

    public static long monthsToMillis(long months) {
        return months * 30L * 24 * 60 * 60 * 1000;
    }

    public static long yearsToMillis(long years) {
        return years * 365L * 24 * 60 * 60 * 1000;
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


    public static String formatDuration(long millis) {
        if (millis == Long.MAX_VALUE) return "永久";
        if (millis <= 0) return "0秒";

        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        if (seconds < 60) return seconds + "秒";

        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (minutes < 60) return minutes + "分钟";

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours < 24) return hours + "小时";

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        if (days < 7) return days + "天";

        long weeks = days / 7;
        if (weeks < 4) return weeks + "周";

        long months = days / 30;
        if (months < 12) return months + "个月";

        long years = days / 365;
        return years + "年";
    }


    public static String timestampToReadable(long timestamp) {
        if (timestamp == Long.MAX_VALUE) return "永久";
        return DATE_FORMAT.format(new Date(timestamp));
    }


    public static String getRemainingTime(long endTime) {
        if (endTime == Long.MAX_VALUE) return "永久";

        long remaining = endTime - System.currentTimeMillis();
        if (remaining <= 0) return "已过期";

        return formatDuration(remaining);
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

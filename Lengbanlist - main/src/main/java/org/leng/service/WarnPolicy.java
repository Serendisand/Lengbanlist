package org.leng.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.leng.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 警告自动封禁（LBAC）策略。
 *
 * <p>默认行为与旧版写死逻辑一致：30 天内累计 3 次警告触发第一档，
 * 之后每多 3 次升一档，时长按 1d/7d/30d/90d/180d/365d 递增。
 */
public record WarnPolicy(
        boolean enabled,
        int threshold,
        long windowMillis,
        List<Long> durations,
        boolean autoUnban,
        boolean broadcast,
        String reasonTemplate
) {

    public static final String DEFAULT_REASON =
            "LBAC自动封禁（累计{warnings}次警告，第{trigger}次触发）";

    private static final List<Long> DEFAULT_DURATIONS = List.of(
            TimeUtils.daysToMillis(1),
            TimeUtils.daysToMillis(7),
            TimeUtils.daysToMillis(30),
            TimeUtils.daysToMillis(90),
            TimeUtils.daysToMillis(180),
            TimeUtils.daysToMillis(365)
    );

    private static final long DEFAULT_WINDOW = TimeUtils.daysToMillis(30);
    private static final Pattern STEP_MARKER = Pattern.compile("\\[LBAC#(\\d+)]");
    private static final Pattern LEGACY_TRIGGER = Pattern.compile("第(\\d+)次触发");

    public WarnPolicy {
        threshold = Math.max(1, threshold);
        windowMillis = Math.max(0L, windowMillis);
        List<Long> safe = durations == null ? List.of() : durations.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .toList();
        durations = safe.isEmpty() ? DEFAULT_DURATIONS : List.copyOf(safe);
        reasonTemplate = reasonTemplate == null || reasonTemplate.isBlank()
                ? DEFAULT_REASON : reasonTemplate;
    }

    public static WarnPolicy defaults() {
        return new WarnPolicy(true, 3, DEFAULT_WINDOW, DEFAULT_DURATIONS, true, true, DEFAULT_REASON);
    }

    public static WarnPolicy fromConfig(FileConfiguration config, Logger logger) {
        if (config == null) {
            return defaults();
        }
        boolean enabled = config.getBoolean("warn-escalation.enabled", true);
        int threshold = config.getInt("warn-escalation.threshold", 3);
        if (threshold < 1) {
            warn(logger, "warn-escalation.threshold 必须大于 0，已回退为 3");
            threshold = 3;
        }
        long window = parseWindow(config.getString("warn-escalation.window", "30d"), logger);
        List<Long> durations = parseDurations(config.getStringList("warn-escalation.durations"), logger);
        boolean autoUnban = config.getBoolean("warn-escalation.auto-unban", true);
        boolean broadcast = config.getBoolean("warn-escalation.broadcast", true);
        String reason = config.getString("warn-escalation.reason", DEFAULT_REASON);
        return new WarnPolicy(enabled, threshold, window, durations, autoUnban, broadcast, reason);
    }

    private static long parseWindow(String raw, Logger logger) {
        if (raw == null || raw.isBlank()
                || raw.equalsIgnoreCase("0")
                || raw.equalsIgnoreCase("off")
                || raw.equalsIgnoreCase("unlimited")
                || raw.equalsIgnoreCase("forever")) {
            return 0L;
        }
        long parsed = TimeUtils.parseDurationToMillis(raw.trim());
        if (parsed == Long.MAX_VALUE || parsed == 0L) {
            return 0L;
        }
        if (parsed < 0) {
            warn(logger, "warn-escalation.window 格式无效（" + raw + "），已回退为 30d");
            return DEFAULT_WINDOW;
        }
        return parsed;
    }

    private static List<Long> parseDurations(List<String> raw, Logger logger) {
        if (raw == null || raw.isEmpty()) {
            return DEFAULT_DURATIONS;
        }
        List<Long> parsed = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            long duration = TimeUtils.parseDurationToMillis(value.trim());
            if (duration <= 0) {
                warn(logger, "warn-escalation.durations 中的时长无效（" + value + "），已跳过");
                continue;
            }
            parsed.add(duration);
        }
        if (parsed.isEmpty()) {
            warn(logger, "warn-escalation.durations 没有有效时长，已回退为默认阶梯");
            return DEFAULT_DURATIONS;
        }
        return parsed;
    }

    private static void warn(Logger logger, String message) {
        if (logger != null) {
            logger.warning("[warn-escalation] " + message);
        }
    }

    public boolean withinWindow(long warnTime, long now) {
        return windowMillis <= 0 || now - warnTime <= windowMillis;
    }

    public int triggerCount(int warningCount) {
        if (warningCount < threshold) {
            return 0;
        }
        return warningCount / threshold;
    }

    public long durationForTrigger(int triggerCount) {
        int index = triggerCount <= 0
                ? durations.size() - 1
                : Math.min(triggerCount - 1, durations.size() - 1);
        return durations.get(Math.max(0, index));
    }

    public String formatReason(int warningCount, int triggerCount) {
        String base = reasonTemplate
                .replace("{warnings}", String.valueOf(warningCount))
                .replace("{trigger}", String.valueOf(triggerCount));
        base = STEP_MARKER.matcher(base).replaceAll("").trim();
        if (base.isEmpty()) {
            base = DEFAULT_REASON
                    .replace("{warnings}", String.valueOf(warningCount))
                    .replace("{trigger}", String.valueOf(triggerCount));
        }
        return base + " §8[LBAC#" + triggerCount + "]";
    }

    /**
     * 从封禁原因里解析上一次触发的档位。
     * 新格式优先读 {@code [LBAC#N]}，老数据回退到“第 N 次触发”。
     */
    public static int parseTrigger(String reason) {
        if (reason == null || reason.isEmpty()) {
            return 0;
        }
        Matcher marker = STEP_MARKER.matcher(reason);
        if (marker.find()) {
            return parseInt(marker.group(1));
        }
        Matcher legacy = LEGACY_TRIGGER.matcher(reason);
        if (legacy.find()) {
            return parseInt(legacy.group(1));
        }
        return 0;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

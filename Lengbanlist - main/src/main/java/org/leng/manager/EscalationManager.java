package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.utils.TimeUtils;

import java.util.List;

public class EscalationManager {
    private final Lengbanlist plugin;
    private final DatabaseManager db;

    public EscalationManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public EscalationResult resolveBan(String target) {
        if (plugin.isFeatureEnabled("escalation")) {
            int count = db.countBanHistory(target);
            return new EscalationResult(tierDuration(count), count);
        }
        return new EscalationResult(legacyWarnBased(target), 0);
    }

    public EscalationResult resolveIpBan(String ip) {
        if (plugin.isFeatureEnabled("escalation")) {
            int count = db.countIpBanHistory(ip);
            return new EscalationResult(tierDuration(count), count);
        }
        return new EscalationResult(legacyWarnBased(ip), 0);
    }

    public long resolveMute(String target) {
        return legacyWarnBased(target);
    }

    private long tierDuration(int count) {
        List<String> tiers = plugin.getConfig().getStringList("escalation.tiers");
        if (tiers == null || tiers.isEmpty()) {
            return TimeUtils.daysToMillis(7);
        }
        int index = Math.max(0, Math.min(count, tiers.size() - 1));
        long duration = TimeUtils.parseDurationToMillis(tiers.get(index));
        return duration > 0 ? duration : TimeUtils.daysToMillis(7);
    }

    private long legacyWarnBased(String target) {
        int warnCount = Math.max(0, plugin.getWarnManager().getActiveWarnings(target).size());
        long duration;
        switch (warnCount) {
            case 0: duration = TimeUtils.daysToMillis(1); break;
            case 1: duration = TimeUtils.daysToMillis(3); break;
            case 2: duration = TimeUtils.daysToMillis(7); break;
            case 3: duration = TimeUtils.daysToMillis(14); break;
            case 4: duration = TimeUtils.daysToMillis(30); break;
            default: duration = Long.MAX_VALUE; break;
        }
        return Math.max(duration, TimeUtils.daysToMillis(1));
    }

    public static class EscalationResult {
        public final long durationMillis;
        public final int offenseCount;

        public EscalationResult(long durationMillis, int offenseCount) {
            this.durationMillis = durationMillis;
            this.offenseCount = offenseCount;
        }
    }
}

package org.leng.service;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.leng.util.TimeUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarnPolicyTest {

    @Test
    void defaultsMatchLegacyBehaviour() {
        WarnPolicy policy = WarnPolicy.fromConfig(new YamlConfiguration(), null);

        assertTrue(policy.enabled());
        assertEquals(3, policy.threshold());
        assertEquals(TimeUtils.daysToMillis(30), policy.windowMillis());
        assertEquals(6, policy.durations().size());
        assertEquals(TimeUtils.daysToMillis(1), policy.durations().get(0));
        assertEquals(TimeUtils.daysToMillis(365), policy.durations().get(5));
        assertTrue(policy.autoUnban());
        assertTrue(policy.broadcast());
    }

    @Test
    void readsCustomValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("warn-escalation.enabled", false);
        config.set("warn-escalation.threshold", 5);
        config.set("warn-escalation.window", "7d");
        config.set("warn-escalation.durations", List.of("1h", "1d", "7d"));
        config.set("warn-escalation.auto-unban", false);
        config.set("warn-escalation.broadcast", false);
        config.set("warn-escalation.reason", "自定义 {warnings}/{trigger}");

        WarnPolicy policy = WarnPolicy.fromConfig(config, null);

        assertFalse(policy.enabled());
        assertEquals(5, policy.threshold());
        assertEquals(TimeUtils.daysToMillis(7), policy.windowMillis());
        assertEquals(List.of(TimeUtils.hoursToMillis(1), TimeUtils.daysToMillis(1),
                TimeUtils.daysToMillis(7)), policy.durations());
        assertFalse(policy.autoUnban());
        assertFalse(policy.broadcast());
        assertEquals("自定义 5/1 §8[LBAC#1]", policy.formatReason(5, 1));
    }

    @Test
    void invalidThresholdFallsBackToThree() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("warn-escalation.threshold", 0);

        assertEquals(3, WarnPolicy.fromConfig(config, null).threshold());
    }

    @Test
    void invalidWindowFallsBackToThirtyDays() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("warn-escalation.window", "not-a-duration");

        assertEquals(TimeUtils.daysToMillis(30), WarnPolicy.fromConfig(config, null).windowMillis());
    }

    @Test
    void zeroWindowMeansUnlimited() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("warn-escalation.window", "0");

        WarnPolicy policy = WarnPolicy.fromConfig(config, null);

        assertEquals(0L, policy.windowMillis());
        assertTrue(policy.withinWindow(System.currentTimeMillis() - TimeUtils.daysToMillis(9999),
                System.currentTimeMillis()));
    }

    @Test
    void invalidDurationsAreSkipped() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("warn-escalation.durations", List.of("1d", "oops", "2d"));

        WarnPolicy policy = WarnPolicy.fromConfig(config, null);

        assertEquals(List.of(TimeUtils.daysToMillis(1), TimeUtils.daysToMillis(2)),
                policy.durations());
    }

    @Test
    void emptyDurationsFallBackToDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("warn-escalation.durations", List.of());

        assertEquals(6, WarnPolicy.fromConfig(config, null).durations().size());
    }

    @Test
    void foreverDurationIsKept() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("warn-escalation.durations", List.of("1d", "forever"));

        WarnPolicy policy = WarnPolicy.fromConfig(config, null);

        assertEquals(Long.MAX_VALUE, policy.durationForTrigger(99));
    }

    @Test
    void triggerCountUsesFloorDivision() {
        WarnPolicy policy = WarnPolicy.fromConfig(new YamlConfiguration(), null);

        assertEquals(0, policy.triggerCount(2));
        assertEquals(1, policy.triggerCount(3));
        assertEquals(1, policy.triggerCount(5));
        assertEquals(2, policy.triggerCount(6));
        assertEquals(3, policy.triggerCount(9));
    }

    @Test
    void durationForTriggerCapsAtLastEntry() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("warn-escalation.durations", List.of("1d", "7d"));

        WarnPolicy policy = WarnPolicy.fromConfig(config, null);

        assertEquals(TimeUtils.daysToMillis(1), policy.durationForTrigger(1));
        assertEquals(TimeUtils.daysToMillis(7), policy.durationForTrigger(2));
        assertEquals(TimeUtils.daysToMillis(7), policy.durationForTrigger(100));
        assertEquals(TimeUtils.daysToMillis(7), policy.durationForTrigger(0));
    }

    @Test
    void windowFilterWorks() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("warn-escalation.window", "7d");
        WarnPolicy policy = WarnPolicy.fromConfig(config, null);
        long now = System.currentTimeMillis();

        assertTrue(policy.withinWindow(now - TimeUtils.daysToMillis(1), now));
        assertFalse(policy.withinWindow(now - TimeUtils.daysToMillis(8), now));
    }

    @Test
    void reasonKeepsPlaceholderValuesAndMarker() {
        WarnPolicy policy = WarnPolicy.fromConfig(new YamlConfiguration(), null);

        String reason = policy.formatReason(6, 2);

        assertTrue(reason.contains("累计6次警告"));
        assertTrue(reason.contains("第2次触发"));
        assertTrue(reason.contains("[LBAC#2]"));
    }

    @Test
    void parsesNewMarkerAndLegacyText() {
        assertEquals(2, WarnPolicy.parseTrigger("LBAC自动封禁 §8[LBAC#2]"));
        assertEquals(3, WarnPolicy.parseTrigger("旧数据（累计9次警告，第3次触发）"));
        assertEquals(0, WarnPolicy.parseTrigger("普通手动封禁"));
        assertEquals(0, WarnPolicy.parseTrigger(null));
    }
}

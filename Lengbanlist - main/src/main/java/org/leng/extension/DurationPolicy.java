package org.leng.extension;

import org.leng.Lengbanlist;
import org.leng.api.DurationPolicyHook;
import org.leng.util.TimeUtils;

/**
 * 自动时长闸门：核心里"auto 该判多久"的唯一入口。
 *
 * <p>结构与 {@link PunishmentGate} 对称，同样把三件事分开：
 * 功能是否启用（问注册表）、策略是什么（问登记在 {@code escalation} 上的钩子）、
 * 策略缺席怎么办（用核心内置兜底）。
 *
 * <p>{@link #legacyWarnBased} 是核心的兜底规则，同时也是 {@code escalation} 钩子
 * 对禁言的处理方式——放在这里而不是各写一份，是为了避免两处漂移。
 */
public final class DurationPolicy {

    /** 累犯升级对应的功能键。 */
    public static final String ESCALATION_FEATURE = "escalation";

    private final Lengbanlist plugin;

    public DurationPolicy(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    /** auto 封禁时长。 */
    public DurationPolicyHook.Decision autoBan(String target) {
        DurationPolicyHook hook = hook();
        return hook != null
                ? hook.ban(target)
                : DurationPolicyHook.Decision.of(legacyWarnBased(plugin, target));
    }

    /** auto IP 封禁时长。 */
    public DurationPolicyHook.Decision autoIpBan(String ip) {
        DurationPolicyHook hook = hook();
        return hook != null
                ? hook.ipBan(ip)
                : DurationPolicyHook.Decision.of(legacyWarnBased(plugin, ip));
    }

    /** auto 禁言时长。 */
    public DurationPolicyHook.Decision autoMute(String target) {
        DurationPolicyHook hook = hook();
        return hook != null
                ? hook.mute(target)
                : DurationPolicyHook.Decision.of(legacyWarnBased(plugin, target));
    }

    private DurationPolicyHook hook() {
        ExtensionRegistry registry = plugin.getExtensionRegistry();
        if (registry == null || !registry.isFeatureActive(ESCALATION_FEATURE)) {
            return null;
        }
        return registry.hook(ESCALATION_FEATURE, DurationPolicyHook.class);
    }

    /**
     * 核心内置兜底：按未撤销警告数决定时长。
     *
     * <p>0 次 1 天 / 1 次 3 天 / 2 次 7 天 / 3 次 14 天 / 4 次 30 天 / 5 次及以上永久。
     * 这是改造前 {@code features.escalation} 关闭时的行为，一字未改。
     */
    public static long legacyWarnBased(Lengbanlist plugin, String target) {
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
}

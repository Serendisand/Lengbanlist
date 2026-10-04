package org.leng.service;

import org.leng.Lengbanlist;
import org.leng.api.DurationPolicyHook;
import org.leng.extension.DurationPolicy;
import org.leng.util.TimeUtils;

import java.util.List;
import org.leng.storage.DatabaseManager;

/**
 * 累犯升级<b>策略</b>：实现 {@link DurationPolicyHook}，由核心作为内置提供者注册到
 * {@code escalation} 功能键上。
 *
 * <p>职责边界（Phase 0.6b 拆分后）：本类只回答"第 N 次封禁该判多久"——按
 * {@code escalation.tiers} 分级取时长。是否启用、以及策略缺席时的兜底都在
 * {@link DurationPolicy}，因此本类不再自行检查 {@code features.escalation}。
 *
 * <p><b>禁言是一个已知的例外</b>：{@link #mute} 恒按警告数给时长，不走累犯分级。
 * 这与改造前的 {@code resolveMute} 行为<b>完全一致</b>（功能审计已记录
 * "features.escalation 对禁言无效"）。修正它属于行为变更，待确认后再动；
 * 这里保持原状并把例外显式写在方法上，而不是让它藏在调用链里。
 */
public class EscalationManager implements DurationPolicyHook {

    private final Lengbanlist plugin;
    private final DatabaseManager db;

    public EscalationManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    @Override
    public Decision ban(String target) {
        int count = db.countBanHistory(target);
        return new Decision(tierDuration(count), count);
    }

    @Override
    public Decision ipBan(String ip) {
        int count = db.countIpBanHistory(ip);
        return new Decision(tierDuration(count), count);
    }

    @Override
    public Decision mute(String target) {
        // 与改造前一致：禁言时长恒按警告数，不走累犯分级（详见类注释）。
        return Decision.of(DurationPolicy.legacyWarnBased(plugin, target));
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
}

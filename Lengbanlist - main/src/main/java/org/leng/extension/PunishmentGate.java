package org.leng.extension;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.api.PunishmentDecisionHook;

/**
 * 处罚放行闸门：核心里"能不能罚"的唯一入口。
 *
 * <p>它把三件事分开，这是拆分的核心：
 *
 * <ol>
 *   <li><b>功能是否启用</b>——问注册表（已安装 且 开关为 true）</li>
 *   <li><b>策略是什么</b>——问登记在 {@code immunity} 功能键上的
 *       {@link PunishmentDecisionHook}</li>
 *   <li><b>策略缺席怎么办</b>——一律放行</li>
 * </ol>
 *
 * <p>第 3 条是刻意选的默认值：免疫系统是<b>可选增强</b>，它缺席的语义是"谁都能罚"，
 * 而不是"谁都罚不了"。一个可选功能绝不能在没装的时候把服务器的处罚能力整体关掉。
 *
 * <p>另注意 {@link #canPunish} 与 {@link #canPunishTarget} 的差别被完整保留：
 * 前者按玩家权重比较，后者对含 {@code .} 的目标按 IP 权重比较。改造前就是两个
 * 不同的方法，合并会改变线上行为（例如 {@code /history 1.2.3.4} 这类调用会开始
 * 被 IP 上玩家的权重拦下）。
 */
public final class PunishmentGate {

    /** 免疫系统对应的功能键。 */
    public static final String IMMUNITY_FEATURE = "immunity";

    private final Lengbanlist plugin;

    public PunishmentGate(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    /** 操作者能否处罚目标玩家（按玩家权重，不把 target 当 IP 解释）。 */
    public boolean canPunish(CommandSender sender, String target) {
        PunishmentDecisionHook hook = hook();
        return hook == null || hook.operatorWeight(sender) > hook.playerWeight(target);
    }

    /** 同上，但操作者权重由调用方给出（Web 面板等没有 CommandSender 的场景）。 */
    public boolean canPunish(int operatorWeight, String target) {
        PunishmentDecisionHook hook = hook();
        return hook == null || operatorWeight > hook.playerWeight(target);
    }

    /**
     * 目标可能是玩家名也可能是 IP：含 {@code .} 时按该 IP 上在线玩家的最高权重比较。
     *
     * <p>这就是改造前 {@code canPunishTarget} 的行为，一点没变。
     */
    public boolean canPunishTarget(int operatorWeight, String target) {
        PunishmentDecisionHook hook = hook();
        if (hook == null) {
            return true;
        }
        boolean ipTarget = target != null && target.contains(".");
        return operatorWeight > (ipTarget ? hook.ipWeight(target) : hook.playerWeight(target));
    }

    /** 操作者权重；钩子缺席时视为最高（放行一切）。 */
    public int staffWeight(CommandSender sender) {
        PunishmentDecisionHook hook = hook();
        return hook == null ? Integer.MAX_VALUE : hook.operatorWeight(sender);
    }

    /** Web 面板操作者权重；钩子缺席时视为最高。 */
    public int webOperatorWeight() {
        PunishmentDecisionHook hook = hook();
        return hook == null ? Integer.MAX_VALUE : hook.webOperatorWeight();
    }

    private PunishmentDecisionHook hook() {
        ExtensionRegistry registry = plugin.getExtensionRegistry();
        if (registry == null || !registry.isFeatureActive(IMMUNITY_FEATURE)) {
            return null;
        }
        return registry.hook(IMMUNITY_FEATURE, PunishmentDecisionHook.class);
    }
}

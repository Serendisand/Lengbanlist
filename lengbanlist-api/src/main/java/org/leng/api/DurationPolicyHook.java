package org.leng.api;

/**
 * 自动时长策略钩子：回答"这个目标的 auto 处罚该判多久"。
 *
 * <p>核心在玩家使用 {@code auto} 作为时长时查询本钩子（{@code /ban <玩家> auto <理由>}）。
 * 累犯升级（escalation）实现它。
 *
 * <p><b>钩子缺席时核心使用内置的兜底规则</b>——按该目标的未撤销警告数给时长
 * （0 次 1 天、1 次 3 天、2 次 7 天、3 次 14 天、4 次 30 天、5 次及以上永久）。
 * 这条兜底是核心里本来就有的行为，不是新增语义：改造前 {@code features.escalation}
 * 关闭时走的就是它。因此"没装升级扩展"与"关掉升级开关"结果一致，
 * 而<b>不会</b>退化成"用调用方请求的时长"（auto 场景下调用方根本没给时长）。
 */
public interface DurationPolicyHook {

    /** 自动封禁时长。 */
    Decision ban(String target);

    /** 自动 IP 封禁时长。 */
    Decision ipBan(String ip);

    /** 自动禁言时长。 */
    Decision mute(String target);

    /**
     * 一次时长决策。
     *
     * @param durationMillis 判罚时长（毫秒）
     * @param offenseCount   累犯次数；{@code > 0} 时核心会额外播报一条升级提示
     */
    record Decision(long durationMillis, int offenseCount) {

        public static Decision of(long durationMillis) {
            return new Decision(durationMillis, 0);
        }
    }
}

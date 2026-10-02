package org.leng.api;

/**
 * 钩子注册表：扩展用它把自己的策略挂进核心的决策点。
 *
 * <p>与命令注册一样，钩子的生命周期由核心管理：扩展停用时它注册过的钩子会被
 * 一并摘掉，核心随即回落到默认行为。因此"扩展被删除"永远等于"功能回到默认"，
 * 不会留下半个策略在跑。
 *
 * <p>每种类型每个扩展只能注册一个钩子；重复注册会覆盖。
 */
public interface HookRegistry {

    /**
     * 注册一个钩子。
     *
     * @param type 钩子接口类型，例如 {@link PunishmentDecisionHook PunishmentDecisionHook.class}
     * @param hook 实现实例
     */
    <T> void register(Class<T> type, T hook);

    /** 注销某类型的钩子；未注册时不做任何事。 */
    <T> void unregister(Class<T> type);
}

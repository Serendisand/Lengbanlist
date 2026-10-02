package org.leng.api;

import java.util.logging.Logger;

/**
 * 核心交给单个扩展的运行时上下文。
 *
 * <p>每个扩展拿到的是<b>自己专属</b>的实例：{@link #config()} 指向该扩展自己的配置文件，
 * {@link #logger()} 打出的日志自带扩展 id 前缀，{@link #commands()} 注册进去的命令
 * 也只属于这个扩展。
 *
 * <p>本接口只会<b>增加</b>方法，不会修改或删除已有方法；扩展作为消费方不受影响。
 * 后续会陆续加入数据存储、统计、占位符等能力。
 */
public interface ExtensionContext {

    /** 本上下文所属的扩展 id。 */
    String extensionId();

    /** 注册命令。扩展注册的命令会随该扩展启用/停用自动注册与注销。 */
    CommandRegistrar commands();

    /**
     * 注册钩子，把自己挂进核心的决策点（处罚放行、时长策略等）。
     *
     * <p>钩子同样随扩展启用/停用自动装卸；摘掉后核心回落到默认行为。
     */
    HookRegistry hooks();

    /** 该扩展私有的配置文件，位于 {@code plugins/<扩展名>/config.yml}。 */
    ExtensionConfig config();

    /**
     * 调度器。<b>务必使用它</b>而不是 {@code Bukkit.getScheduler()}：
     * 前者在 Folia 上会走对应的区域化调度，后者在 Folia 上会直接抛异常。
     */
    Scheduler scheduler();

    /** 带扩展 id 前缀的日志器。 */
    Logger logger();
}

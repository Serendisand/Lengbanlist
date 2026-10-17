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

    /**
     * 某个功能此刻是否生效：<b>归属提供者已启用 且 开关为 true</b>，与核心门控同一语义。
     *
     * <p>命令会被核心自动门控，但监听器与调度任务不会——扩展被核心停用后，Bukkit 插件
     * 本身并没有 disable，{@code registerEvents} 注册的监听器仍会触发。所以在事件与任务
     * 的入口问一句 {@code isFeatureActive(feature)}，是扩展侧需要自己做的事。
     *
     * <p>实现 {@link SelfGatedExtension} 的扩展问自己时，开关来自它自己的配置；
     * 其余扩展仍读核心 {@code config.yml} 的 {@code features.<键>}。
     *
     * <p>不要在 {@link SelfGatedExtension#isFeatureEnabled} 里调用它——那条路径就是它自己。
     */
    boolean isFeatureActive(String feature);

    /** 注册命令。扩展注册的命令会随该扩展启用/停用自动注册与注销。 */
    CommandRegistrar commands();

    /**
     * 注册钩子，把自己挂进核心的决策点（处罚放行、时长策略等）。
     *
     * <p>钩子同样随扩展启用/停用自动装卸；摘掉后核心回落到默认行为。
     */
    HookRegistry hooks();

    /**
     * 文案门面：取当前模型，让提示语带上服务器选定的人设口吻。
     * 取不到模型时 {@link Messages#current()} 返回 {@code null}，调用方需自行兜底。
     */
    Messages messages();

    /**
     * 数据门面：复用核心已建好的连接池读写扩展自己的表。
     * <b>不要在扩展里自建连接池</b>，理由见 {@link DataStore}。
     */
    DataStore data();

    /** 核心开放的读写服务（封禁 / 禁言 / 警告）。写处罚请走这里，不要直接写核心表。 */
    Services services();

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

package org.leng.api;

import java.util.Set;

/**
 * 核心向扩展暴露的门面，由核心注册到 Bukkit 的 {@code ServicesManager}。
 *
 * <p>扩展的取用方式（这是扩展唯一需要写死的核心调用）：
 *
 * <pre>{@code
 * LengbanlistCore core = Bukkit.getServicesManager().load(LengbanlistCore.class);
 * if (core == null) {
 *     getLogger().severe("未找到 Lengbanlist 核心，扩展无法启用");
 *     return;
 * }
 * core.enable(this);
 * }</pre>
 *
 * <p>之所以走 {@code ServicesManager} 而不是让扩展直接引用核心类：扩展只应编译依赖
 * {@code lengbanlist-api}，不应依赖核心实现 jar。这样核心内部重构不会牵连扩展。
 */
public interface LengbanlistCore {

    /** 核心提供的契约版本，形如 {@code 2.1.6}。 */
    String apiVersion();

    /**
     * 启用一个扩展：校验 {@link LengbanlistExtension#requiredApi()}、建立
     * {@link ExtensionContext}、调用 {@link LengbanlistExtension#onEnable}，
     * 并把该扩展注册的命令接入命令表。
     *
     * @return 该扩展的运行时上下文，扩展通常应保存它
     * @throws ExtensionLoadException 契约版本不满足、id 重复或扩展自身启用失败
     */
    ExtensionContext enable(LengbanlistExtension extension) throws ExtensionLoadException;

    /**
     * 停用并移除一个扩展，同时注销它注册的命令。
     *
     * <p>扩展应优先在自己的 {@code onDisable()} 里自行调用；核心在停服时也会兜底调用。
     */
    void disable(String extensionId);

    boolean isEnabled(String extensionId);

    /** 当前已启用的扩展 id 快照。 */
    Set<String> enabledExtensionIds();
}

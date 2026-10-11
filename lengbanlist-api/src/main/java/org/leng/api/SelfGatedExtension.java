package org.leng.api;

/**
 * 自带开关的扩展。
 *
 * <p>默认情况下，功能是否开启由核心读 {@code config.yml} 的 {@code features.<key>} 决定。
 * 实现本接口的扩展改为自己回答这个问题，核心不再查那份配置。
 *
 * <p>Lua 扩展就是这么做的：它的开关在扩展自己的目录里
 * （{@code extensions/<id>/config.yml}），跟着扩展一起安装与卸载，
 * 核心配置里不留它的任何键。这样"一个功能的开关只有一个来源"是结构性成立的，
 * 不会出现两份配置互相覆盖、其中一份静默失效的情况。
 *
 * <p>注意本接口只回答"开关是不是打开"，不回答"这个功能能不能用"——
 * 后者还要看扩展是否已注册，见 {@code ExtensionRegistry.isFeatureActive}。
 */
public interface SelfGatedExtension {

    boolean isFeatureEnabled(String feature);
}

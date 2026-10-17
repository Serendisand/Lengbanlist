package org.leng.api;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * 外部扩展的推荐基类：把"找到核心 → 启用 → 停用"这段样板收进来。
 *
 * <p>不继承它也能写扩展（直接实现 {@link LengbanlistExtension} 并自己调
 * {@link LengbanlistCore#enable}），但那段样板里每一步都有踩坑的余地：忘了在
 * {@code plugin.yml} 里 {@code depend} 就会拿到 {@code null}、启用失败后不
 * {@code disablePlugin} 会让插件停在半死状态、忘了在 {@code onDisable} 里注销就会在
 * {@code /reload} 之后留下幽灵命令。继承本类，这些都由基类一次做对。
 *
 * <p>子类要写的东西只有三样：
 *
 * <pre>{@code
 * public final class MyExtension extends LengbanlistExtensionPlugin {
 *
 *     @Override
 *     public String id() {
 *         return "myext";                  // 不写就取 plugin.yml 的 name（转小写、去掉非字母数字）
 *     }
 *
 *     @Override
 *     protected void onExtensionEnable(ExtensionContext context) {
 *         context.commands().register(CommandSpec.of("hello", id(), "lengbanlist.myext",
 *                 "/hello", "打个招呼", (sender, command, label, args) -> {
 *                     sender.sendMessage("hi");
 *                     return true;
 *                 }));
 *         context.scheduler().runSyncRepeating(() -> { ... }, 20L, 20L * 60);
 *     }
 * }
 * }</pre>
 *
 * <p>{@code plugin.yml} 里必须有 {@code depend: [Lengbanlist]}——它是扩展在运行时
 * 拿到本模块与核心类的唯一来源。
 *
 * <p>{@link #onEnable()} 与 {@link #onDisable()} 被基类收走了（{@code final}）：
 * 扩展的启用/停用逻辑请写在 {@link #onExtensionEnable(ExtensionContext)} 与
 * {@link #onExtensionDisable()} 里，这样"核心那边注册成功"与"扩展自己开始干活"
 * 永远是同一个时刻。
 */
public abstract class LengbanlistExtensionPlugin extends JavaPlugin implements LengbanlistExtension, SelfGatedExtension {

    private LengbanlistCore core;
    private ExtensionContext context;

    /**
     * Bukkit 的插件启用入口（无参）。
     *
     * <p>注意它与 {@link #onEnable(ExtensionContext)} 是<b>重载</b>而不是覆盖：前者是
     * Bukkit 的插件生命周期，后者是扩展契约里"核心已接受本扩展"的回调，由核心在
     * {@link LengbanlistCore#enable} 内部回调。两个都要实现，缺一个子类就得自己补。
     */
    @Override
    public final void onEnable() {
        core = Bukkit.getServicesManager().load(LengbanlistCore.class);
        if (core == null) {
            getLogger().severe("没有找到 Lengbanlist 核心：请确认服务端装了 Lengbanlist，"
                    + "并且本插件的 plugin.yml 里有 depend: [Lengbanlist]");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            // 核心会在 enable 内部回调 onEnable(ExtensionContext)，子类的逻辑在那里开始
            context = core.enable(this);
        } catch (ExtensionLoadException e) {
            getLogger().severe("扩展启用失败: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** 扩展契约的回调：核心接受本扩展后调用。子类请改写 {@link #onExtensionEnable}。 */
    @Override
    public final void onEnable(ExtensionContext context) {
        this.context = context;
        onExtensionEnable(context);
    }

    @Override
    public final void onDisable() {
        try {
            onExtensionDisable();
        } catch (Throwable t) {
            getLogger().warning("扩展停用时出错: " + t);
        }
        try {
            // 核心停用扩展时 Bukkit 插件本身并没有被 disable，监听器不会自动摘掉；
            // 不注销的话命令没了、事件还在跑。
            HandlerList.unregisterAll(this);
        } catch (Throwable t) {
            getLogger().warning("注销扩展监听器时出错: " + t);
        }
        if (core != null) {
            core.disable(id());
            core = null;
            context = null;
        }
    }

    /** 核心已接受本扩展：在这里注册命令、钩子、任务与监听器。 */
    protected abstract void onExtensionEnable(ExtensionContext context);

    /** 停用前的收尾。核心在扩展被注销时也会调到这里，可以放心依赖它做清理。 */
    protected void onExtensionDisable() {
    }

    /**
     * 扩展 id：默认取 {@code plugin.yml} 的 {@code name}，转小写并去掉非字母数字字符
     * （契约要求 id 全小写、无连字符）。想要别的 id 就覆写它。
     */
    @Override
    public String id() {
        return getDescription().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Override
    public String version() {
        return getDescription().getVersion();
    }

    /**
     * 扩展自己的开关：{@code plugins/<扩展名>/config.yml}。
     *
     * <p>{@code enabled} 是总开关（缺省 {@code true}）：关掉它，整个扩展的功能一起停。
     * 一个模块带多个功能键时，可以按功能键写 {@code features.<键>: false} 单独关掉某一个。
     * 开关只住在这份配置里，核心 {@code config.yml} 不再参与判断。
     */
    @Override
    public boolean isFeatureEnabled(String feature) {
        ExtensionContext current = context;
        FileConfiguration config = current != null ? current.config().raw() : getConfig();
        if (!config.getBoolean("enabled", true)) {
            return false;
        }
        return config.getBoolean("features." + feature, true);
    }

    /** 核心给的上下文；尚未启用或已停用时为 {@code null}。 */
    protected final ExtensionContext context() {
        return context;
    }
}

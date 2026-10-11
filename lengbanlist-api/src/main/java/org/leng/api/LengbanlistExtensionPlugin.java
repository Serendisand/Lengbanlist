package org.leng.api;

import org.bukkit.Bukkit;
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
public abstract class LengbanlistExtensionPlugin extends JavaPlugin implements LengbanlistExtension {

    private LengbanlistCore core;
    private ExtensionContext context;

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
            context = core.enable(this);
        } catch (ExtensionLoadException e) {
            getLogger().severe("扩展启用失败: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        onExtensionEnable(context);
    }

    @Override
    public final void onDisable() {
        try {
            onExtensionDisable();
        } catch (Throwable t) {
            getLogger().warning("扩展停用时出错: " + t);
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

    /** 核心给的上下文；尚未启用或已停用时为 {@code null}。 */
    protected final ExtensionContext context() {
        return context;
    }
}

package org.leng.extension;

import org.bukkit.plugin.Plugin;
import org.leng.Lengbanlist;
import org.leng.api.ApiVersion;
import org.leng.api.CommandRegistrar;
import org.leng.api.CommandSpec;
import org.leng.api.ExtensionContext;
import org.leng.api.ExtensionLoadException;
import org.leng.api.LengbanlistExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 扩展注册表：核心的"功能提供者"总账。
 *
 * <p>它同时管理两类提供者，两者走<b>完全相同</b>的注册路径：
 *
 * <ul>
 *   <li><b>内置</b>：仍是核心 jar 里的代码，由 {@link BuiltinExtensions} 注册。
 *       它们存在的意义是让"功能由提供者声明"这件事从第一天就成立，
 *       这样 Phase 1-3 把它们搬出 jar 时不需要再改机制。</li>
 *   <li><b>外部</b>：独立的 Bukkit 插件，通过 {@code LengbanlistCore.enable} 注册。</li>
 * </ul>
 *
 * <p>它同时是<b>统一门控</b>的唯一入口：{@link #isFeatureActive(String)}
 * 把"功能键"解析为"归属提供者 + 开关"两个条件。
 */
public final class ExtensionRegistry {

    private final Lengbanlist plugin;
    private final Map<String, Registration> byId = new LinkedHashMap<>();
    private final Map<String, Registration> byFeature = new LinkedHashMap<>();

    /** 注册表发生变化（新增/移除扩展）时回调，用于刷新命令表。 */
    private volatile Runnable onChanged;

    public ExtensionRegistry(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    // ---------------------------------------------------------------- 注册

    /**
     * 注册并启用一个扩展。
     *
     * <p>会先校验 id 与 {@code requiredApi}，再建立上下文并调用
     * {@link LengbanlistExtension#onEnable}。启用失败时本次注册会被完整回滚，
     * 不留半截状态。
     */
    public ExtensionContext register(LengbanlistExtension extension) throws ExtensionLoadException {
        if (extension == null) {
            throw new ExtensionLoadException("扩展实例为空");
        }
        String id = extension.id();
        if (id == null || id.isBlank()) {
            throw new ExtensionLoadException("扩展 id 为空");
        }
        if (byId.containsKey(id)) {
            throw new ExtensionLoadException("扩展 id 已被占用: " + id);
        }
        String coreVersion = plugin.getPluginVersion();
        if (!ApiVersion.satisfies(extension.requiredApi(), coreVersion)) {
            throw new ExtensionLoadException("扩展 " + id + " 需要的契约版本 " + extension.requiredApi()
                    + " 与当前核心 " + coreVersion + " 不兼容");
        }

        Registration reg = new Registration(extension);
        byId.put(id, reg);

        // 扩展主类通常同时是 Bukkit 插件（设计上如此），据此决定配置目录与日志前缀；
        // 内置提供者没有自己的插件，落在核心数据目录下的 extensions/ 里。
        Plugin owner = extension instanceof Plugin bukkitPlugin ? bukkitPlugin : null;
        ExtensionContextImpl context = new ExtensionContextImpl(plugin, id, new Registrar(reg), owner);
        reg.context = context;
        try {
            extension.onEnable(context);
        } catch (Exception e) {
            byId.remove(id);
            throw new ExtensionLoadException("扩展 " + id + " 启用失败: " + e.getMessage(), e);
        }

        for (CommandSpec spec : reg.commands) {
            byFeature.putIfAbsent(spec.feature(), reg);
        }
        notifyChanged();
        return context;
    }

    /** 停用并移除一个扩展，连带注销它注册过的命令。 */
    public void unregister(String extensionId) {
        Registration reg = byId.remove(extensionId);
        if (reg == null) {
            return;
        }
        reg.enabled = false;
        byFeature.values().removeIf(r -> r == reg);
        try {
            reg.extension.onDisable();
        } catch (Exception e) {
            plugin.getLogger().warning("扩展 " + extensionId + " 停用时出错: " + e);
        }
        notifyChanged();
    }

    public void unregisterAll() {
        for (String id : new ArrayList<>(byId.keySet())) {
            unregister(id);
        }
    }

    private void notifyChanged() {
        Runnable hook = onChanged;
        if (hook != null) {
            try {
                hook.run();
            } catch (Throwable t) {
                plugin.getLogger().warning("刷新扩展命令表时出错: " + t);
            }
        }
    }

    // ---------------------------------------------------------------- 查询

    public boolean isRegistered(String extensionId) {
        return byId.containsKey(extensionId);
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(byId.keySet()));
    }

    /** 全部已注册扩展贡献的命令声明，按注册顺序（内置在前）。 */
    public List<CommandSpec> commandSpecs() {
        List<CommandSpec> specs = new ArrayList<>();
        for (Registration reg : byId.values()) {
            if (reg.enabled) {
                specs.addAll(reg.commands);
            }
        }
        return specs;
    }

    /** 某个功能键归属的扩展 id；未知返回 {@code null}。 */
    public String ownerOf(String feature) {
        Registration reg = byFeature.get(feature);
        return reg == null ? null : reg.extension.id();
    }

    /**
     * 统一门控：功能是否生效 = 归属提供者已启用 <b>且</b> {@code features.<key>} 开关为 true。
     *
     * <p>对内置提供者而言"已启用"恒为真，因此行为与改造前完全一致；
     * 对外部扩展而言，未安装的扩展自然不生效——这正是 Phase 0.9 要的语义
     * （"已安装 且 开启"），且修掉了"缺失键默认 true"带来的误导。
     *
     * <p>未知功能键沿用旧语义（只看开关），以免尚未登记的功能被静默关闭。
     */
    public boolean isFeatureActive(String feature) {
        Registration reg = byFeature.get(feature);
        if (reg == null) {
            return plugin.isFeatureEnabled(feature);
        }
        return reg.enabled && plugin.isFeatureEnabled(feature);
    }

    // ---------------------------------------------------------------- 内部

    private static final class Registration {
        private final LengbanlistExtension extension;
        private final List<CommandSpec> commands = new ArrayList<>();
        private volatile boolean enabled = true;
        private ExtensionContextImpl context;

        private Registration(LengbanlistExtension extension) {
            this.extension = extension;
        }
    }

    /** 绑定到某个注册项的 {@link CommandRegistrar}。 */
    private static final class Registrar implements CommandRegistrar {
        private final Registration registration;

        private Registrar(Registration registration) {
            this.registration = registration;
        }

        @Override
        public void register(CommandSpec spec) {
            if (spec == null) {
                return;
            }
            if (!spec.isComplete()) {
                throw new IllegalArgumentException("命令声明不完整: name=" + spec.name()
                        + " feature=" + spec.feature() + " permission=" + spec.permission()
                        + " usage=" + spec.usage());
            }
            // 同名命令覆盖而不是叠加，保证重复注册是幂等的
            registration.commands.removeIf(existing -> existing.name().equals(spec.name()));
            registration.commands.add(spec);
        }

        @Override
        public List<String> registeredNames() {
            return registration.commands.stream().map(CommandSpec::name).toList();
        }
    }
}

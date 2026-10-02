package org.leng.extension;

import org.leng.Lengbanlist;
import org.leng.api.ExtensionContext;
import org.leng.api.ExtensionLoadException;
import org.leng.api.LengbanlistCore;
import org.leng.api.LengbanlistExtension;

import java.util.Set;

/**
 * {@link LengbanlistCore} 的实现，由核心注册进 Bukkit 的 {@code ServicesManager}，
 * 是外部扩展触达核心的唯一入口。
 *
 * <p>它本身刻意保持极薄：真正的状态与门控都在 {@link ExtensionRegistry}，
 * 这里只负责"把服务暴露出去"与打印面向服主的日志。
 */
public final class CoreService implements LengbanlistCore {

    private final Lengbanlist plugin;
    private final ExtensionRegistry registry;

    public CoreService(Lengbanlist plugin, ExtensionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public String apiVersion() {
        return plugin.getPluginVersion();
    }

    @Override
    public ExtensionContext enable(LengbanlistExtension extension) throws ExtensionLoadException {
        ExtensionContext context = registry.register(extension);
        String version = extension.version();
        plugin.getLogger().info("已启用扩展 " + extension.id()
                + (version == null || version.isBlank() ? "" : " v" + version)
                + "（" + registry.commandSpecs().size() + " 条命令在册）");
        return context;
    }

    @Override
    public void disable(String extensionId) {
        if (!registry.isRegistered(extensionId)) {
            return;
        }
        registry.unregister(extensionId);
        plugin.getLogger().info("已停用扩展 " + extensionId);
    }

    @Override
    public boolean isEnabled(String extensionId) {
        return registry.isRegistered(extensionId);
    }

    @Override
    public Set<String> enabledExtensionIds() {
        return registry.ids();
    }
}

package org.leng.extension.lua;

import org.leng.Lengbanlist;
import org.leng.api.ExtensionLoadException;
import org.leng.extension.ExtensionRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 扫描 {@code plugins/Lengbanlist/extensions/} 并加载其中的 Lua 扩展。
 *
 * <p><b>目录存在即已安装</b>，没有安装清单、没有迁移、没有第二个文件记录"装了什么"。
 * 卸载就是删目录。这样"安装状态"只有一个来源，不会出现清单与实际不同步。
 *
 * <p>单个扩展加载失败只影响它自己：记一条可读日志，核心照常启动。
 * 这里连 {@link Error} 都接住——脚本超时抛的 {@code Aborted} 就是 Error，
 * 漏出去会掀掉整个插件启用。
 */
public final class LuaLoader {

    public static final String DIRECTORY = "extensions";

    private final Lengbanlist plugin;
    private final ExtensionRegistry registry;
    private final List<String> loaded = new ArrayList<>();

    public LuaLoader(Lengbanlist plugin, ExtensionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public List<String> loadedIds() {
        return List.copyOf(loaded);
    }

    public void loadAll() {
        File root = new File(plugin.getDataFolder(), DIRECTORY);
        if (!root.isDirectory() && !root.mkdirs()) {
            plugin.getLogger().warning("创建 " + DIRECTORY + " 目录失败，Lua 扩展未加载");
            return;
        }
        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null || directories.length == 0) {
            return;
        }
        Arrays.sort(directories, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File directory : directories) {
            if (new File(directory, LuaExtension.ENTRY_FILE).isFile()) {
                load(directory);
            }
        }
        if (!loaded.isEmpty()) {
            plugin.getLogger().info("已加载 " + loaded.size() + " 个 Lua 扩展: " + String.join(", ", loaded));
        }
    }

    public void reload() {
        unloadAll();
        loadAll();
    }

    public void unloadAll() {
        for (String id : new ArrayList<>(loaded)) {
            registry.unregister(id);
        }
        loaded.clear();
    }

    private void load(File directory) {
        LuaExtension extension = new LuaExtension(plugin, registry, directory);
        // 功能键与已注册的提供者撞车时，byFeature.putIfAbsent 会静默丢弃新来的，
        // 于是扩展自己的开关根本没被读过——这正是"一个功能两个来源"的老毛病，
        // 必须喊出来而不是让它悄悄失效。
        String owner = registry.ownerOf(extension.id());
        if (owner != null) {
            plugin.getLogger().warning("Lua 扩展 " + extension.id() + " 跳过：功能键已被内置功能 "
                    + owner + " 占用，两者必须换掉一个");
            return;
        }
        try {
            registry.register(extension);
            loaded.add(extension.id());
        } catch (ExtensionLoadException e) {
            plugin.getLogger().warning("Lua 扩展 " + extension.id() + " 加载失败: " + e.getMessage());
        } catch (Throwable t) {
            plugin.getLogger().warning("Lua 扩展 " + extension.id() + " 加载失败: " + t);
        }
    }
}

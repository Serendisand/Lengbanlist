package org.leng.extension.lua;

import org.leng.Lengbanlist;
import org.leng.api.ExtensionLoadException;
import org.leng.extension.ExtensionRegistry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描 {@code plugins/Lengbanlist/extensions/} 并加载其中的 Lua 扩展。
 *
 * <p><b>目录存在即已安装</b>，没有安装清单、没有迁移、没有第二个文件记录"装了什么"。
 * 卸载就是删目录。这样"安装状态"只有一个来源，不会出现清单与实际不同步。
 *
 * <p>单个扩展加载失败只影响它自己：记一条可读日志，核心照常启动。
 * 这里连 {@link Error} 都接住——脚本超时抛的 {@code Aborted} 就是 Error，
 * 漏出去会掀掉整个插件启用。
 *
 * <p>脚本自己把 {@code enabled} 关掉、或者因死循环被强制停用之后，扩展仍在
 * {@link #extensions()} 列表里（这样 {@code /lban ext list} 才说得清"它怎么了"），
 * 但 {@link #isRunning(String)} 会回答"没在跑"。
 */
public final class LuaLoader {

    public static final String DIRECTORY = "extensions";

    /**
     * 随核心 jar 打包的官方扩展文件。
     *
     * <p>只在 {@code extensions/} <b>整个目录不存在</b>时释放一次：这样升级后
     * 老用户的 {@code /lban tp}、{@code /sc} 不会凭空消失，而管理员删掉某个官方扩展
     * 之后，下次启动也不会被"复活"。
     */
    private static final String[] BUNDLED_FILES = {
            "extensions/README.txt",
            "extensions/tp/extension.lua",
            "extensions/tp/config.yml",
            "extensions/staffchat/extension.lua",
            "extensions/staffchat/config.yml",
    };

    private final Lengbanlist plugin;
    private final ExtensionRegistry registry;
    private final Map<String, LuaExtension> discovered = new LinkedHashMap<>();
    private final Map<String, String> failures = new LinkedHashMap<>();

    public LuaLoader(Lengbanlist plugin, ExtensionRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    /** 扫到的扩展，按目录名排序；含被禁用、被强制停用的那些。 */
    public List<LuaExtension> extensions() {
        return List.copyOf(discovered.values());
    }

    /** 目录名 → 加载失败原因。给 {@code /lban ext list} 用。 */
    public Map<String, String> failures() {
        return Map.copyOf(failures);
    }

    /** 该扩展此刻是否真的挂在注册表上（被禁用或被停用都不算）。 */
    public boolean isRunning(String id) {
        return registry.isRegistered(id);
    }

    public String directory() {
        return DIRECTORY;
    }

    public void loadAll() {
        File root = root();
        ensureDefaults(root);
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
        if (!discovered.isEmpty()) {
            plugin.getLogger().info("已加载 " + discovered.size() + " 个 Lua 扩展: "
                    + String.join(", ", discovered.keySet()));
        }
    }

    public void reload() {
        unloadAll();
        loadAll();
    }

    public void unloadAll() {
        for (String id : new ArrayList<>(discovered.keySet())) {
            registry.unregister(id);
        }
        discovered.clear();
        failures.clear();
    }

    /**
     * 打开/关闭一个扩展。
     *
     * <p>写的是<b>扩展自己的</b> {@code config.yml}：核心配置里没有它的键，
     * 所以不会波及核心配置的注释，也不会留下第二个开关来源。
     *
     * @return 该目录不存在时返回 false
     */
    public boolean setEnabled(String id, boolean enabled) throws IOException {
        File directory = new File(root(), id);
        if (!directory.isDirectory()) {
            return false;
        }
        LuaConfigWriter.writeEnabled(new File(directory, LuaExtension.CONFIG_FILE), enabled);
        // 先摘再挂：关掉时命令要立刻消失，打开时要重新读一遍配置。
        // 无论开关，都重新注册一次——禁用状态的扩展留在注册表里（门控关着），
        // /lban ext list 才看得到它、也才能再被打开。
        registry.unregister(id);
        discovered.remove(id);
        failures.remove(id);
        load(directory);
        return true;
    }

    private File root() {
        return new File(plugin.getDataFolder(), DIRECTORY);
    }

    private void ensureDefaults(File root) {
        if (root.exists()) {
            return;
        }
        for (String path : BUNDLED_FILES) {
            try {
                plugin.saveResource(path, false);
            } catch (Throwable t) {
                plugin.getLogger().warning("释放默认扩展文件 " + path + " 失败: " + t);
            }
        }
    }

    private void load(File directory) {
        LuaExtension extension = new LuaExtension(plugin, registry, directory);
        // 功能键与已注册的提供者撞车时，byFeature.putIfAbsent 会静默丢弃新来的，
        // 于是扩展自己的开关根本没被读过——这正是"一个功能两个来源"的老毛病，
        // 必须喊出来而不是让它悄悄失效。
        String owner = registry.ownerOf(extension.id());
        if (owner != null) {
            String reason = "功能键已被内置功能 " + owner + " 占用，两者必须换掉一个";
            plugin.getLogger().warning("Lua 扩展 " + extension.id() + " 跳过：" + reason);
            failures.put(extension.id(), reason);
            return;
        }
        try {
            registry.register(extension);
            discovered.put(extension.id(), extension);
            failures.remove(extension.id());
        } catch (ExtensionLoadException e) {
            plugin.getLogger().warning("Lua 扩展 " + extension.id() + " 加载失败: " + e.getMessage());
            failures.put(extension.id(), e.getMessage());
        } catch (Throwable t) {
            plugin.getLogger().warning("Lua 扩展 " + extension.id() + " 加载失败: " + t);
            failures.put(extension.id(), String.valueOf(t));
        }
    }
}

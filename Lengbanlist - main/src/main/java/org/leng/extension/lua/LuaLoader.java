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
 * 扫描 {@code plugins/Lengbanlist/extensions/} 下的 {@code <扩展名>.lua} 并加载。
 *
 * <p><b>脚本在、扩展就在</b>，没有安装清单、没有迁移、没有第二个文件记录"装了什么"。
 * 卸载就是删脚本。这样"安装状态"只有一个来源，不会出现清单与实际不同步。
 *
 * <p><b>脚本平铺、配置下钻一层</b>：脚本是 {@code extensions/<id>.lua}，扩展自己的
 * {@code config.yml} 放在 {@code extensions/<id>/}。根目录因此一眼能看全装了哪些扩展，
 * 而配置不会和脚本挤在同一层、也不会互相串键。缺配置目录的会按需生成一份。
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

    /** 老布局的入口脚本名（{@code extensions/<id>/extension.lua}）。本版已不识别，只在日志里指路。 */
    private static final String LEGACY_ENTRY_FILE = "extension.lua";

    /**
     * 随核心 jar 打包的官方扩展文件。
     *
     * <p>只在 {@code extensions/} <b>整个目录不存在</b>时释放一次：这样升级后
     * 老用户的 {@code /lban tp}、{@code /sc} 不会凭空消失，而管理员删掉某个官方扩展
     * 之后，下次启动也不会被"复活"。
     */
    private static final String[] BUNDLED_FILES = {
            "extensions/README.txt",
            "extensions/tp.lua",
            "extensions/tp/config.yml",
            "extensions/staffchat.lua",
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
        warnAboutLegacyLayout(root);
        File[] scripts = root.listFiles(file -> file.isFile() && file.getName().endsWith(LuaExtension.ENTRY_SUFFIX));
        if (scripts == null || scripts.length == 0) {
            return;
        }
        Arrays.sort(scripts, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        for (File script : scripts) {
            load(script);
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
     * <p>写的是<b>扩展自己的</b> {@code extensions/<id>/config.yml}：核心配置里没有它的键，
     * 所以不会波及核心配置的注释，也不会留下第二个开关来源。配置目录不存在时顺手生成。
     *
     * @return 该扩展的脚本不存在时返回 false
     */
    public boolean setEnabled(String id, boolean enabled) throws IOException {
        if (!isSafeId(id)) {
            return false;
        }
        File script = scriptFile(id);
        if (!script.isFile()) {
            return false;
        }
        File config = new File(new File(root(), id), LuaExtension.CONFIG_FILE);
        LuaConfigWriter.ensureConfig(config, id);
        LuaConfigWriter.writeEnabled(config, enabled);
        // 先摘再挂：关掉时命令要立刻消失，打开时要重新读一遍配置。
        // 无论开关，都重新注册一次——禁用状态的扩展留在注册表里（门控关着），
        // /lban ext list 才看得到它、也才能再被打开。
        registry.unregister(id);
        discovered.remove(id);
        failures.remove(id);
        load(script);
        return true;
    }

    private File root() {
        return new File(plugin.getDataFolder(), DIRECTORY);
    }

    private File scriptFile(String id) {
        return new File(root(), id + LuaExtension.ENTRY_SUFFIX);
    }

    /**
     * id 直接来自 {@code /lban ext enable <id>} 的输入，得挡住 {@code ../} 这类东西：
     * 否则一句命令就能把 config.yml 写到数据目录外面去。扫描出来的 id 来自真实文件名，
     * 天然安全，这道关卡只对命令入口有意义。
     */
    private static boolean isSafeId(String id) {
        return id != null && !id.isBlank()
                && id.indexOf('/') < 0 && id.indexOf('\\') < 0 && !id.contains("..");
    }

    /**
     * 老布局（{@code extensions/<id>/extension.lua}）本版不再识别，但也不能让它悄悄消失——
     * 管理员看到的是"功能没了"，而不是"目录放错了"。逐个目录指路，说清该挪去哪。
     */
    private void warnAboutLegacyLayout(File root) {
        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null) {
            return;
        }
        for (File directory : directories) {
            if (!new File(directory, LEGACY_ENTRY_FILE).isFile()) {
                continue;
            }
            String id = directory.getName();
            plugin.getLogger().warning("扩展 " + id + " 用的是旧布局（" + DIRECTORY + "/" + id + "/"
                    + LEGACY_ENTRY_FILE + "），本版已不再识别；把脚本挪到 "
                    + DIRECTORY + "/" + id + LuaExtension.ENTRY_SUFFIX + " 即可，"
                    + "配置目录 " + DIRECTORY + "/" + id + "/ 原地不动。");
        }
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

    private void load(File script) {
        LuaExtension extension = new LuaExtension(plugin, registry, script);
        // 文件名叫 ".lua" 的取出来是空 id，注册表会拒；这里直接放过，省一条没头没脑的报错
        if (extension.id().isBlank()) {
            return;
        }
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
            ensureConfig(extension);
        } catch (ExtensionLoadException e) {
            plugin.getLogger().warning("Lua 扩展 " + extension.id() + " 加载失败: " + e.getMessage());
            failures.put(extension.id(), e.getMessage());
        } catch (Throwable t) {
            plugin.getLogger().warning("Lua 扩展 " + extension.id() + " 加载失败: " + t);
            failures.put(extension.id(), String.valueOf(t));
        }
    }

    /**
     * 给还没有配置文件的扩展生成一份。
     *
     * <p>放在注册<b>之后</b>：配置是给管理员改的，脚本本身不依赖它（脚本里
     * {@code leng.config(键, 默认值)} 自带默认值），所以生成失败最多是少一个文件，
     * 不该把已经跑起来的扩展拖下水。
     */
    private void ensureConfig(LuaExtension extension) {
        try {
            if (LuaConfigWriter.ensureConfig(extension.configFile(), extension.id())) {
                plugin.getLogger().info("已生成扩展配置 " + DIRECTORY + "/" + extension.id()
                        + "/" + LuaExtension.CONFIG_FILE);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("生成扩展 " + extension.id() + " 的配置失败: " + e.getMessage());
        }
    }
}

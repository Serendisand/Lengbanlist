package org.leng.extension.lua;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.leng.Lengbanlist;
import org.leng.api.ApiVersion;
import org.leng.api.ExtensionContext;
import org.leng.api.ExtensionLoadException;
import org.leng.api.LengbanlistExtension;
import org.leng.api.SelfGatedExtension;
import org.leng.extension.ExtensionRegistry;
import org.leng.util.SchedulerUtils;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 一个 Lua 脚本扩展。
 *
 * <p>它只是 {@link LengbanlistExtension} 的又一个实现——命令注册、门控、命令表刷新
 * 全部复用既有机制，Lua 这边不另起一套。所以"Lua 命令与内置命令同一条命令树"
 * 是结构决定的，不是靠约定维持。
 *
 * <p><b>扩展 id 就是目录名</b>，脚本无法覆写：注册表在 {@code onEnable} 之前就要拿
 * {@code id()} 建索引，而脚本是 {@code onEnable} 里才跑起来的——允许覆写会让
 * 索引与自报的 id 对不上。
 *
 * <p><b>开关在扩展自己的 config.yml 里</b>（{@link SelfGatedExtension}），
 * 核心配置里不留它的键。所以"一个功能的开关只有一个来源"成立。
 */
public final class LuaExtension implements LengbanlistExtension, SelfGatedExtension {

    static final String ENTRY_FILE = "extension.lua";
    static final String CONFIG_FILE = "config.yml";

    /**
     * 脚本 API 版本。脚本用 {@code leng.api("1.0")} 声明它要求的范围，核心在加载期校验。
     *
     * <p>它与插件版本是两件事：插件每次修 bug 都发版，而脚本 API 只在
     * {@code leng} 表增删能力时才动，这样扩展作者不必跟着核心版本号跑。
     */
    public static final String API_VERSION = "1.0";

    private final Lengbanlist plugin;
    private final ExtensionRegistry registry;
    private final File directory;
    private final String id;

    private String displayName;
    private String version = "";
    private String requiredApi = "";
    private FileConfiguration config;
    private LuaSandbox sandbox;
    private LuaEventBridge events;
    private LuaValue onDisable;
    private final Map<Integer, SchedulerUtils.SchedulerTask> tasks = new LinkedHashMap<>();
    private int nextTaskHandle = 1;
    private boolean poisoned;
    private String disabledReason = "";
    /** 加载期标志：命令与事件订阅只能在脚本顶层注册，运行期注册会让命令表与事件表漂移。 */
    private boolean loading;

    LuaExtension(Lengbanlist plugin, ExtensionRegistry registry, File directory) {
        this.plugin = plugin;
        this.registry = registry;
        this.directory = directory;
        this.id = directory.getName();
        this.displayName = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return displayName;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public Set<String> features() {
        return Set.of(id);
    }

    @Override
    public boolean isFeatureEnabled(String feature) {
        return enabled();
    }

    @Override
    public void onEnable(ExtensionContext context) throws ExtensionLoadException {
        config = YamlConfiguration.loadConfiguration(configFile());
        sandbox = new LuaSandbox(line -> log("info", line));
        events = new LuaEventBridge(this);

        LuaCommandBridge commands = new LuaCommandBridge(this, context.commands());
        sandbox.globals().set("leng", new LuaFacade(this, commands, events).table());

        loading = true;
        try {
            runChunk(readEntry());
        } finally {
            loading = false;
        }
        // 版本校验放在脚本跑完之后：leng.api() 是脚本在顶层声明的，注册时核心还不知道它。
        // 不满足就抛出去，注册表会完整回滚这次注册。
        if (!ApiVersion.satisfies(requiredApi, API_VERSION)) {
            throw new ExtensionLoadException("脚本要求 API " + requiredApi
                    + "，当前核心提供的是 " + API_VERSION);
        }
        onDisable = sandbox.globals().get("on_disable");
        if (!onDisable.isfunction()) {
            onDisable = null;
        }
    }

    @Override
    public void onDisable() {
        if (onDisable != null) {
            runScript(onDisable, LuaValue.NONE, "on_disable");
            onDisable = null;
        }
        if (events != null) {
            events.unregister();
            events = null;
        }
        for (SchedulerUtils.SchedulerTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
        sandbox = null;
    }

    /** 命令与事件订阅的注册时机检查：加载期之外注册会让命令表/事件表与实际不符。 */
    void requireLoading(String what) {
        if (!loading) {
            throw new LuaError(what + " 只能在扩展加载期（脚本顶层）调用");
        }
    }

    /**
     * 扩展自己的开关（{@code extensions/<id>/config.yml} 的 {@code enabled}）。
     *
     * <p>它就是"这个功能开不开"的唯一来源，核心 {@code config.yml} 里没有对应的键。
     */
    public boolean enabled() {
        FileConfiguration current = config;
        return current == null || current.getBoolean("enabled", true);
    }

    File configFile() {
        return new File(directory, CONFIG_FILE);
    }

    void api(String range) {
        requiredApi = range == null ? "" : range.trim();
    }

    private String readEntry() throws ExtensionLoadException {
        File entry = new File(directory, ENTRY_FILE);
        if (!entry.isFile()) {
            throw new ExtensionLoadException("缺少入口脚本 " + ENTRY_FILE);
        }
        try {
            return Files.readString(entry.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ExtensionLoadException("读取 " + ENTRY_FILE + " 失败: " + e.getMessage());
        }
    }

    /**
     * 跑一遍顶层代码（加载期）。
     *
     * <p>这里必须把中断信号转成 {@link ExtensionLoadException}：{@code Aborted} 继承的是
     * {@code Error}，注册表那边只回滚 {@code Exception}，漏出去会一路掀掉插件启用。
     */
    private void runChunk(String source) throws ExtensionLoadException {
        LuaValue chunk;
        try {
            chunk = sandbox.globals().load(source, "@" + id + "/" + ENTRY_FILE);
        } catch (LuaError error) {
            throw new ExtensionLoadException("脚本语法错误: " + error.getMessage());
        }
        try {
            sandbox.run(chunk, LuaValue.NONE);
        } catch (LuaSandbox.Aborted aborted) {
            throw new ExtensionLoadException("脚本加载被中断: " + aborted.getMessage());
        } catch (LuaError error) {
            throw new ExtensionLoadException("脚本加载失败: " + error.getMessage());
        }
    }

    /**
     * 所有进入脚本的调用都走这里：重置预算、把异常挡在核心之外。
     *
     * <p>超时不是"这次调用失败"而是"这个扩展不能再留"——死循环脚本若只失败一次，
     * 下次调用还会卡住主线程。
     */
    Varargs runScript(LuaValue function, Varargs args, String what) {
        LuaSandbox current = sandbox;
        if (current == null) {
            return LuaValue.NONE;
        }
        try {
            return current.run(function, args);
        } catch (LuaSandbox.Aborted aborted) {
            poison(what + " " + aborted.getMessage());
        } catch (LuaError error) {
            log("error", what + " 出错: " + error.getMessage());
        } catch (Throwable t) {
            // 这里必须接 Throwable：深层非尾递归会抛 StackOverflowError，那是 Error，
            // 漏出去既不会记日志也不会隔离扩展，等于每次调用都崩一遍。
            log("error", what + " 出错: " + t);
        }
        return LuaValue.NONE;
    }

    private void poison(String reason) {
        log("error", "已停用本扩展 —— " + reason);
        if (poisoned) {
            return;
        }
        poisoned = true;
        disabledReason = reason;
        try {
            // 延后一拍再摘自己：中断可能发生在命令分派的中途，当场改注册表会打到正在遍历的表。
            SchedulerUtils.runTask(plugin, () -> registry.unregister(id));
        } catch (Exception e) {
            registry.unregister(id);
        }
    }

    /** 因超时/崩溃被强制停用的原因；正常运行返回空串。给 {@code /lban ext list} 用。 */
    public String disabledReason() {
        return poisoned ? disabledReason : "";
    }

    int scheduleTask(int delayTicks, int periodTicks, LuaValue body) {
        int handle = nextTaskHandle++;
        Runnable wrapper = () -> {
            try {
                runScript(body, LuaValue.NONE, "定时任务");
            } finally {
                // 一次性任务跑完就把句柄摘掉：留着的话每调度一次就攒一条，
                // 长跑的服务端会攒出一张只增不减的表。
                if (periodTicks <= 0) {
                    tasks.remove(handle);
                }
            }
        };
        SchedulerUtils.SchedulerTask task = periodTicks > 0
                ? SchedulerUtils.runTaskTimer(plugin, wrapper, Math.max(0, delayTicks), periodTicks)
                : SchedulerUtils.runTaskLater(plugin, wrapper, Math.max(0, delayTicks));
        tasks.put(handle, task);
        return handle;
    }

    void cancelTask(int handle) {
        SchedulerUtils.SchedulerTask task = tasks.remove(handle);
        if (task != null) {
            task.cancel();
        }
    }

    void meta(String key, String value) {
        switch (key) {
            case "name" -> displayName = value;
            case "version" -> version = value;
            default -> {
            }
        }
    }

    void log(String level, String message) {
        Logger logger = plugin.getLogger();
        String line = "[" + id + "] " + message;
        switch (level == null ? "" : level.toLowerCase(Locale.ROOT)) {
            case "warn", "warning" -> logger.warning(line);
            case "error", "severe" -> logger.severe(line);
            default -> logger.info(line);
        }
    }

    Lengbanlist plugin() {
        return plugin;
    }

    FileConfiguration config() {
        return config;
    }

    File directory() {
        return directory;
    }
}

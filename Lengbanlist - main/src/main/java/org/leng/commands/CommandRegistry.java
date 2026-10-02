package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandMap;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.PluginManager;
import org.leng.Lengbanlist;
import org.leng.api.CommandSpec;
import org.leng.extension.BuiltinExtensions;
import org.leng.extension.ExtensionRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommandRegistry {

    private final Lengbanlist plugin;
    private final ExtensionRegistry extensions;
    private final Map<String, FeatureCommand> registered = new LinkedHashMap<>();

    /** 扩展提供的子命令，键为子命令名（小写）。它们不进 Bukkit 命令表，由父命令分派。 */
    private final Map<String, Spec> subcommands = new LinkedHashMap<>();

    /** 独立使用时自建一个只含内置提供者的注册表（测试与单模块复用）。 */
    public CommandRegistry(Lengbanlist plugin) {
        this(plugin, builtinRegistry(plugin));
    }

    public CommandRegistry(Lengbanlist plugin, ExtensionRegistry extensions) {
        this.plugin = plugin;
        this.extensions = extensions;
    }

    private static ExtensionRegistry builtinRegistry(Lengbanlist plugin) {
        ExtensionRegistry registry = new ExtensionRegistry(plugin);
        BuiltinExtensions.registerAll(plugin, registry);
        return registry;
    }

    public ExtensionRegistry extensions() {
        return extensions;
    }

    public void refresh() {
        // 子命令不进命令表，因此必须在 CommandMap 判空之前收集——
        // 否则反射取不到 CommandMap 的服务器上，扩展子命令会静默失效。
        refreshSubcommands();
        CommandMap commandMap = commandMap();
        if (commandMap == null) {
            return;
        }
        boolean changed = false;
        for (Spec spec : specs()) {
            if (spec.isSubcommand()) {
                continue;   // 交给 /lban 分派，不占用独立的命令名
            }
            try {
                FeatureCommand existing = registered.get(spec.name());
                if (!extensions.isFeatureActive(spec.feature())) {
                    if (existing != null) {
                        unregister(commandMap, existing);
                        registered.remove(spec.name());
                        changed = true;
                        plugin.getLogger().fine("功能 " + spec.feature() + " 已禁用，/" + spec.name() + " 命令未注册。");
                    }
                    continue;
                }
                if (existing != null) {
                    continue;
                }
                FeatureCommand command = new FeatureCommand(plugin, extensions, spec.name(), spec.feature(), spec.permission(),
                        spec.description(), spec.usage(), new ArrayList<>(), spec.executor(), spec.tabCompleter());
                boolean bareName = commandMap.register(plugin.getName().toLowerCase(Locale.ROOT), command);
                registered.put(spec.name(), command);
                changed = true;
                if (bareName) {
                    plugin.getLogger().fine("已注册命令 /" + spec.name() + "(功能 " + spec.feature() + ")");
                } else {
                    plugin.getLogger().warning("命令名 /" + spec.name() + " 已被其他插件占用，本插件只能通过 /"
                            + plugin.getName().toLowerCase(Locale.ROOT) + ":" + spec.name() + " 调用。");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("刷新命令 " + spec.name() + " 时出错: " + t);
            }
        }
        if (changed) {
            syncClientCommands();
        }
    }

    public void unregisterAll() {
        subcommands.clear();
        CommandMap commandMap = commandMap();
        if (commandMap == null) {
            return;
        }
        for (FeatureCommand command : registered.values()) {
            unregister(commandMap, command);
        }
        registered.clear();
        syncClientCommands();
    }

    /** 父命令名：目前只有 /lban。 */
    public static final String SUBCOMMAND_PARENT = "lban";

    /**
     * 重新收集扩展提供的 {@code /lban} 子命令。
     *
     * <p>门控在这里做：功能未生效的子命令根本不会进表，于是 {@code /lban <它>}
     * 会落到"未知子命令"分支，与顶层命令未注册时的表现一致。
     */
    void refreshSubcommands() {
        subcommands.clear();
        for (Spec spec : specs()) {
            if (!spec.isSubcommand() || !spec.parent().equalsIgnoreCase(SUBCOMMAND_PARENT)) {
                continue;
            }
            if (!extensions.isFeatureActive(spec.feature())) {
                continue;
            }
            subcommands.put(spec.name().toLowerCase(Locale.ROOT), spec);
        }
    }

    /** 当前生效的扩展子命令名快照（小写）。 */
    public List<String> subcommandNames() {
        return new ArrayList<>(subcommands.keySet());
    }

    /**
     * 分派一条由扩展提供的子命令。
     *
     * <p><b>必须在核心自己的子命令分支全部落空之后才调用</b>：这样即使扩展注册了与
     * 内置同名的子命令，也不可能把它顶替掉。
     *
     * @param label 父命令的 label（如 {@code lban}），会拼成 {@code "lban <sub>"} 传给执行器
     * @return 是否已认领；{@code false} 表示没有扩展提供这个子命令
     */
    public boolean dispatchSubcommand(String parent, String sub, CommandSender sender,
                                      String label, String[] args) {
        if (parent == null || sub == null || !parent.equalsIgnoreCase(SUBCOMMAND_PARENT)) {
            return false;
        }
        Spec spec = subcommands.get(sub.toLowerCase(Locale.ROOT));
        if (spec == null) {
            return false;
        }
        if (!spec.permission().isEmpty() && !sender.hasPermission(spec.permission())) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        String[] tail = args != null && args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
        return spec.executor().onCommand(sender, null, label + " " + sub, tail);
    }

    private void unregister(CommandMap commandMap, Command command) {
        Map<String, Command> known = knownCommands(commandMap);
        if (known == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Command> entry : known.entrySet()) {
            if (entry.getValue() == command) {
                keys.add(entry.getKey());
            }
        }
        for (String key : keys) {
            try {
                known.remove(key);
                if (known.get(key) == command) {
                    plugin.getLogger().warning("命令 /" + key + " 注销后仍留在注册表中，该命令名可能无法让给其他插件。");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("注销命令 " + command.getName() + " 失败: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> knownCommands(CommandMap commandMap) {
        Field field = findField(commandMap.getClass(), "knownCommands");
        if (field == null) {
            plugin.getLogger().warning("无法读取 CommandMap 注册表，功能命令注销失败。");
            return null;
        }
        try {
            Object value = field.get(commandMap);
            return value instanceof Map ? (Map<String, Command>) value : null;
        } catch (Exception e) {
            plugin.getLogger().warning("读取 CommandMap 注册表时出错: " + e.getMessage());
            return null;
        }
    }

    private CommandMap commandMap() {
        if (Bukkit.getServer() == null) {
            // 只有测试环境（未初始化 Bukkit）会走到这里，生产里服务端一定非空。
            // 注意不能只对 getPluginManager() 的返回值判空：服务端为空时
            // getPluginManager() 自身就会抛 NPE，那样根本来不及判空。
            // 这里转成"取不到命令表"的既有路径。
            return null;
        }
        PluginManager pluginManager = Bukkit.getPluginManager();
        Field field = findField(pluginManager.getClass(), "commandMap");
        if (field == null) {
            plugin.getLogger().warning("无法获取 CommandMap，功能命令未注册。");
            return null;
        }
        try {
            Object value = field.get(pluginManager);
            if (value instanceof CommandMap) {
                return (CommandMap) value;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("获取 CommandMap 时出错: " + e.getMessage());
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private void syncClientCommands() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("syncCommands");
            method.invoke(Bukkit.getServer());
        } catch (Throwable t) {
            plugin.getLogger().fine("客户端命令树同步跳过: " + t.getClass().getSimpleName());
        }
    }

    record Spec(String feature, String name, String permission, String usage, String description,
                CommandExecutor executor, String parent) {

        TabCompleter tabCompleter() {
            return executor instanceof TabCompleter ? (TabCompleter) executor : null;
        }

        boolean isSubcommand() {
            return parent != null && !parent.isEmpty();
        }
    }

    /** 命令表由 {@link ExtensionRegistry} 提供；内置提供者由 BuiltinExtensions 注册。 */
    List<Spec> specs() {
        List<Spec> specs = new ArrayList<>();
        for (CommandSpec commandSpec : extensions.commandSpecs()) {
            specs.add(new Spec(commandSpec.feature(), commandSpec.name(), commandSpec.permission(),
                    commandSpec.usage(), commandSpec.description(), commandSpec.executor(),
                    commandSpec.parent()));
        }
        return specs;
    }

    /**
     * {@code /lban} 子命令的帮助过滤表。
     *
     * <p>已注册命令的 (usage → feature) 关系**不在这里**——它由
     * {@link BuiltinExtensions#declarations()} 派生。改造前本表手工重复了那 20 条命令条目，
     * 两处一旦漂移，{@code /lban help} 就会漏行或显示已关闭的功能，而测试也只能靠人工核对。
     * 现在只剩无法从注册命令推导的子命令。
     */
    private static final Map<String, String> SUBCOMMAND_FEATURES = new LinkedHashMap<>();

    static {
        SUBCOMMAND_FEATURES.put("/lban add", "ban");
        SUBCOMMAND_FEATURES.put("/lban remove", "unban");
        SUBCOMMAND_FEATURES.put("/lban list", "ban");
        SUBCOMMAND_FEATURES.put("/lban list-mute", "mute");
        SUBCOMMAND_FEATURES.put("/lban mute", "mute");
        SUBCOMMAND_FEATURES.put("/lban unmute", "mute");
        SUBCOMMAND_FEATURES.put("/lban warn", "warn");
        SUBCOMMAND_FEATURES.put("/lban unwarn", "unwarn");
        SUBCOMMAND_FEATURES.put("/lban vanish", "vanish");
        SUBCOMMAND_FEATURES.put("/lban freeze", "freeze");
        SUBCOMMAND_FEATURES.put("/lban unfreeze", "freeze");
        SUBCOMMAND_FEATURES.put("/lban check", "check");
        SUBCOMMAND_FEATURES.put("/lban history", "history");
        SUBCOMMAND_FEATURES.put("/lban getip", "getip");
        SUBCOMMAND_FEATURES.put("/lban audit export", "export");
        SUBCOMMAND_FEATURES.put("/lban audit", "audit");
        SUBCOMMAND_FEATURES.put("/lban alts", "alts");
        SUBCOMMAND_FEATURES.put("/lban sync", "sync");
        SUBCOMMAND_FEATURES.put("/lban rollback", "rollback");
        SUBCOMMAND_FEATURES.put("/lban model", "model");
        SUBCOMMAND_FEATURES.put("/lban open", "chest-ui");
        SUBCOMMAND_FEATURES.put("/lban info", "info");
        SUBCOMMAND_FEATURES.put("/lban handle", "report");
        SUBCOMMAND_FEATURES.put("/lban admin", "admin");
        SUBCOMMAND_FEATURES.put("/lban tp", "tp");
    }

    /**
     * 按用法串反查功能键：先匹配已注册命令（键为 {@code "/" + 命令名}，由声明表派生），
     * 再匹配 {@code /lban} 子命令手工表，取匹配到的最长键。
     */
    public static String featureForUsage(String usage) {
        if (usage == null || usage.isEmpty()) {
            return null;
        }
        String bestKey = null;
        String bestFeature = null;
        for (BuiltinExtensions.Declaration declaration : BuiltinExtensions.declarations()) {
            String key = "/" + declaration.name();
            if (matchesKey(usage, key) && (bestKey == null || key.length() > bestKey.length())) {
                bestKey = key;
                bestFeature = declaration.feature();
            }
        }
        for (Map.Entry<String, String> entry : SUBCOMMAND_FEATURES.entrySet()) {
            String key = entry.getKey();
            if (matchesKey(usage, key) && (bestKey == null || key.length() > bestKey.length())) {
                bestKey = key;
                bestFeature = entry.getValue();
            }
        }
        return bestFeature;
    }

    private static boolean matchesKey(String usage, String key) {
        return usage.equals(key) || usage.startsWith(key + " ");
    }
}

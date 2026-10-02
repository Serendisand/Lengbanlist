package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommandRegistry {

    private final Lengbanlist plugin;
    private final ExtensionRegistry extensions;
    private final Map<String, FeatureCommand> registered = new LinkedHashMap<>();

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
        CommandMap commandMap = commandMap();
        if (commandMap == null) {
            return;
        }
        boolean changed = false;
        for (Spec spec : specs()) {
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
                CommandExecutor executor) {

        TabCompleter tabCompleter() {
            return executor instanceof TabCompleter ? (TabCompleter) executor : null;
        }
    }

    /** 命令表由 {@link ExtensionRegistry} 提供；内置提供者由 BuiltinExtensions 注册。 */
    List<Spec> specs() {
        List<Spec> specs = new ArrayList<>();
        for (CommandSpec commandSpec : extensions.commandSpecs()) {
            specs.add(new Spec(commandSpec.feature(), commandSpec.name(), commandSpec.permission(),
                    commandSpec.usage(), commandSpec.description(), commandSpec.executor()));
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

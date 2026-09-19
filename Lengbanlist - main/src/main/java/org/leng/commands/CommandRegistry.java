package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.PluginManager;
import org.leng.Lengbanlist;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommandRegistry {

    private final Lengbanlist plugin;
    private final Map<String, FeatureCommand> registered = new LinkedHashMap<>();

    public CommandRegistry(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void refresh() {
        CommandMap commandMap = commandMap();
        if (commandMap == null) {
            return;
        }
        boolean changed = false;
        for (Spec spec : specs()) {
            FeatureCommand existing = registered.get(spec.name());
            if (!plugin.isFeatureEnabled(spec.feature())) {
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
            FeatureCommand command = new FeatureCommand(plugin, spec.name(), spec.feature(), spec.permission(),
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
        Map<?, ?> known = knownCommands(commandMap);
        if (known == null) {
            return;
        }
        known.entrySet().removeIf(entry -> entry.getValue() == command);
    }

    private Map<?, ?> knownCommands(CommandMap commandMap) {
        Field field = findField(commandMap.getClass(), "knownCommands");
        if (field == null) {
            plugin.getLogger().warning("无法读取 CommandMap 注册表，功能命令注销失败。");
            return null;
        }
        try {
            Object value = field.get(commandMap);
            return value instanceof Map ? (Map<?, ?>) value : null;
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

    private record Spec(String feature, String name, String permission, String usage, String description,
                        CommandExecutor executor) {

        TabCompleter tabCompleter() {
            return executor instanceof TabCompleter ? (TabCompleter) executor : null;
        }
    }

    private List<Spec> specs() {
        List<Spec> specs = new ArrayList<>();
        specs.add(new Spec("ban", "ban", "lengbanlist.ban",
                "/ban [-s] <玩家名> <时间/auto> <理由>", "封禁玩家", new BanCommand(plugin)));
        specs.add(new Spec("ban-ip", "ban-ip", "lengbanlist.banip",
                "/ban-ip [-s] <IP> <时间/auto> <理由>", "封禁 IP", new BanIpCommand(plugin)));
        specs.add(new Spec("unban", "unban", "lengbanlist.unban",
                "/unban <玩家名/IP>", "解封玩家", new UnbanCommand(plugin)));
        specs.add(new Spec("warn", "warn", "lengbanlist.warn",
                "/warn <玩家名> <理由>", "警告玩家", new WarnCommand(plugin)));
        specs.add(new Spec("unwarn", "unwarn", "lengbanlist.unwarn",
                "/unwarn <玩家名> [警告ID]", "移除警告", new UnwarnCommand(plugin)));
        specs.add(new Spec("check", "check", "lengbanlist.check",
                "/check <玩家名/IP>", "检查玩家或 IP 的封禁状态", new CheckCommand(plugin)));
        specs.add(new Spec("report", "report", "lengbanlist.report",
                "/report <玩家名> <理由>", "举报玩家", new ReportCommand(plugin)));
        specs.add(new Spec("admin", "admin", "lengbanlist.admin",
                "/admin <子命令>", "管理举报", new AdminReportCommand(plugin)));
        specs.add(new Spec("info", "info", "lengbanlist.info",
                "/info", "显示插件信息", new InfoCommand(plugin)));
        specs.add(new Spec("kick", "kick", "lengbanlist.kick",
                "/kick <玩家名> [理由]", "踢出玩家", new KickCommand(plugin)));
        specs.add(new Spec("chat-filter", "allowmsg", "lengbanlist.allowmsg",
                "/allowmsg <玩家名>", "允许玩家发送消息", new AllowMsgCommand(plugin)));
        specs.add(new Spec("warn", "warnmsg", "lengbanlist.warnmsg",
                "/warnmsg <玩家名>", "警告玩家发送违规消息", new WarnMsgCommand(plugin)));
        specs.add(new Spec("setban", "setban", "lengbanlist.setban",
                "/setban <玩家名/IP> <时间/forever/auto> <理由>", "设置玩家的封禁时间", new SetBanCommand(plugin)));
        specs.add(new Spec("history", "history", "lengbanlist.history",
                "/history <玩家名>", "查询玩家的处罚历史", new HistoryCommand(plugin)));
        specs.add(new Spec("mute", "mute", "lengbanlist.mute",
                "/mute [-s] <玩家名> <时间/auto> <原因>", "禁言玩家", new MuteCommand(plugin)));
        specs.add(new Spec("mute", "unmute", "lengbanlist.mute",
                "/unmute [-s] <玩家名>", "解除禁言", new UnmuteCommand(plugin)));
        specs.add(new Spec("mute", "listmute", "lengbanlist.listmute",
                "/listmute", "查看禁言列表", new ListMuteCommand(plugin)));
        specs.add(new Spec("getip", "getip", "lengbanlist.getip",
                "/getip [玩家名]", "查询玩家 IP 地理位置", new GetIPCommand(plugin)));
        specs.add(new Spec("staffchat", "sc", "lengbanlist.staffchat",
                "/sc <内容>", "工作频道聊天", new StaffChatCommand(plugin)));
        specs.add(new Spec("alts", "alts", "lengbanlist.alts",
                "/alts <玩家名>", "查询玩家同IP小号", plugin.getAltsCommand()));
        return specs;
    }

    private static final Map<String, String> HELP_FEATURES = new LinkedHashMap<>();

    static {
        HELP_FEATURES.put("/ban", "ban");
        HELP_FEATURES.put("/ban-ip", "ban-ip");
        HELP_FEATURES.put("/unban", "unban");
        HELP_FEATURES.put("/warn", "warn");
        HELP_FEATURES.put("/unwarn", "unwarn");
        HELP_FEATURES.put("/kick", "kick");
        HELP_FEATURES.put("/mute", "mute");
        HELP_FEATURES.put("/unmute", "mute");
        HELP_FEATURES.put("/listmute", "mute");
        HELP_FEATURES.put("/check", "check");
        HELP_FEATURES.put("/history", "history");
        HELP_FEATURES.put("/report", "report");
        HELP_FEATURES.put("/admin", "admin");
        HELP_FEATURES.put("/info", "info");
        HELP_FEATURES.put("/getip", "getip");
        HELP_FEATURES.put("/alts", "alts");
        HELP_FEATURES.put("/setban", "setban");
        HELP_FEATURES.put("/allowmsg", "chat-filter");
        HELP_FEATURES.put("/warnmsg", "warn");
        HELP_FEATURES.put("/sc", "staffchat");
        HELP_FEATURES.put("/lban add", "ban");
        HELP_FEATURES.put("/lban remove", "unban");
        HELP_FEATURES.put("/lban list", "ban");
        HELP_FEATURES.put("/lban list-mute", "mute");
        HELP_FEATURES.put("/lban mute", "mute");
        HELP_FEATURES.put("/lban unmute", "mute");
        HELP_FEATURES.put("/lban warn", "warn");
        HELP_FEATURES.put("/lban unwarn", "unwarn");
        HELP_FEATURES.put("/lban vanish", "vanish");
        HELP_FEATURES.put("/lban freeze", "freeze");
        HELP_FEATURES.put("/lban unfreeze", "freeze");
        HELP_FEATURES.put("/lban check", "check");
        HELP_FEATURES.put("/lban history", "history");
        HELP_FEATURES.put("/lban getip", "getip");
        HELP_FEATURES.put("/lban audit export", "export");
        HELP_FEATURES.put("/lban audit", "audit");
        HELP_FEATURES.put("/lban alts", "alts");
        HELP_FEATURES.put("/lban sync", "sync");
        HELP_FEATURES.put("/lban rollback", "rollback");
        HELP_FEATURES.put("/lban model", "model");
        HELP_FEATURES.put("/lban open", "chest-ui");
        HELP_FEATURES.put("/lban info", "info");
        HELP_FEATURES.put("/lban handle", "report");
        HELP_FEATURES.put("/lban admin", "admin");
        HELP_FEATURES.put("/lban tp", "tp");
    }

    public static String featureForUsage(String usage) {
        if (usage == null || usage.isEmpty()) {
            return null;
        }
        String matched = null;
        for (String key : HELP_FEATURES.keySet()) {
            if (!usage.equals(key) && !usage.startsWith(key + " ")) {
                continue;
            }
            if (matched == null || key.length() > matched.length()) {
                matched = key;
            }
        }
        return matched == null ? null : HELP_FEATURES.get(matched);
    }
}

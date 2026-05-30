package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.manager.WarnManager;
import org.leng.utils.Utils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class WarnCommand extends Command implements CommandExecutor, TabCompleter {
    private final Lengbanlist plugin;

    public WarnCommand(Lengbanlist plugin) {
        super("warn");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        if (!plugin.isFeatureEnabled("warn")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        // 检查权限
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!sender.isOp() && !player.hasPermission("lengbanlist.warn")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return false;
            }
        }

        // 检查参数长度
        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法错误: /lban warn <玩家名/IP> <原因>");
            return false;
        }

        String target = args[0];
        String rawReason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String reason = resolvePresetReason(rawReason);
        WarnManager warnManager = plugin.getWarnManager();

        // 检查是否是 IP
        boolean isIp = target.contains(".");

        // 检查是否是 IP 地址
        if (isIp) {
            if (!plugin.getBanManager().isValidIp(target)) {
                Utils.sendMessage(sender, plugin.prefix() + "§c无效的IP地址");
                return false;
            }
            // IP警告逻辑
            warnManager.warnPlayer(target, sender.getName(), reason);
            Utils.sendMessage(sender, ModelManager.getInstance().getCurrentModel().addWarn(target, reason));
            return true;
        }

        // 玩家警告逻辑 - 允许超过3次警告，警告将在1天后自动过期
        warnManager.warnPlayer(target, sender.getName(), reason);
        Utils.sendMessage(sender, ModelManager.getInstance().getCurrentModel().addWarn(target, reason));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            List<String> presets = new ArrayList<>();
            if (plugin.getConfig().isConfigurationSection("preset-reasons")) {
                presets.addAll(plugin.getConfig().getConfigurationSection("preset-reasons").getKeys(false));
            }
            List<String> completions = new ArrayList<>();
            for (String key : presets) {
                if (key.toLowerCase().startsWith(prefix)) completions.add(key);
            }
            return completions;
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }

    private String resolvePresetReason(String input) {
        if (input == null || !plugin.getConfig().isConfigurationSection("preset-reasons")) return input;
        String value = plugin.getConfig().getString("preset-reasons." + input.toLowerCase());
        return value != null ? value : input;
    }
}
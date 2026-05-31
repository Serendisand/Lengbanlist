package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.object.MuteEntry;
import org.leng.utils.Utils;

import java.util.Arrays;

public class MuteCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public MuteCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("mute")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        if (!sender.hasPermission("lengbanlist.mute")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }
        if (args.length < 3) {
            sendUsage(sender, label);
            return true;
        }
        String target = args[0];
        String timeArg = args[1];
        String rawReason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String reason = resolvePresetReason(rawReason);
        long duration;
        if (timeArg.equalsIgnoreCase("auto")) {
            duration = calculateAutoMuteTime(target);
        } else {
            duration = org.leng.utils.TimeUtils.parseDurationToMillis(timeArg);
            if (duration <= 0) {
                showTimeFormatError(sender);
                return true;
            }
        }
        MuteEntry entry = new MuteEntry(target, sender.getName(), org.leng.utils.TimeUtils.calculateEndTime(duration), reason);
        plugin.getMuteManager().mutePlayer(entry);
        Bukkit.broadcastMessage(ModelManager.getInstance().getCurrentModel().addMute(target, reason));
        return true;
    }

    private long calculateAutoMuteTime(String playerName) {
        int warnCount = Math.max(0, plugin.getWarnManager().getActiveWarnings(playerName).size());
        switch (warnCount) {
            case 0:  return org.leng.utils.TimeUtils.daysToMillis(1);
            case 1:  return org.leng.utils.TimeUtils.daysToMillis(3);
            case 2:  return org.leng.utils.TimeUtils.daysToMillis(7);
            case 3:  return org.leng.utils.TimeUtils.daysToMillis(14);
            case 4:  return org.leng.utils.TimeUtils.daysToMillis(30);
            default: return Long.MAX_VALUE;
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /" + label + " <玩家> <时间/auto> <原因>");
        Utils.sendMessage(sender, plugin.prefix() + "§c时间单位喵: s(秒), m(分), h(时), d(天), w(周), M(月), y(年)");
        Utils.sendMessage(sender, plugin.prefix() + "§c使用 auto 会根据警告次数自动计算禁言时间喵");
    }

    private void showTimeFormatError(CommandSender sender) {
        Utils.sendMessage(sender, plugin.prefix() + "§c时间格式错误喵，请使用以下格式:");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 10s: 秒 (10 秒)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 5m: 分钟 (5 分钟)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 2h: 小时 (2 小时)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 7d: 天 (7 天)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 1w: 周 (1 周，等于 7 天)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 1M: 月 (1 月，按 30 天计算)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - 1y: 年 (1 年，按 365 天计算)");
        Utils.sendMessage(sender, plugin.prefix() + "§c - forever: 永久禁言");
        Utils.sendMessage(sender, plugin.prefix() + "§c - auto: 自动计算禁言时间");
    }

    private String resolvePresetReason(String input) {
        if (input == null || !plugin.getConfig().isConfigurationSection("preset-reasons")) return input;
        String value = plugin.getConfig().getString("preset-reasons." + input.toLowerCase());
        return value != null ? value : input;
    }
}

package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.object.MuteEntry;
import org.leng.utils.IpMatcher;
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
        boolean silent = false;
        if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
            silent = true;
            args = Arrays.copyOfRange(args, 1, args.length);
        }

        if (args.length < 3) {
            sendUsage(sender, label);
            return true;
        }
        String target = args[0];
        String timeArg = args[1];
        String rawReason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        String reason = resolvePresetReason(rawReason);
        if (IpMatcher.normalizeIpOrCidr(target) == null && !plugin.getImmunityManager().canPunish(sender, target)) {
            Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(target));
            return true;
        }
        String normalized = IpMatcher.normalizeIpOrCidr(target);
        if (normalized != null) target = normalized;
        long duration;
        if (timeArg.equalsIgnoreCase("auto")) {
            duration = plugin.getEscalationManager().resolveMute(target);
        } else {
            duration = org.leng.utils.TimeUtils.parseDurationToMillis(timeArg);
            if (duration <= 0) {
                showTimeFormatError(sender);
                return true;
            }
        }
        MuteEntry entry = new MuteEntry(target, sender.getName(), org.leng.utils.TimeUtils.calculateEndTime(duration), reason);
        Long newDuration = plugin.getMuteManager().mutePlayer(entry);
        if (newDuration == null) {
            Utils.sendMessage(sender, plugin.prefix() + "§e该目标已有相同时长的禁言记录，未重复禁言。");
            return true;
        }
        String muteMessage = currentModel().addMute(target, reason);
        if (silent) {
            Utils.sendMessage(sender, muteMessage);
        } else {
            Utils.broadcast(muteMessage);
        }
        return true;
    }

    private org.leng.models.Model currentModel() {
        return ModelManager.getInstance().getCurrentModel();
    }

    private void sendUsage(CommandSender sender, String label) {
        Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /" + label + " <玩家> <时间/auto> <原因>");
        Utils.sendMessage(sender, plugin.prefix() + "§c时间单位喵: s(秒), m(分), h(时), d(天), w(周), M(月), y(年)");
        Utils.sendMessage(sender, plugin.prefix() + "§c使用 auto 会根据警告次数自动计算禁言时间喵");
        Utils.sendMessage(sender, plugin.prefix() + "§7在第一位加上 -s 可静默执行（不向全服广播）喵");
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

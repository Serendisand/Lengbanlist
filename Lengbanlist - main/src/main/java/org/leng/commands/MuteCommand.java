package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.object.MuteEntry;
import org.leng.utils.Utils;

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
            Utils.sendMessage(sender, plugin.prefix() + "§c用法: /" + label + " <玩家名> <时间/forever> <原因>");
            return true;
        }
        long duration = org.leng.utils.TimeUtils.parseDurationToMillis(args[1]);
        if (duration <= 0) {
            Utils.sendMessage(sender, plugin.prefix() + "§c时间格式错误，请使用 10s, 5m, 2h, 7d, 1w, 1M, 1y 或 forever。");
            return true;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        MuteEntry entry = new MuteEntry(args[0], sender.getName(), org.leng.utils.TimeUtils.calculateEndTime(duration), reason);
        plugin.getMuteManager().mutePlayer(entry);
        Bukkit.broadcastMessage(ModelManager.getInstance().getCurrentModel().addMute(args[0], reason));
        return true;
    }
}

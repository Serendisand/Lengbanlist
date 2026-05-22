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
        if (args.length < 2) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法: /" + label + " <玩家名> <原因>");
            return true;
        }
        MuteEntry entry = new MuteEntry(args[0], sender.getName(), System.currentTimeMillis(), args[1]);
        plugin.getMuteManager().mutePlayer(entry);
        Bukkit.broadcastMessage(ModelManager.getInstance().getCurrentModel().addMute(args[0], args[1]));
        return true;
    }
}

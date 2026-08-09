package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.utils.Utils;

import java.util.Arrays;

public class UnmuteCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public UnmuteCommand(Lengbanlist plugin) {
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
        if (args.length < 1) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /" + label + " <玩家名>");
            return true;
        }
        plugin.getMuteManager().unmutePlayer(args[0], sender.getName());
        String message = ModelManager.getInstance().getCurrentModel().removeMute(args[0]);
        if (silent) {
            Utils.sendMessage(sender, message);
        } else {
            Utils.broadcast(message);
        }
        return true;
    }
}

package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

public class StaffChatCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public StaffChatCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("staffchat")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        if (!sender.hasPermission("lengbanlist.staffchat")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }

        if (args.length < 1) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法: /sc <内容>");
            return true;
        }

        String message = String.join(" ", args);
        String senderName = sender instanceof Player ? sender.getName() : "§cConsole";

        String staffChatFormat = "§b<STAFF> §e" + senderName + "§f: " + message;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("lengbanlist.staffchat")) {
                Utils.sendMessage(player, staffChatFormat);
            }
        }
        Bukkit.getConsoleSender().sendMessage(staffChatFormat);

        return true;
    }
}

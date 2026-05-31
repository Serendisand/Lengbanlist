package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

public class UnbanCommand extends Command implements CommandExecutor {
    private final Lengbanlist plugin;

    public UnbanCommand(Lengbanlist plugin) {
        super("unban");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String s, String[] args) {
        if (!plugin.isFeatureEnabled("unban")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }


        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!sender.isOp() || !(player.hasPermission("lengbanlist.unban"))) {
                Utils.sendMessage(sender, "§c你没有权限使用此命令。");
                return false;
            }
        }


        if (args.length < 1) {
            Utils.sendMessage(sender, "§c用法错误喵: /unban <玩家名/IP>");
            return false;
        }


        if (args[0].contains(".")) {

            if (Lengbanlist.getInstance().banManager.isIpBanned(args[0])) {
                Lengbanlist.getInstance().banManager.unbanIp(args[0]);
            } else {
                Utils.sendMessage(sender, "§cIP " + args[0] + " 未被封禁或封禁已过期");
            }
        } else {

            if (Lengbanlist.getInstance().banManager.isPlayerBanned(args[0])) {
                Lengbanlist.getInstance().banManager.unbanPlayer(args[0]);
            } else {
                Utils.sendMessage(sender, "§c玩家 " + args[0] + " 未被封禁或封禁已过期");
            }
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }
}

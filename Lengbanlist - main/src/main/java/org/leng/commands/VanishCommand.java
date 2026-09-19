package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.VanishManager;
import org.leng.models.Model;
import org.leng.utils.Utils;

import java.util.Collections;
import java.util.List;

public class VanishCommand implements CommandExecutor, TabCompleter {

    private final Lengbanlist plugin;

    public VanishCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("vanish")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        if (!sender.hasPermission(VanishManager.PERMISSION_USE)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
            return true;
        }
        if (!(sender instanceof Player)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c此命令只能由玩家执行。");
            return true;
        }

        Player player = (Player) sender;
        boolean vanished = plugin.getVanishManager().toggle(player);
        Model model = plugin.getModelManager().getCurrentModel();
        if (model != null) {
            Utils.sendMessage(sender, plugin.prefix() + model.onVanish(vanished));
        }
        plugin.getAuditManager().log(vanished ? "开启隐身" : "关闭隐身", Utils.getSenderName(sender), player.getName(), "");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}

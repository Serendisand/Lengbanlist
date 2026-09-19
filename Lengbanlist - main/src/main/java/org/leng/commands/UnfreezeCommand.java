package org.leng.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.object.FreezeEntry;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class UnfreezeCommand implements CommandExecutor, TabCompleter {

    private final Lengbanlist plugin;

    public UnfreezeCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("freeze")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        if (!sender.hasPermission(FreezeCommand.PERMISSION)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
            return true;
        }
        if (args.length == 0) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /lban unfreeze <玩家名|all>");
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            int unfrozen = plugin.getFreezeManager().unfreezeAll();
            if (unfrozen == 0) {
                Utils.sendMessage(sender, plugin.prefix() + "§7当前没有处于冻结状态的玩家。");
                return true;
            }
            Utils.sendMessage(sender, plugin.prefix() + "§a已解冻全部 " + unfrozen + " 名玩家。");
            plugin.getAuditManager().log("解冻全部玩家", Utils.getSenderName(sender), "", String.valueOf(unfrozen));
            return true;
        }

        FreezeEntry entry = plugin.getFreezeManager().getByName(args[0]);
        if (entry == null) {
            Utils.sendMessage(sender, plugin.prefix() + "§c玩家 §f" + args[0] + " §c当前没有被冻结。");
            return true;
        }
        if (!plugin.getFreezeManager().unfreeze(entry)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c解冻失败，请查看控制台日志。");
            return true;
        }

        Model model = plugin.getModelManager().getCurrentModel();
        if (model != null) {
            Utils.sendMessage(sender, plugin.prefix() + model.onUnfreeze(entry.player()));
        }
        plugin.getAuditManager().log("解除冻结", Utils.getSenderName(sender), entry.player(), entry.reason());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length != 1) {
            return completions;
        }
        String prefix = args[0].toLowerCase();
        if ("all".startsWith(prefix)) {
            completions.add("all");
        }
        for (FreezeEntry entry : plugin.getFreezeManager().all()) {
            if (entry.player().toLowerCase().startsWith(prefix)) {
                completions.add(entry.player());
            }
        }
        return completions;
    }
}

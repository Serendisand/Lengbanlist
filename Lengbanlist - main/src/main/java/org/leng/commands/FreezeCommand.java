package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.object.FreezeEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class FreezeCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "lengbanlist.freeze";

    private final Lengbanlist plugin;

    public FreezeCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("freeze")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
            return true;
        }
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("list"))) {
            showFrozenList(sender);
            return true;
        }

        Model model = plugin.getModelManager().getCurrentModel();
        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim() : "";
        if (reason.isEmpty()) {
            Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式：/lban freeze <玩家名> <理由>");
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            Utils.sendMessage(sender, plugin.prefix() + "§c玩家 §f" + targetName + " §c不在线，无法冻结。");
            return true;
        }
        if (!plugin.getImmunityManager().canPunish(sender, target.getName())) {
            Utils.sendMessage(sender, model == null
                    ? plugin.prefix() + "§c目标权限等级不低于你，无法冻结。"
                    : model.getImmunityDenied(target.getName()));
            return true;
        }
        if (!plugin.getFreezeManager().freeze(target, Utils.getSenderName(sender), reason)) {
            Utils.sendMessage(sender, plugin.prefix() + "§e玩家 §f" + target.getName()
                    + " §e已处于冻结状态，输入 §f/lban unfreeze " + target.getName() + " §e可解冻。");
            return true;
        }

        plugin.getFreezeManager().notify(target, reason);
        if (model != null) {
            Utils.sendMessage(sender, plugin.prefix() + model.onFreeze(target.getName(), reason));
        }
        plugin.getAuditManager().log("冻结玩家", Utils.getSenderName(sender), target.getName(), reason);
        return true;
    }

    public void showFrozenList(CommandSender sender) {
        Utils.sendMessage(sender, "§7--§bLengbanlist 冻结名单§7--");
        Collection<FreezeEntry> frozen = plugin.getFreezeManager().all();
        if (frozen.isEmpty()) {
            Utils.sendMessage(sender, "§7当前没有处于冻结状态的玩家。");
        }
        for (FreezeEntry entry : frozen) {
            Utils.sendMessage(sender, "§c被冻结者：§f" + entry.player() + " §e处理人：§f" + entry.staff()
                    + " §e理由：§f" + entry.reason() + " §f冻结时间：" + TimeUtils.timestampToReadable(entry.time()));
        }
        Utils.sendMessage(sender, "§7用法: §f/lban freeze <玩家名> <理由> §7| §f/lban unfreeze <玩家名|all>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length != 1) {
            return completions;
        }
        String prefix = args[0].toLowerCase();
        if ("list".startsWith(prefix)) {
            completions.add("list");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(prefix)) {
                completions.add(player.getName());
            }
        }
        return completions;
    }
}

package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.ReportManager;
import org.leng.object.ReportEntry;
import org.leng.utils.Utils;

import java.util.List;

public class AdminReportCommand implements CommandExecutor {
    private final Lengbanlist plugin;

    public AdminReportCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("admin")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }

        if (!sender.hasPermission("lengbanlist.admin")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }

        if (!(sender instanceof Player)) {
            Utils.sendMessage(sender, plugin.prefix() + "§c此命令只能由玩家执行。");
            return true;
        }

        Player player = (Player) sender;
        showAdminReportUI(player);
        return true;
    }

    private void showAdminReportUI(Player player) {
        ReportManager reportManager = plugin.getReportManager();

        int pendingReports = reportManager.getPendingReportCount();
        int onlineAdmins = (int) Bukkit.getOnlinePlayers().stream().filter(p -> p.isOp()).count();

        Utils.sendMessage(player, plugin.prefix() + "§bLengbanlist Report Admin");
        Utils.sendMessage(player, plugin.prefix() + "§e当前待处理举报数：§c" + pendingReports);
        Utils.sendMessage(player, plugin.prefix() + "§e当前在线管理员：§c" + onlineAdmins);

        List<ReportEntry> reports = reportManager.getPendingReports();
        if (reports.isEmpty()) {
            Utils.sendMessage(player, plugin.prefix() + "§a暂无待处理的举报！");
            return;
        }

        for (ReportEntry report : reports) {
            Player targetPlayer = Bukkit.getPlayer(report.getTarget());
            Player reporterPlayer = Bukkit.getPlayer(report.getReporter());

            String targetLoc = "";
            net.md_5.bungee.api.chat.BaseComponent targetComponent;
            if (targetPlayer != null) {
                targetLoc = " §7(世界:" + targetPlayer.getWorld().getName() + " X:" + (int)targetPlayer.getLocation().getX() + " Y:" + (int)targetPlayer.getLocation().getY() + " Z:" + (int)targetPlayer.getLocation().getZ() + ")";
                targetComponent = Utils.clickableText("§c" + report.getTarget() + targetLoc, "/lban tp " + report.getTarget());
            } else {
                targetComponent = new net.md_5.bungee.api.chat.TextComponent("§7" + report.getTarget());
            }

            String reporterLoc = "";
            net.md_5.bungee.api.chat.BaseComponent reporterComponent;
            if (reporterPlayer != null) {
                reporterLoc = " §7(世界:" + reporterPlayer.getWorld().getName() + " X:" + (int)reporterPlayer.getLocation().getX() + " Y:" + (int)reporterPlayer.getLocation().getY() + " Z:" + (int)reporterPlayer.getLocation().getZ() + ")";
                reporterComponent = Utils.clickableText("§e" + report.getReporter() + reporterLoc, "/lban tp " + report.getReporter());
            } else {
                reporterComponent = new net.md_5.bungee.api.chat.TextComponent("§7" + report.getReporter());
            }

            String status = report.getStatus() == null ? "" : "§a【当前状态：" + report.getStatus() + "】";
            Utils.sendMessage(player, plugin.prefix() + "§7————————————————");
            Utils.sendMessage(player, plugin.prefix() + "§e举报编号：§f" + report.getId() + " " + status);
            Utils.sendMessage(player, plugin.prefix() + "§e举报原因：§f" + report.getReason());

            player.spigot().sendMessage(
                new net.md_5.bungee.api.chat.TextComponent(plugin.prefix() + "§e被举报人："),
                targetComponent,
                new net.md_5.bungee.api.chat.TextComponent(" §e举报人："),
                reporterComponent
            );

            player.spigot().sendMessage(
                new net.md_5.bungee.api.chat.TextComponent(plugin.prefix() + " "),
                Utils.clickableText("§a【点击受理】", "/report accept " + report.getId()),
                new net.md_5.bungee.api.chat.TextComponent(" "),
                Utils.clickableText("§b【点击关闭】", "/report close " + report.getId()),
                new net.md_5.bungee.api.chat.TextComponent(" "),
                Utils.clickableText("§c【点击封禁】", "/ban " + report.getTarget() + " auto unfair advantage")
            );
            Utils.sendMessage(player, plugin.prefix() + "§7————————————————");
        }
    }
}

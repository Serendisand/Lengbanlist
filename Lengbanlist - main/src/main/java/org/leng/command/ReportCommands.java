package org.leng.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.ReportEntry;
import org.leng.service.ReportManager;
import org.leng.util.Utils;

import java.util.List;

public final class ReportCommands {

    private ReportCommands() {}

    public static final class Report implements CommandExecutor {
        private final Lengbanlist plugin;

        public Report(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                Utils.sendMessage(sender, plugin.prefix() + "§c此命令只能由玩家执行。");
                return true;
            }
            Player player = (Player) sender;

            if (args.length < 1) {
                if (!plugin.isFeatureEnabled("report")) {
                    plugin.sendFeatureDisabled(player);
                    return true;
                }
                if (!player.hasPermission("lengbanlist.report")) {
                    Utils.sendMessage(player, plugin.prefix() + "§c你没有权限使用此命令。");
                    return true;
                }
                if (plugin.isFeatureActive("chest-ui")) {
                    plugin.getReportSubmitGui().openPlayerList(player);
                } else {
                    Utils.sendMessage(player, plugin.prefix() + "§c用法错误喵: /report <玩家名> <原因>");
                }
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "accept":
                    if (!plugin.isFeatureEnabled("admin")) {
                        plugin.sendFeatureDisabled(player);
                        return true;
                    }
                    if (!player.hasPermission("lengbanlist.admin")) {
                        Utils.sendMessage(player, plugin.prefix() + "§c你没有权限处理举报。");
                        return true;
                    }
                    if (args.length < 2) {
                        Utils.sendMessage(player, plugin.prefix() + "§c用法错误喵: /report accept <举报编号>");
                        return true;
                    }
                    handleAccept(player, args[1]);
                    break;
                case "close":
                    if (!plugin.isFeatureEnabled("admin")) {
                        plugin.sendFeatureDisabled(player);
                        return true;
                    }
                    if (!player.hasPermission("lengbanlist.admin")) {
                        Utils.sendMessage(player, plugin.prefix() + "§c你没有权限处理举报。");
                        return true;
                    }
                    if (args.length < 2) {
                        Utils.sendMessage(player, plugin.prefix() + "§c用法错误喵: /report close <举报编号>");
                        return true;
                    }
                    handleClose(player, args[1]);
                    break;
                case "ack":
                    if (!plugin.isFeatureEnabled("report")) {
                        plugin.sendFeatureDisabled(player);
                        return true;
                    }
                    if (!player.hasPermission("lengbanlist.report")) {
                        Utils.sendMessage(player, plugin.prefix() + "§c你没有权限使用此命令。");
                        return true;
                    }
                    if (args.length < 2) {
                        return true;
                    }
                    handleAck(player, args[1]);
                    break;
                default:
                    if (!plugin.isFeatureEnabled("report")) {
                        plugin.sendFeatureDisabled(player);
                        return true;
                    }
                    if (!player.hasPermission("lengbanlist.report")) {
                        Utils.sendMessage(player, plugin.prefix() + "§c你没有权限使用此命令。");
                        return true;
                    }
                    String target = args[0];
                    if (args.length < 2) {
                        plugin.getWizardManager().startReportReason(player, target);
                        return true;
                    }
                    String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                    handleReportSubmit(player, target, reason);
                    break;
            }
            return true;
        }

        private void handleAccept(Player player, String reportId) {
            ReportManager manager = plugin.getReportManager();
            ReportManager.ActionResult result = manager.acceptReport(reportId, player.getName());
            manager.sendAcceptFeedback(player, result, reportId);
        }

        private void handleClose(Player player, String reportId) {
            ReportManager manager = plugin.getReportManager();
            ReportManager.ActionResult result = manager.closeReport(reportId, player.getName());
            manager.sendCloseFeedback(player, result, reportId);
        }

        private void handleAck(Player player, String reportId) {
            ReportEntry report = plugin.getReportManager().getReport(reportId);
            if (report == null) return;
            if (!report.getReporter().equalsIgnoreCase(player.getName())) {
                Utils.sendMessage(player, plugin.prefix() + "§c你只能确认自己提交的举报。");
                return;
            }
            if (ReportEntry.isFinalized(report.getStatus())) {
                Utils.sendMessage(player, plugin.prefix() + "§c该举报已结束，无需标记已读。");
                return;
            }
            report = report.withStatus(ReportEntry.STATUS_READ);
            if (!plugin.getReportManager().updateReport(report)) {
                Utils.sendMessage(player, plugin.prefix() + "§c操作失败，请稍后再试。");
                return;
            }
            plugin.getAuditManager().log("已读举报", player.getName(), report.getTarget(), "编号: " + reportId);
            Utils.sendMessage(player, plugin.prefix() + "§a已标记举报 " + reportId + " 为已读。");
        }

        private void handleReportSubmit(Player reporter, String target, String reason) {
            ReportManager manager = plugin.getReportManager();
            ReportManager.SubmitOutcome outcome = manager.submitReport(reporter, target, reason);
            manager.sendSubmitFeedback(reporter, outcome);
        }
    }

    public static final class AdminReport implements CommandExecutor {
        private final Lengbanlist plugin;

        public AdminReport(Lengbanlist plugin) {
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
            if (plugin.isFeatureActive("chest-ui")) {
                plugin.getReportManageGui().openList(player);
            } else {
                showAdminReportUI(player);
            }
            return true;
        }

        private void showAdminReportUI(Player player) {
            ReportManager reportManager = plugin.getReportManager();

            int pendingReports = reportManager.getPendingReportCount();
            int onlineAdmins = (int) Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("lengbanlist.admin")).count();

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
                    Utils.clickableText("§c【点击封禁】", "/lban handle " + report.getId() + " auto")
                );
                Utils.sendMessage(player, plugin.prefix() + "§7————————————————");
            }
        }
    }
}

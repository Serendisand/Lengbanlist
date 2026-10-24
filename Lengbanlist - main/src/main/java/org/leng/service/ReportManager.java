package org.leng.service;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.api.events.LengbanlistReportEvent;
import org.leng.object.BanEntry;
import org.leng.object.ReportEntry;
import org.leng.storage.DatabaseManager;
import org.leng.util.SchedulerUtils;
import org.leng.util.TimeUtils;
import org.leng.util.Utils;

import java.util.List;
import java.util.UUID;

public class ReportManager {

    public enum SubmitResult {
        OK, COOLDOWN, SELF, INVALID, DB_ERROR
    }

    public record SubmitOutcome(SubmitResult result, ReportEntry report, long remainingMillis) {
    }

    public enum ActionResult {
        OK, NOT_FOUND, FINALIZED, ALREADY_ACCEPTED, DB_ERROR
    }

    private final Lengbanlist plugin;

    public ReportManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public boolean addReport(ReportEntry report) {
        boolean saved = updateReport(report);
        if (saved) {
            Bukkit.getPluginManager().callEvent(new LengbanlistReportEvent(report));
        }
        return saved;
    }

    public boolean updateReport(ReportEntry report) {
        return plugin.getDatabaseManager().upsertReport(report);
    }

    public void removeReport(String id) {
        plugin.getDatabaseManager().deleteReport(id);
    }

    public int getReportCount(String target) {
        return plugin.getDatabaseManager().getReportCount(target);
    }

    public ReportEntry getReport(String id) {
        return plugin.getDatabaseManager().getReport(id);
    }

    public List<ReportEntry> getReportsByReporterAndTarget(String reporter, String target) {
        return plugin.getDatabaseManager().getReportsByReporterAndTarget(reporter, target);
    }

    public List<ReportEntry> getPendingReports() {
        return plugin.getDatabaseManager().getPendingReports();
    }

    public List<ReportEntry> getReportsByReporterWithStatus(String reporter, String status) {
        return plugin.getDatabaseManager().getReportsByReporterWithStatus(reporter, status);
    }

    public int getPendingReportCount() {
        return plugin.getDatabaseManager().getPendingReportCount();
    }

    /**
     * 提交举报。冷却、自己举报自己、空内容都在这里统一判断，
     * 命令、GUI 和聊天向导共用同一条路径。
     */
    public SubmitOutcome submitReport(Player reporter, String target, String reason) {
        if (reporter == null) {
            return new SubmitOutcome(SubmitResult.INVALID, null, 0L);
        }
        String safeTarget = target == null ? "" : target.trim();
        String safeReason = reason == null ? "" : reason.trim();
        if (safeTarget.isEmpty() || safeReason.isEmpty()) {
            return new SubmitOutcome(SubmitResult.INVALID, null, 0L);
        }
        if (safeTarget.equalsIgnoreCase(reporter.getName())) {
            return new SubmitOutcome(SubmitResult.SELF, null, 0L);
        }

        int cooldownSeconds = reportCooldownSeconds();
        long cooldownMillis = Math.max(0L, (long) cooldownSeconds * 1000L);
        long now = System.currentTimeMillis();
        long cutoff = now - cooldownMillis;
        long remaining = 0L;
        for (ReportEntry existing : getReportsByReporterAndTarget(reporter.getName(), safeTarget)) {
            if (existing.getTimestamp() > cutoff && !ReportEntry.STATUS_CLOSED.equals(existing.getStatus())) {
                remaining = Math.max(remaining, existing.getTimestamp() + cooldownMillis - now);
            }
        }
        if (remaining > 0) {
            return new SubmitOutcome(SubmitResult.COOLDOWN, null, remaining);
        }

        String reportId = UUID.randomUUID().toString();
        ReportEntry report = new ReportEntry(safeTarget, reporter.getName(), safeReason,
                reportId, now, ReportEntry.STATUS_PENDING);
        if (!addReport(report)) {
            return new SubmitOutcome(SubmitResult.DB_ERROR, report, 0L);
        }
        plugin.getAuditManager().log("提交举报", reporter.getName(), safeTarget, safeReason);
        SchedulerUtils.runSync(plugin, () -> notifyStaff(report));
        return new SubmitOutcome(SubmitResult.OK, report, 0L);
    }

    public ActionResult acceptReport(String reportId, String actor) {
        ReportEntry report = getReport(reportId);
        if (report == null) {
            return ActionResult.NOT_FOUND;
        }
        String status = report.getStatus();
        if (ReportEntry.isFinalized(status)) {
            return ActionResult.FINALIZED;
        }
        if (ReportEntry.STATUS_ACCEPTED.equals(status)) {
            return ActionResult.ALREADY_ACCEPTED;
        }
        ReportEntry updated = report.withStatus(ReportEntry.STATUS_ACCEPTED);
        if (!updateReport(updated)) {
            return ActionResult.DB_ERROR;
        }
        plugin.getAuditManager().log("受理举报", actor, report.getTarget(),
                "编号: " + report.getId() + " - " + report.getReason());
        notifyReporter(report, plugin.prefix() + "§a你的举报已被受理，受理人：" + actor
                + "，举报编号：" + report.getId() + "，将尽快处理。");
        return ActionResult.OK;
    }

    public ActionResult closeReport(String reportId, String actor) {
        ReportEntry report = getReport(reportId);
        if (report == null) {
            return ActionResult.NOT_FOUND;
        }
        if (ReportEntry.isFinalized(report.getStatus())) {
            return ActionResult.FINALIZED;
        }
        ReportEntry updated = report.withStatus(ReportEntry.STATUS_CLOSED);
        if (!updateReport(updated)) {
            return ActionResult.DB_ERROR;
        }
        plugin.getAuditManager().log("关闭举报", actor, report.getTarget(),
                "编号: " + report.getId() + " - " + report.getReason());
        notifyReporter(report, plugin.prefix() + "§e你的举报(编号：" + report.getId()
                + ")已被管理员关闭。");
        return ActionResult.OK;
    }

    public void sendSubmitFeedback(CommandSender sender, SubmitOutcome outcome) {
        if (sender == null || outcome == null) {
            return;
        }
        switch (outcome.result()) {
            case OK -> Utils.sendMessage(sender, plugin.prefix() + "§a举报已提交: "
                    + outcome.report().getTarget() + " - " + outcome.report().getReason()
                    + "，举报编号：" + outcome.report().getId());
            case COOLDOWN -> Utils.sendMessage(sender, plugin.prefix() + "§c你在"
                    + TimeUtils.formatDuration(outcome.remainingMillis())
                    + "内已举报过该玩家，且该举报尚未处理完毕，请耐心等待！");
            case SELF -> Utils.sendMessage(sender, plugin.prefix() + "§c不能举报自己喵！");
            case INVALID -> Utils.sendMessage(sender, plugin.prefix() + "§c举报玩家名和原因都不能为空喵！");
            case DB_ERROR -> Utils.sendMessage(sender, plugin.prefix() + "§c举报保存失败，请稍后再试或联系管理员。");
        }
    }

    public void sendAcceptFeedback(CommandSender sender, ActionResult result, String reportId) {
        if (sender == null) {
            return;
        }
        switch (result) {
            case OK -> Utils.sendMessage(sender, plugin.prefix() + "§a你已受理举报：" + reportId);
            case NOT_FOUND -> Utils.sendMessage(sender, plugin.prefix() + "§c未找到举报编号: " + reportId);
            case FINALIZED -> Utils.sendMessage(sender, plugin.prefix() + "§c该举报已处理，无法再次操作。");
            case ALREADY_ACCEPTED -> Utils.sendMessage(sender, plugin.prefix() + "§e该举报已被受理。");
            case DB_ERROR -> Utils.sendMessage(sender, plugin.prefix() + "§c操作失败，请稍后再试。");
        }
    }

    public void sendCloseFeedback(CommandSender sender, ActionResult result, String reportId) {
        if (sender == null) {
            return;
        }
        switch (result) {
            case OK -> Utils.sendMessage(sender, plugin.prefix() + "§a你已关闭举报: " + reportId);
            case NOT_FOUND -> Utils.sendMessage(sender, plugin.prefix() + "§c未找到举报编号: " + reportId);
            case FINALIZED -> Utils.sendMessage(sender, plugin.prefix() + "§c该举报已处理，无法再次操作。");
            case ALREADY_ACCEPTED -> Utils.sendMessage(sender, plugin.prefix() + "§e该举报已被受理。");
            case DB_ERROR -> Utils.sendMessage(sender, plugin.prefix() + "§c操作失败，请稍后再试。");
        }
    }

    private int reportCooldownSeconds() {
        if (plugin.getConfig() == null) {
            return 86400;
        }
        return plugin.getConfig().getInt("report-cooldown-seconds", 86400);
    }

    private void notifyReporter(ReportEntry report, String message) {
        if (report == null || message == null || report.getReporter() == null) {
            return;
        }
        Player reporter = Bukkit.getPlayer(report.getReporter());
        if (reporter == null) {
            return;
        }
        SchedulerUtils.runSync(plugin, () -> Utils.sendMessage(reporter, message));
    }

    private void notifyStaff(ReportEntry report) {
        Player targetPlayer = Bukkit.getPlayer(report.getTarget());
        String targetLoc = "";
        BaseComponent targetComponent;
        if (targetPlayer != null) {
            targetLoc = " §7(世界: " + targetPlayer.getWorld().getName()
                    + " X:" + (int) targetPlayer.getLocation().getX()
                    + " Y:" + (int) targetPlayer.getLocation().getY()
                    + " Z:" + (int) targetPlayer.getLocation().getZ() + ")";
            targetComponent = Utils.clickableText("§c" + report.getTarget(),
                    "/lban tp " + Utils.escapeCommandArg(report.getTarget()));
        } else {
            targetComponent = new TextComponent("§7" + report.getTarget());
        }

        Player reporterPlayer = Bukkit.getPlayer(report.getReporter());
        BaseComponent reporterComponent;
        if (reporterPlayer != null) {
            reporterComponent = Utils.clickableText("§e" + report.getReporter(),
                    "/lban tp " + Utils.escapeCommandArg(report.getReporter()));
        } else {
            reporterComponent = new TextComponent("§7" + report.getReporter());
        }

        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (!admin.hasPermission("lengbanlist.admin")) {
                continue;
            }
            admin.spigot().sendMessage(
                    new TextComponent(plugin.prefix() + "§e新举报！编号：§f" + report.getId() + " §e被举报人："),
                    targetComponent,
                    new TextComponent(" §e举报人："),
                    reporterComponent,
                    new TextComponent(" §e原因：§f" + report.getReason() + targetLoc)
            );
        }
    }

    public BanManager.BanMutationResult tryBanFromReport(ReportEntry entry, String staff, long endTime, String reason, boolean isAuto) {
        BanEntry banEntry = new BanEntry(entry.getTarget(), staff, endTime, reason, isAuto);
        DatabaseManager.WriteResult writeResult = plugin.getDatabaseManager()
                .replaceActiveBanAndUpdateReport(banEntry, entry, ReportEntry.STATUS_HANDLED);
        if (writeResult == DatabaseManager.WriteResult.DATABASE_ERROR) {
            return BanManager.BanMutationResult.DATABASE_ERROR;
        }
        if (writeResult == DatabaseManager.WriteResult.NO_CHANGE) {
            return BanManager.BanMutationResult.STATE_CHANGED;
        }
        plugin.getBanManager().publishAppliedPlayerBan(banEntry, false);
        Player reporterPlayer = Bukkit.getPlayer(entry.getReporter());
        if (reporterPlayer != null) {
            long durationMillis = endTime == Long.MAX_VALUE ? Long.MAX_VALUE : endTime - System.currentTimeMillis();
            Utils.sendMessage(reporterPlayer, plugin.getModelManager().getCurrentModel().onReportBan(entry.getTarget(), TimeUtils.formatDuration(durationMillis)));
        }
        return BanManager.BanMutationResult.APPLIED;
    }

}

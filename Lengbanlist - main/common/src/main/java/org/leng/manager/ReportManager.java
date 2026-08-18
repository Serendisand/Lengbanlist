package org.leng.manager;

import org.leng.object.BanEntry;
import org.leng.object.ReportEntry;
import org.leng.platform.LengbanlistPlatform;
import org.leng.utils.TimeUtils;

import java.util.List;

public class ReportManager {
    private final LengbanlistPlatform plugin;

    public ReportManager(LengbanlistPlatform plugin) {
        this.plugin = plugin;
    }

    public void addReport(ReportEntry report) {
        updateReport(report);
    }

    public void updateReport(ReportEntry report) {
        plugin.getDatabaseManager().upsertReport(report);
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

    public void saveReports() {
    }

    public void loadReports() {
    }

    public List<ReportEntry> getPendingReports() {
        return plugin.getDatabaseManager().getPendingReports();
    }

    public int getPendingReportCount() {
        return plugin.getDatabaseManager().getPendingReportCount();
    }

    public BanManager.BanMutationResult tryBanFromReport(ReportEntry entry, String staff, long endTime, String reason, boolean isAuto) {
        BanEntry banEntry = new BanEntry(entry.getTarget(), staff, endTime, reason, isAuto);
        DatabaseManager.WriteResult writeResult = plugin.getDatabaseManager()
                .replaceActiveBanAndUpdateReport(banEntry, entry, "已处理");
        if (writeResult == DatabaseManager.WriteResult.DATABASE_ERROR) {
            return BanManager.BanMutationResult.DATABASE_ERROR;
        }
        if (writeResult == DatabaseManager.WriteResult.NO_CHANGE) {
            return BanManager.BanMutationResult.STATE_CHANGED;
        }
        entry.setStatus("已处理");
        plugin.getBanManager().publishAppliedPlayerBan(banEntry, false);
        plugin.getAuditManager().log("举报转封禁", staff, entry.getTarget(), reason);
        long durationMillis = endTime == Long.MAX_VALUE ? Long.MAX_VALUE : endTime - System.currentTimeMillis();
        String message = plugin.getModelManager().getCurrentModel().onReportBan(entry.getTarget(), TimeUtils.formatDuration(durationMillis));
        plugin.getLogger().info(message);
        return BanManager.BanMutationResult.APPLIED;
    }

}

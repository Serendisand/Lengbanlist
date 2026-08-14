package org.leng.models;

import org.leng.platform.MessageSink;

import java.util.List;

public interface Model {
    static String formatBanDays(int days) {
        return days == Integer.MAX_VALUE ? "永久" : days + " 天";
    }

    String getName();

    void showHelp(MessageSink sender);

    String toggleBroadcast(boolean enabled);

    String reloadConfig();

    String addBan(String player, int days, String reason);

    String removeBan(String player);

    String addMute(String player, String reason);

    String removeMute(String player);

    String addBanIp(String ip, int days, String reason);

    String removeBanIp(String ip);

    String addWarn(String player, String reason);

    String removeWarn(String player);

    String getKickMessage(String reason);

    String onKickSuccess(String playerName, String reason);

    String getHistory(String player, List<String> entries);

    String onMuteCommandBlocked();

    String onWarnOffline(String player, String reason);

    String getPendingWarningsNotice(int count);

    String getExpiryReminder(String type, String target, String remaining);

    String onEscalatedBan(String player, int offenseCount, String duration);

    String getAltsResult(String player, int count);

    String getNoAlts(String player);

    String onReportBan(String player, String duration);

    String getExportResult(int count);

    String getVerifyResult(boolean valid, int count);

    String getSyncStatus(String detail);

    String getImmunityDenied(String target);

    /** 回滚预览：显示查询到的可回滚操作条数。 */
    String getRollbackPreview(int matched, String actor, String timeRange);

    /** 回滚结果：显示执行/跳过条数。 */
    String getRollbackResult(int matched, int executed, int skipped);

    /** 无回滚记录提示。 */
    String getRollbackNoRecords(String actor);
}

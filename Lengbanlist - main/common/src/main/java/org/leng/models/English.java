package org.leng.models;

import org.leng.platform.MessageSink;
import org.leng.platform.PlatformHolder;

import java.util.List;

public class English implements Model {
    @Override
    public String getName() {
        return "English";
    }

    @Override
    public void showHelp(MessageSink sender) {
        sender.sendMessage("§b╔══════════════════════════════════════╗");
        sender.sendMessage("§b║ §2§oLengbanlist Help - English §b║");
        sender.sendMessage("§b╠══════════════════════════════════════╣");
        sender.sendMessage("§6§l◆ Punishments");
        sender.sendMessage("§2✦ §b/lban add [optional]-s <required>player <required>days/auto <required>reason §7- §3Ban a player");
        sender.sendMessage("§7  = §b/ban");
        sender.sendMessage("§2✦ §b/lban remove <required>player §7- §3Unban a player");
        sender.sendMessage("§7  = §b/unban");
        sender.sendMessage("§2✦ §b/ban-ip [optional]-s <required>IP <required>days/auto <required>reason §7- §3Ban an IP address");
        sender.sendMessage("§2✦ §b/lban mute [optional]-s <required>player <required>time/auto <required>reason §7- §3Mute a player");
        sender.sendMessage("§7  = §b/mute");
        sender.sendMessage("§2✦ §b/lban unmute [optional]-s <required>player §7- §3Unmute a player");
        sender.sendMessage("§7  = §b/unmute");
        sender.sendMessage("§2✦ §b/lban warn <required>player <required>reason §7- §3Warn a player, 3 = auto-ban");
        sender.sendMessage("§7  = §b/warn");
        sender.sendMessage("§2✦ §b/lban unwarn <required>player §7- §3Remove player warnings");
        sender.sendMessage("§7  = §b/unwarn");
        sender.sendMessage("§2✦ §b/kick <required>player [optional]reason §7- §3Kick a player (reason optional)");
        sender.sendMessage("§2✦ §b/setban <required>player/IP <required>time/forever/auto <required>reason §7- §3Modify ban time");
        sender.sendMessage("§6§l◆ Information");
        sender.sendMessage("§2✦ §b/lban check <required>player/IP §7- §3Check ban status");
        sender.sendMessage("§2✦ §b/lban history <required>player §7- §3Query punishment history");
        sender.sendMessage("§7  = §b/history");
        sender.sendMessage("§2✦ §b/report <required>player <required>reason §7- §3Report a player");
        sender.sendMessage("§2✦ §b/lban getip [optional]player §7- §3Query player IP (omit for self)");
        sender.sendMessage("§2✦ §b/lban alts <required>player §7- §3Query same-IP alt accounts");
        sender.sendMessage("§6§l◆ Audit & Rollback");
        sender.sendMessage("§2✦ §b/lban audit [optional]operator §7- §3View audit log");
        sender.sendMessage("§2✦ §b/lban audit export [optional]count §7- §3Export audit log");
        sender.sendMessage("§2✦ §b/lban audit verify §7- §3Verify audit integrity");
        sender.sendMessage("§2✦ §b/lban sync §7- §3View cross-server sync status");
        sender.sendMessage("§2✦ §b/lban rollback <required>operator <required>start <required>end [optional]type §7- §3Rollback admin actions");
        sender.sendMessage("§6§l◆ Miscellaneous");
        sender.sendMessage("§2✦ §b/lban list §7- §3View ban list");
        sender.sendMessage("§2✦ §b/lban list-mute §7- §3View mute list");
        sender.sendMessage("§7  = §b/listmute");
        sender.sendMessage("§2✦ §b/lban a §7- §3Broadcast ban count");
        sender.sendMessage("§2✦ §b/lban toggle §7- §3Toggle auto broadcast");
        sender.sendMessage("§2✦ §b/lban open §7- §3Open visual operation UI");
        sender.sendMessage("§2✦ §b/lban model <required>model §7- §3Switch model");
        sender.sendMessage("§2✦ §b/lban reload §7- §3Reload configuration");
        sender.sendMessage("§2✦ §b/lban info §7- §3View plugin info");
        sender.sendMessage("§b╚══════════════════════════════════════╝");
        sender.sendMessage("§2♡ Current Version: " + PlatformHolder.get().getPluginVersion() + " §7| §bModel: English");
    }

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §dEnglish Model Kick Notice  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ You have been kicked from the server!\n\n" +
               "§7Reason: §f" + reason + "\n\n" +
               "§dPlease follow the rules next time~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ English Model: §a" + playerName + " §ehas been kicked!\n" +
               "§bReason: §f" + reason + "\n" +
               "§bMaintaining order, no disruption allowed! §b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§bEnglish Model: §aAutomatic broadcast has been " + (enabled ? "enabled" : "disabled");
    }

    @Override
    public String reloadConfig() {
        return "§bEnglish Model: §aConfiguration reloaded successfully";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        String durationText = days == Integer.MAX_VALUE ? "permanently" : days + " days";
        return "§bEnglish Model: §aPlayer " + player + " has been banned for " + durationText + ", reason: " + reason;
    }

    @Override
    public String removeBan(String player) {
        return "§bEnglish Model: §aPlayer " + player + " has been removed from ban list";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§bEnglish Model: §aPlayer " + player + " has been muted, reason: " + reason;
    }

    @Override
    public String removeMute(String player) {
        return "§bEnglish Model: §aPlayer " + player + " has been unmuted";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        String durationText = days == Integer.MAX_VALUE ? "permanently" : days + " days";
        return "§bEnglish Model: §aIP " + ip + " has been banned for " + durationText + ", reason: " + reason;
    }

    @Override
    public String removeBanIp(String ip) {
        return "§bEnglish Model: §aIP " + ip + " has been unbanned";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§bEnglish Model: §aPlayer " + player + " has been warned, reason: " + reason + ". 3 warnings will result in automatic ban.";
    }

    @Override
    public String removeWarn(String player) {
        return "§bEnglish Model: §aWarning records for " + player + " have been removed.";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§bEnglish Model: §aPlayer " + player + " has a clean record. Good job!";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§bEnglish Model: §aPunishment history for ").append(player).append(":\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        return sb.toString().trim();
    }

    public String onMuteCommandBlocked() {
        return "§bEnglish Model: §cYou cannot use this command while muted. Please wait until your mute expires~";
    }

    public String onWarnOffline(String player, String reason) {
        return "§bEnglish Model: §aOffline player " + player + " has been warned, reason: " + reason + ". They will be notified when they come online~";
    }

    public String getPendingWarningsNotice(int count) {
        return "§bEnglish Model: §eYou have " + count + " pending warning(s). Please follow the rules~";
    }

    public String getExpiryReminder(String type, String target, String remaining) {
        return "§bEnglish Model: §a" + target + "'s " + type + " will expire in " + remaining + "!";
    }

    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§bEnglish Model: §aPlayer " + player + " has been automatically banned for " + duration + " after " + offenseCount + " offense(s)!";
    }

    public String getAltsResult(String player, int count) {
        return "§bEnglish Model: §aFound " + count + " alt account(s) for player " + player + ":";
    }

    public String getNoAlts(String player) {
        return "§bEnglish Model: §aNo alt accounts found for player " + player + ". Clean record!";
    }

    public String onReportBan(String player, String duration) {
        return "§bEnglish Model: §aReport confirmed! Player " + player + " has been banned for " + duration + "! Maintaining order, no disruption allowed! §b(◕‿◕✿)";
    }

    public String getExportResult(int count) {
        return "§bEnglish Model: §aAudit log exported successfully, total: " + count + " entries.";
    }

    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§bEnglish Model: §aAudit verification passed! All " + count + " entries are intact.";
        }
        return "§bEnglish Model: §cAudit verification failed! Detected " + count + " entries that may have been tampered with!";
    }

    public String getSyncStatus(String detail) {
        return "§bEnglish Model: §aCross-server sync status: §f" + detail;
    }

    public String getImmunityDenied(String target) {
        return "§bEnglish Model: §cTarget " + target + " has equal or higher permission weight, action denied!";
    }

    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§bEnglish Model: §eOperator " + actor + " has " + matched + " rollbackable operation(s) in " + timeRange + ".";
    }

    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§bEnglish Model: §aRollback complete! Matched " + matched + ", executed " + executed + ", skipped " + skipped + ".";
    }

    public String getRollbackNoRecords(String actor) {
        return "§bEnglish Model: §eOperator " + actor + " has no rollbackable records in the specified range.";
    }

}

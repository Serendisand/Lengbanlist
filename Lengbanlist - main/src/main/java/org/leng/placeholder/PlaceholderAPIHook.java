package org.leng.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.WarnEntry;
import org.leng.utils.TimeUtils;

import java.util.List;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final Lengbanlist plugin;

    public PlaceholderAPIHook(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "lengbanlist";
    }

    @Override
    public String getAuthor() {
        return "Serendisand";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        }

        String lower = params.toLowerCase();

        if (lower.equals("bans")) {
            return String.valueOf(plugin.getBanManager().countActiveBans());
        }
        if (lower.equals("ip_bans")) {
            return String.valueOf(plugin.getBanManager().countActiveIpBans());
        }
        if (lower.equals("total_bans")) {
            return String.valueOf(plugin.getBanManager().countActiveBans() + plugin.getBanManager().countActiveIpBans());
        }
        if (lower.equals("mutes")) {
            return String.valueOf(plugin.getMuteManager().countActiveMutes());
        }
        if (lower.equals("pending_reports")) {
            return String.valueOf(plugin.getReportManager().getPendingReportCount());
        }

        if (lower.equals("banned")) {
            return String.valueOf(isBannedByName(player));
        }
        if (lower.equals("muted")) {
            return String.valueOf(isMutedByName(player));
        }
        if (lower.equals("ban_expire")) {
            return banExpire(player == null ? null : player.getName());
        }
        if (lower.equals("ban_remaining")) {
            return banRemaining(player == null ? null : player.getName());
        }
        if (lower.equals("ban_reason")) {
            return banReason(player == null ? null : player.getName());
        }
        if (lower.equals("ban_actor")) {
            return banActor(player == null ? null : player.getName());
        }
        if (lower.equals("ban_active")) {
            return banActive(player == null ? null : player.getName());
        }
        if (lower.equals("mute_remaining")) {
            return muteRemaining(player == null ? null : player.getName());
        }
        if (lower.equals("warnings")) {
            return String.valueOf(activeWarnings(player == null ? null : player.getName()));
        }
        if (lower.equals("warnings_total")) {
            return String.valueOf(totalWarnings(player == null ? null : player.getName()));
        }
        if (lower.equals("last_warn_reason")) {
            return lastWarnReason(player == null ? null : player.getName());
        }

        if (lower.startsWith("banned_")) {
            return String.valueOf(plugin.getBanManager().isPlayerBanned(params.substring(7)));
        }
        if (lower.startsWith("muted_")) {
            return String.valueOf(plugin.getMuteManager().isPlayerMuted(params.substring(6)));
        }
        if (lower.startsWith("ban_expire_")) {
            return banExpire(params.substring(11));
        }
        if (lower.startsWith("ban_remaining_")) {
            return banRemaining(params.substring(15));
        }
        if (lower.startsWith("ban_reason_")) {
            return banReason(params.substring(12));
        }
        if (lower.startsWith("ban_actor_")) {
            return banActor(params.substring(11));
        }
        if (lower.startsWith("mute_remaining_")) {
            return muteRemaining(params.substring(16));
        }
        if (lower.startsWith("warnings_")) {
            return String.valueOf(activeWarnings(params.substring(9)));
        }
        if (lower.startsWith("warnings_total_")) {
            return String.valueOf(totalWarnings(params.substring(16)));
        }
        return null;
    }

    private boolean isBannedByName(OfflinePlayer player) {
        return player != null && plugin.getBanManager().isPlayerBanned(player.getName());
    }

    private boolean isMutedByName(OfflinePlayer player) {
        return player != null && plugin.getMuteManager().isPlayerMuted(player.getName());
    }

    private String banExpire(String target) {
        if (target == null) return "无";
        BanEntry ban = plugin.getBanManager().getBanEntry(target);
        if (ban != null && ban.time() > System.currentTimeMillis()) {
            return TimeUtils.timestampToReadable(ban.time());
        }
        return "无";
    }

    private String banRemaining(String target) {
        if (target == null) return "无";
        BanEntry ban = plugin.getBanManager().getBanEntry(target);
        if (ban != null) {
            return TimeUtils.getRemainingTime(ban.time());
        }
        return "无";
    }

    private String banReason(String target) {
        if (target == null) return "无";
        BanEntry ban = plugin.getBanManager().getBanEntry(target);
        return ban == null ? "无" : ban.reason();
    }

    private String banActor(String target) {
        if (target == null) return "无";
        BanEntry ban = plugin.getBanManager().getBanEntry(target);
        return ban == null ? "无" : ban.staff();
    }

    private String banActive(String target) {
        if (target == null) return "false";
        BanEntry ban = plugin.getBanManager().getBanEntry(target);
        return ban == null ? "false" : String.valueOf(ban.active());
    }

    private String muteRemaining(String target) {
        if (target == null) return "无";
        Long endTime = plugin.getMuteManager().getActiveMuteEndTime(target);
        return endTime == null ? "无" : TimeUtils.getRemainingTime(endTime);
    }

    private int activeWarnings(String target) {
        if (target == null) return 0;
        return plugin.getWarnManager().countActiveWarnings(target);
    }

    private int totalWarnings(String target) {
        if (target == null) return 0;
        return plugin.getWarnManager().getAllWarnings(target).size();
    }

    private String lastWarnReason(String target) {
        if (target == null) return "无";
        List<WarnEntry> warnings = plugin.getWarnManager().getAllWarnings(target);
        if (warnings.isEmpty()) return "无";

        WarnEntry latest = warnings.get(0);
        for (WarnEntry w : warnings) {
            if (w.time() > latest.time()) latest = w;
        }
        return latest.reason();
    }
}

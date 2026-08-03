package org.leng.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.utils.TimeUtils;

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
            return String.valueOf(plugin.getBanManager().getBanList().size());
        }
        if (lower.equals("ip_bans")) {
            return String.valueOf(plugin.getBanManager().getBanIpList().size());
        }
        if (lower.equals("total_bans")) {
            return String.valueOf(plugin.getBanManager().getBanList().size() + plugin.getBanManager().getBanIpList().size());
        }
        if (lower.equals("mutes")) {
            return String.valueOf(plugin.getMuteManager().getMuteList().size());
        }
        if (lower.equals("pending_reports")) {
            return String.valueOf(plugin.getReportManager().getPendingReportCount());
        }
        if (lower.equals("banned")) {
            if (player != null) {
                return String.valueOf(plugin.getBanManager().isPlayerBanned(player.getName()));
            }
            return "false";
        }
        if (lower.equals("muted")) {
            if (player != null) {
                return String.valueOf(plugin.getMuteManager().isPlayerMuted(player.getName()));
            }
            return "false";
        }
        if (lower.equals("ban_expire")) {
            if (player != null) {
                return banExpire(player.getName());
            }
            return "无";
        }
        if (lower.equals("warnings")) {
            if (player != null) {
                return String.valueOf(plugin.getWarnManager().getActiveWarnings(player.getName()).size());
            }
            return "0";
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
        if (lower.startsWith("warnings_")) {
            return String.valueOf(plugin.getWarnManager().getActiveWarnings(params.substring(9)).size());
        }
        return null;
    }

    private String banExpire(String target) {
        BanEntry ban = plugin.getBanManager().getBanEntry(target);
        if (ban != null && ban.getTime() > System.currentTimeMillis()) {
            return TimeUtils.timestampToReadable(ban.getTime());
        }
        return "无";
    }
}

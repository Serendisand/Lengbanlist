package org.leng.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.IpMatcher;
import org.leng.utils.Utils;

import java.util.List;


public class BanManager {
    private final Lengbanlist plugin;
    private final DatabaseManager db;

    public BanManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void banPlayer(BanEntry banEntry) {
        banPlayer(banEntry, false);
    }

    public void banPlayer(BanEntry banEntry, boolean silent) {
        long durationMillis = banEntry.getEndTime() == Long.MAX_VALUE ? Long.MAX_VALUE : banEntry.getEndTime() - System.currentTimeMillis();
        int durationDays = durationMillis == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, Math.round(durationMillis / (double)(1000 * 60 * 60 * 24)));

        Model currentModel = plugin.getModelManager().getCurrentModel();
        String banResult = currentModel.addBan(banEntry.getTarget(), durationDays, banEntry.getReason());
        updateBan(banEntry);
        plugin.getAuditManager().log("封禁", banEntry.getStaff(), banEntry.getTarget(), banEntry.getReason());

        Player targetPlayer = Bukkit.getPlayer(banEntry.getTarget());
        if (targetPlayer != null) {
            String kickMessage = String.format(
                    "§c您已被封禁!\n" +
                            "§f原因: §e%s\n" +
                            "§f封禁时长: §a%s\n" +
                            "§f解封时间: §b%s",
                    banEntry.getReason(),
                    TimeUtils.formatDuration(durationMillis),
                    TimeUtils.timestampToReadable(banEntry.getEndTime())
            );
            SchedulerUtils.runTask(plugin, targetPlayer, () -> targetPlayer.kickPlayer(kickMessage));
        }

        if (!silent) {
            if (banResult != null && !banResult.isEmpty()) {
                Utils.broadcast(banResult);
            } else {
                String defaultMessage = String.format("§c玩家 %s 已被封禁！原因：%s，时长：%s", banEntry.getTarget(), banEntry.getReason(), TimeUtils.formatDuration(durationMillis));
                Utils.broadcast(defaultMessage);
            }
        }
    }

    public void banIp(BanIpEntry banIpEntry) {
        banIp(banIpEntry, false);
    }

    public void banIp(BanIpEntry banIpEntry, boolean silent) {
        if (IpMatcher.isLoopback(banIpEntry.getIp())) {
            plugin.getLogger().warning("已阻止封禁本地回环 IP: " + banIpEntry.getIp() + "（staff: " + banIpEntry.getStaff() + "）");
            return;
        }
        long durationMillis = banIpEntry.getEndTime() == Long.MAX_VALUE ? Long.MAX_VALUE : banIpEntry.getEndTime() - System.currentTimeMillis();
        int durationDays = durationMillis == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, Math.round(durationMillis / (double)(1000 * 60 * 60 * 24)));

        Model currentModel = plugin.getModelManager().getCurrentModel();
        String banIpResult = currentModel.addBanIp(banIpEntry.getIp(), durationDays, banIpEntry.getReason());
        updateIpBan(banIpEntry);
        plugin.getAuditManager().log("封禁IP", banIpEntry.getStaff(), banIpEntry.getIp(), banIpEntry.getReason());

        if (!silent) {
            if (banIpResult != null && !banIpResult.isEmpty()) {
                Utils.broadcast(banIpResult);
            } else {
                String defaultMessage = String.format("§cIP %s 已被封禁！原因：%s，时长：%s", banIpEntry.getIp(), banIpEntry.getReason(), TimeUtils.formatDuration(durationMillis));
                Utils.broadcast(defaultMessage);
            }
        }
    }

    public void unbanPlayer(String target) {
        unbanPlayer(target, null, false);
    }

    public void unbanPlayer(String target, boolean silent) {
        unbanPlayer(target, null, silent);
    }

    public void unbanPlayer(String target, String actor) {
        unbanPlayer(target, actor, false);
    }

    public void unbanPlayer(String target, String actor, boolean silent) {
        Model currentModel = plugin.getModelManager().getCurrentModel();
        String unbanResult = currentModel.removeBan(target);
        boolean removed = isPlayerBanned(target);
        db.deactivateBan(target);

        if (removed) {
            plugin.getAuditManager().log("解封", actor, target, "");
            if (!silent) {
                if (unbanResult != null && !unbanResult.isEmpty()) {
                    Utils.broadcast(unbanResult);
                } else {
                    Utils.broadcast(String.format("§a玩家 %s 已被解封", target));
                }
            }
        }
    }

    public void unbanIp(String ip) {
        unbanIp(ip, null, false);
    }

    public void unbanIp(String ip, boolean silent) {
        unbanIp(ip, null, silent);
    }

    public void unbanIp(String ip, String actor) {
        unbanIp(ip, actor, false);
    }

    public void unbanIp(String ip, String actor, boolean silent) {
        Model currentModel = plugin.getModelManager().getCurrentModel();
        String unbanIpResult = currentModel.removeBanIp(ip);
        boolean removed = isIpBanned(ip);
        db.deactivateIpBan(ip);

        if (removed) {
            plugin.getAuditManager().log("解封IP", actor, ip, "");
            if (!silent) {
                if (unbanIpResult != null && !unbanIpResult.isEmpty()) {
                    Utils.broadcast(unbanIpResult);
                } else {
                    Utils.broadcast(String.format("§aIP %s 已被解封", ip));
                }
            }
        }
    }

    public boolean isPlayerBanned(String target) {
        return db.isPlayerBanned(target);
    }

    public boolean isIpBanned(String ip) {
        return db.isIpBanned(ip);
    }

    public List<BanEntry> getBanList() {
        return db.getBans();
    }

    public List<BanIpEntry> getBanIpList() {
        return db.getIpBans();
    }

    public void checkBanOnJoin(Player player) {
        if (plugin.isFeatureEnabled("ban")) {
            BanEntry ban = getBanEntry(player.getName());
            if (ban != null) {
                long currentTime = System.currentTimeMillis();
                if (ban.getTime() <= currentTime) {
                    unbanPlayer(player.getName());
                } else {
                    SchedulerUtils.runTask(plugin, player, () -> player.kickPlayer("您仍处于封禁状态，原因：" + ban.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(ban.getTime())));
                    return;
                }
            }
        }

        if (plugin.isFeatureEnabled("ban-ip") && player.getAddress() != null) {
            String ip = player.getAddress().getAddress().getHostAddress();
            BanIpEntry banIp = getMatchingIpBan(ip);
            if (banIp != null) {
                long currentTime = System.currentTimeMillis();
                if (banIp.getTime() <= currentTime) {
                    unbanIp(banIp.getIp());
                } else {
                    SchedulerUtils.runTask(plugin, player, () -> player.kickPlayer("您的 IP 仍处于封禁状态，原因：" + banIp.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(banIp.getTime())));
                }
            }
        }
    }

    public BanEntry getBanEntry(String target) {
        return db.getBan(target);
    }

    public BanIpEntry getBanIpEntry(String ip) {
        return db.getIpBan(ip);
    }

    public void updateBan(BanEntry entry) {
        db.upsertBan(entry);
    }

    public void updateIpBan(BanIpEntry entry) {
        db.upsertIpBan(entry);
    }

    public boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return ip.contains(":");
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public boolean isValidIpOrCidr(String value) {
        return IpMatcher.isValidIpOrCidr(value);
    }

    public BanIpEntry getMatchingIpBan(String ip) {
        if (ip == null) return null;
        for (BanIpEntry entry : getBanIpList()) {
            if (entry.getIp().equals(ip)) return entry;
            if (IpMatcher.cidrMatches(ip, entry.getIp())) return entry;
        }
        return null;
    }

    public boolean isIpBannedByCidr(String ip) {
        return getMatchingIpBan(ip) != null;
    }

    public boolean isBanned(String player, String reason) {
        BanEntry banEntry = getBanEntry(player);
        return banEntry != null && banEntry.getReason().contains(reason);
    }

    public void saveBanList() {}
    public void saveBanIpConfig() {}
}

package org.leng.manager;

import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.platform.LengbanlistPlatform;
import org.leng.utils.TimeUtils;

import java.util.List;


public class BanManager {
    private final LengbanlistPlatform plugin;
    private final DatabaseManager db;

    public BanManager(LengbanlistPlatform plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void banPlayer(BanEntry banEntry) {
        long durationMillis = banEntry.getEndTime() == Long.MAX_VALUE ? Long.MAX_VALUE : banEntry.getEndTime() - System.currentTimeMillis();
        int durationDays = durationMillis == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, Math.round(durationMillis / (double)(1000 * 60 * 60 * 24)));

        Model currentModel = plugin.getModelManager().getCurrentModel();
        String banResult = currentModel.addBan(banEntry.getTarget(), durationDays, banEntry.getReason());
        updateBan(banEntry);

        String kickMessage = String.format(
                "§c您已被封禁!\n" +
                        "§f原因: §e%s\n" +
                        "§f封禁时长: §a%s\n" +
                        "§f解封时间: §b%s",
                banEntry.getReason(),
                TimeUtils.formatDuration(durationMillis),
                TimeUtils.timestampToReadable(banEntry.getEndTime())
        );
        plugin.runSync(() -> plugin.kickPlayerIfOnline(banEntry.getTarget(), kickMessage));

        if (banResult != null && !banResult.isEmpty()) {
            plugin.broadcastMessage(banResult);
        } else {
            String defaultMessage = String.format("§c玩家 %s 已被封禁！原因：%s，时长：%s", banEntry.getTarget(), banEntry.getReason(), TimeUtils.formatDuration(durationMillis));
            plugin.broadcastMessage(defaultMessage);
        }
    }

    public void banIp(BanIpEntry banIpEntry) {
        long durationMillis = banIpEntry.getEndTime() == Long.MAX_VALUE ? Long.MAX_VALUE : banIpEntry.getEndTime() - System.currentTimeMillis();
        int durationDays = durationMillis == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, Math.round(durationMillis / (double)(1000 * 60 * 60 * 24)));

        Model currentModel = plugin.getModelManager().getCurrentModel();
        String banIpResult = currentModel.addBanIp(banIpEntry.getIp(), durationDays, banIpEntry.getReason());
        updateIpBan(banIpEntry);

        if (banIpResult != null && !banIpResult.isEmpty()) {
            plugin.broadcastMessage(banIpResult);
        } else {
            String defaultMessage = String.format("§cIP %s 已被封禁！原因：%s，时长：%s", banIpEntry.getIp(), banIpEntry.getReason(), TimeUtils.formatDuration(durationMillis));
            plugin.broadcastMessage(defaultMessage);
        }
    }

    public void unbanPlayer(String target) {
        Model currentModel = plugin.getModelManager().getCurrentModel();
        String unbanResult = currentModel.removeBan(target);
        boolean removed = isPlayerBanned(target);
        db.deactivateBan(target);

        if (removed) {
            if (unbanResult != null && !unbanResult.isEmpty()) {
                plugin.broadcastMessage(unbanResult);
            } else {
                plugin.broadcastMessage(String.format("§a玩家 %s 已被解封", target));
            }
        }
    }

    public void unbanIp(String ip) {
        Model currentModel = plugin.getModelManager().getCurrentModel();
        String unbanIpResult = currentModel.removeBanIp(ip);
        boolean removed = isIpBanned(ip);
        db.deactivateIpBan(ip);

        if (removed) {
            if (unbanIpResult != null && !unbanIpResult.isEmpty()) {
                plugin.broadcastMessage(unbanIpResult);
            } else {
                plugin.broadcastMessage(String.format("§aIP %s 已被解封", ip));
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

    public String checkBanOnJoin(String playerName, String ip) {
        if (plugin.isFeatureEnabled("ban")) {
            BanEntry ban = getBanEntry(playerName);
            if (ban != null) {
                long currentTime = System.currentTimeMillis();
                if (ban.getTime() <= currentTime) {
                    unbanPlayer(playerName);
                } else {
                    return "您仍处于封禁状态，原因：" + ban.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(ban.getTime());
                }
            }
        }

        if (plugin.isFeatureEnabled("ban-ip") && ip != null) {
            BanIpEntry banIp = getBanIpEntry(ip);
            if (banIp != null) {
                long currentTime = System.currentTimeMillis();
                if (banIp.getTime() <= currentTime) {
                    unbanIp(ip);
                } else {
                    return "您的 IP 仍处于封禁状态，原因：" + banIp.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(banIp.getTime());
                }
            }
        }
        return null;
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

    public boolean isBanned(String player, String reason) {
        BanEntry banEntry = getBanEntry(player);
        return banEntry != null && banEntry.getReason().contains(reason);
    }

    public void saveBanList() {}
    public void saveBanIpConfig() {}
}

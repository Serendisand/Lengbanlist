package org.leng.manager;

import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.platform.LengbanlistPlatform;
import org.leng.utils.IpMatcher;
import org.leng.utils.TimeUtils;

import java.util.List;


public class BanManager {

    public enum BanMutationResult {
        APPLIED,
        NOT_ACTIVE,
        STATE_CHANGED,
        REJECTED_PRIVATE_OR_RESERVED_IP,
        DATABASE_ERROR;

        public boolean isApplied() {
            return this == APPLIED;
        }
    }

    private final LengbanlistPlatform plugin;
    private final DatabaseManager db;

    public BanManager(LengbanlistPlatform plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public BanMutationResult tryBanPlayer(BanEntry banEntry) {
        return tryBanPlayer(banEntry, false);
    }

    public BanMutationResult tryBanPlayer(BanEntry banEntry, boolean silent) {
        BanMutationResult writeResult = mapWriteResult(db.replaceActiveBan(banEntry));
        if (!writeResult.isApplied()) {
            return writeResult;
        }

        publishAppliedPlayerBan(banEntry, silent);
        return BanMutationResult.APPLIED;
    }

    void publishAppliedPlayerBan(BanEntry banEntry, boolean silent) {
        long durationMillis = banEntry.getEndTime() == Long.MAX_VALUE ? Long.MAX_VALUE : banEntry.getEndTime() - System.currentTimeMillis();
        int durationDays = durationMillis == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, Math.round(durationMillis / (double) (1000 * 60 * 60 * 24)));

        Model currentModel = plugin.getModelManager().getCurrentModel();
        String banResult = currentModel.addBan(banEntry.getTarget(), durationDays, banEntry.getReason());
        plugin.getAuditManager().log("封禁", banEntry.getStaff(), banEntry.getTarget(), banEntry.getReason());

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

        if (!silent) {
            if (banResult != null && !banResult.isEmpty()) {
                plugin.broadcastMessage(banResult);
            } else {
                String defaultMessage = String.format("§c玩家 %s 已被封禁！原因：%s，时长：%s", banEntry.getTarget(), banEntry.getReason(), TimeUtils.formatDuration(durationMillis));
                plugin.broadcastMessage(defaultMessage);
            }
        }
    }

    public BanMutationResult tryBanIp(BanIpEntry banIpEntry) {
        return tryBanIp(banIpEntry, false);
    }

    public BanMutationResult tryBanIp(BanIpEntry banIpEntry, boolean silent) {
        if (isPrivateOrReservedIp(banIpEntry)) {
            return BanMutationResult.REJECTED_PRIVATE_OR_RESERVED_IP;
        }
        BanMutationResult writeResult = mapWriteResult(db.replaceActiveIpBan(banIpEntry));
        if (!writeResult.isApplied()) {
            return writeResult;
        }
        long durationMillis = banIpEntry.getEndTime() == Long.MAX_VALUE ? Long.MAX_VALUE : banIpEntry.getEndTime() - System.currentTimeMillis();
        int durationDays = durationMillis == Long.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, Math.round(durationMillis / (double) (1000 * 60 * 60 * 24)));

        Model currentModel = plugin.getModelManager().getCurrentModel();
        String banIpResult = currentModel.addBanIp(banIpEntry.getIp(), durationDays, banIpEntry.getReason());
        plugin.getAuditManager().log("封禁IP", banIpEntry.getStaff(), banIpEntry.getIp(), banIpEntry.getReason());

        if (!silent) {
            if (banIpResult != null && !banIpResult.isEmpty()) {
                plugin.broadcastMessage(banIpResult);
            } else {
                String defaultMessage = String.format("§cIP %s 已被封禁！原因：%s，时长：%s", banIpEntry.getIp(), banIpEntry.getReason(), TimeUtils.formatDuration(durationMillis));
                plugin.broadcastMessage(defaultMessage);
            }
        }
        return BanMutationResult.APPLIED;
    }

    public BanMutationResult tryUnbanPlayer(String target, String actor, boolean silent) {
        long now = System.currentTimeMillis();
        BanMutationResult writeResult = mapWriteResult(db.deactivateBanForUnban(target, now));
        if (writeResult.isApplied()) {
            Model currentModel = plugin.getModelManager().getCurrentModel();
            String unbanResult = currentModel.removeBan(target);
            plugin.getAuditManager().log("解封", actor, target, "");
            if (!silent) {
                if (unbanResult != null && !unbanResult.isEmpty()) {
                    plugin.broadcastMessage(unbanResult);
                } else {
                    plugin.broadcastMessage(String.format("§a玩家 %s 已被解封", target));
                }
            }
            return BanMutationResult.APPLIED;
        }
        return writeResult;
    }

    public BanMutationResult tryUnbanIp(String ip, String actor, boolean silent) {
        long now = System.currentTimeMillis();
        BanMutationResult writeResult = mapWriteResult(db.deactivateIpBanForUnban(ip, now));
        if (writeResult.isApplied()) {
            Model currentModel = plugin.getModelManager().getCurrentModel();
            String unbanIpResult = currentModel.removeBanIp(ip);
            plugin.getAuditManager().log("解封IP", actor, ip, "");
            if (!silent) {
                if (unbanIpResult != null && !unbanIpResult.isEmpty()) {
                    plugin.broadcastMessage(unbanIpResult);
                } else {
                    plugin.broadcastMessage(String.format("§aIP %s 已被解封", ip));
                }
            }
            return BanMutationResult.APPLIED;
        }
        return writeResult;
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
                    BanMutationResult result = tryUnbanPlayer(playerName, null, true);
                    if (result == BanMutationResult.DATABASE_ERROR) {
                        plugin.getLogger().warning("清理玩家过期封禁失败，玩家将被拦截: " + playerName);
                    }
                } else {
                    return "您仍处于封禁状态，原因：" + ban.getReason() + "，封禁到：" + TimeUtils.timestampToReadable(ban.getTime());
                }
            }
        }

        if (plugin.isFeatureEnabled("ban-ip") && ip != null) {
            BanIpEntry banIp = getMatchingIpBan(ip);
            if (banIp != null) {
                long currentTime = System.currentTimeMillis();
                if (banIp.getTime() <= currentTime) {
                    BanMutationResult result = tryUnbanIp(banIp.getIp(), null, true);
                    if (result == BanMutationResult.DATABASE_ERROR) {
                        plugin.getLogger().warning("清理过期 IP 封禁失败，玩家将被拦截: " + banIp.getIp());
                    }
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

    public BanMutationResult tryUpdateBan(BanEntry entry) {
        return mapWriteResult(db.replaceExistingActiveBan(entry));
    }

    public BanMutationResult tryUpdateIpBan(BanIpEntry entry) {
        if (isPrivateOrReservedIp(entry)) {
            return BanMutationResult.REJECTED_PRIVATE_OR_RESERVED_IP;
        }
        return mapWriteResult(db.replaceExistingActiveIpBan(entry));
    }

    private boolean isPrivateOrReservedIp(BanIpEntry entry) {
        if (!IpMatcher.isPrivateOrReserved(entry.getIp())) {
            return false;
        }
        plugin.getLogger().warning("已阻止封禁私有/保留 IP: " + entry.getIp() + "（staff: " + entry.getStaff() + "）");
        return true;
    }

    private BanMutationResult mapWriteResult(DatabaseManager.WriteResult writeResult) {
        if (writeResult == DatabaseManager.WriteResult.APPLIED) {
            return BanMutationResult.APPLIED;
        }
        if (writeResult == DatabaseManager.WriteResult.NO_CHANGE) {
            return BanMutationResult.NOT_ACTIVE;
        }
        return BanMutationResult.DATABASE_ERROR;
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

    public void saveBanList() {
    }

    public void saveBanIpConfig() {
    }
}

package org.leng.service;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.api.events.LengbanlistUnwarnEvent;
import org.leng.api.events.LengbanlistWarnEvent;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.WarnEntry;
import org.leng.storage.DatabaseManager;
import org.leng.util.ErrorLog;
import org.leng.util.IpMatcher;
import org.leng.util.TimeUtils;
import org.leng.util.Utils;

import java.util.List;
import java.util.stream.Collectors;

public class WarnManager {

    /** 自动封禁使用的操作人标识，同时用于识别一条封禁是不是 LBAC 自动产生的。 */
    public static final String AUTO_STAFF = "LBAC";

    private final Lengbanlist plugin;
    private final DatabaseManager db;
    private volatile WarnPolicy policy = WarnPolicy.defaults();

    public WarnManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        reloadPolicy();
    }

    public void reloadPolicy() {
        this.policy = WarnPolicy.fromConfig(plugin.getConfig(), plugin.getLogger());
    }

    public WarnPolicy getPolicy() {
        return policy;
    }

    public void warnPlayer(String player, String staff, String reason) {
        WarnEntry entry = new WarnEntry(player, staff, System.currentTimeMillis(), reason);
        if (!db.upsertWarning(entry)) {
            plugin.getLogger().warning("警告写入失败: target=" + player + ", staff=" + staff);
        }
        plugin.getAuditManager().log("警告", staff, player, reason);
        Bukkit.getPluginManager().callEvent(new LengbanlistWarnEvent(entry));
        checkAutoBan(player);
        if (plugin.isFeatureEnabled("offline-warn") && !player.contains(".")) {
            Player targetPlayer = plugin.getServer().getPlayer(player);
            if (targetPlayer == null) {
                Player staffPlayer = plugin.getServer().getPlayer(staff);
                if (staffPlayer != null) {
                    Utils.sendMessage(staffPlayer, plugin.getModelManager().getCurrentModel().onWarnOffline(player, reason));
                }
            }
        }
    }

    public int countActiveWarnings(String target) {
        return db.getWarnings(target, true).size();
    }

    public List<WarnEntry> getAllWarnings(String target) {
        return db.getWarnings(target, false);
    }

    public List<String> getWarnedPlayers() {
        return db.getWarnedPlayers();
    }

    public List<WarnEntry> getActiveWarnings(String target) {
        long oneDayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        return db.getWarnings(target, true).stream()
                .filter(e -> e.getTime() > oneDayAgo)
                .collect(Collectors.toList());
    }

    public boolean unwarnPlayer(String target, int warnId, CommandSender actor) {
        List<WarnEntry> playerWarnings = getAllWarnings(target);
        if (warnId > 0 && warnId <= playerWarnings.size()) {
            WarnEntry entry = playerWarnings.get(warnId - 1);
            if (!entry.isRevoked()) {
                entry = entry.revoke();
                db.updateWarningRevoked(entry.getId(), true, target);
                plugin.getAuditManager().log("取消警告", Utils.getSenderName(actor), target, "警告ID: " + entry.getId());
                Bukkit.getPluginManager().callEvent(new LengbanlistUnwarnEvent(target, entry.getId(), Utils.getSenderName(actor)));
                checkUnbanIfNecessary(target);
                return true;
            }
        }
        return false;
    }

    private void checkAutoBan(String player) {
        if (!policy.enabled()) {
            return;
        }
        try {
            String target = normalize(player);
            long now = System.currentTimeMillis();
            List<WarnEntry> validWarnings = validWarnings(target, now);
            if (validWarnings.size() < policy.threshold()) {
                return;
            }
            int triggerCount = policy.triggerCount(validWarnings.size());
            if (triggerCount <= 0) {
                return;
            }

            long banDuration = policy.durationForTrigger(triggerCount);
            long endTime = TimeUtils.calculateEndTime(banDuration);
            String reason = policy.formatReason(validWarnings.size(), triggerCount);
            String durationText = TimeUtils.formatDuration(banDuration);

            if (IpMatcher.isValidIpOrCidr(target)) {
                BanIpEntry existingIpBan = plugin.getBanManager().getBanIpEntry(target);
                if (existingIpBan != null && isLbacBan(existingIpBan.getStaff())) {
                    int previousTrigger = WarnPolicy.parseTrigger(existingIpBan.getReason());
                    if (triggerCount <= previousTrigger) {
                        return;
                    }
                }

                BanIpEntry ipBanEntry = new BanIpEntry(target, AUTO_STAFF, endTime, reason, true);
                BanManager.BanMutationResult result = plugin.getBanManager().tryBanIp(ipBanEntry);
                if (result.isApplied()) {
                    broadcastOrLog(String.format(
                            "§6[LBAC] §e%s §c因累计%d次警告被自动封禁§a%s §6<此封禁由系统决定>",
                            target, validWarnings.size(), durationText));
                } else {
                    logAutomaticMutationFailure("封禁 IP", target, result);
                }
            } else {
                BanEntry existingBan = plugin.getBanManager().getBanEntry(target);
                if (existingBan != null && isLbacBan(existingBan.getStaff())) {
                    int previousTrigger = WarnPolicy.parseTrigger(existingBan.getReason());
                    if (triggerCount <= previousTrigger) {
                        return;
                    }
                }

                BanEntry banEntry = new BanEntry(target, AUTO_STAFF, endTime, reason, true);
                BanManager.BanMutationResult result = plugin.getBanManager().tryBanPlayer(banEntry);
                if (result.isApplied()) {
                    broadcastOrLog(String.format(
                            "§6[LBAC] §e%s §c因累计%d次警告被自动封禁§a%s §6<此封禁由系统决定>",
                            target, validWarnings.size(), durationText));
                } else {
                    logAutomaticMutationFailure("封禁玩家", target, result);
                }
            }
        } catch (Exception e) {
            ErrorLog.record(plugin, "警告自动封禁检查失败", e);
        }
    }

    public void checkUnbanIfNecessary(String player) {
        if (!policy.enabled() || !policy.autoUnban()) {
            return;
        }
        try {
            String target = normalize(player);
            long now = System.currentTimeMillis();
            List<WarnEntry> validWarnings = validWarnings(target, now);
            if (validWarnings.size() >= policy.threshold()) {
                return;
            }

            if (IpMatcher.isValidIpOrCidr(target)) {
                BanIpEntry existingIpBan = plugin.getBanManager().getBanIpEntry(target);
                if (existingIpBan != null && isLbacBan(existingIpBan.getStaff())) {
                    BanManager.BanMutationResult result = plugin.getBanManager()
                            .tryUnbanIp(existingIpBan.getIp(), AUTO_STAFF, false);
                    if (result.isApplied()) {
                        broadcastOrLog(String.format(
                                "§6[LBAC] §e%s §a因警告次数减少至%d次，自动解封",
                                target, validWarnings.size()));
                    } else {
                        logAutomaticMutationFailure("解封 IP", target, result);
                    }
                }
            } else {
                BanEntry existingBan = plugin.getBanManager().getBanEntry(target);
                if (existingBan != null && isLbacBan(existingBan.getStaff())) {
                    BanManager.BanMutationResult result = plugin.getBanManager()
                            .tryUnbanPlayer(existingBan.getTarget(), AUTO_STAFF, false);
                    if (result.isApplied()) {
                        broadcastOrLog(String.format(
                                "§6[LBAC] §e%s §a因警告次数减少至%d次，自动解封",
                                target, validWarnings.size()));
                    } else {
                        logAutomaticMutationFailure("解封玩家", target, result);
                    }
                }
            }
        } catch (Exception e) {
            ErrorLog.record(plugin, "警告自动解封检查失败", e);
        }
    }

    private String normalize(String player) {
        String normalized = IpMatcher.normalizeIpOrCidr(player);
        return normalized != null ? normalized : player;
    }

    private List<WarnEntry> validWarnings(String target, long now) {
        return getAllWarnings(target).stream()
                .filter(entry -> !entry.isRevoked())
                .filter(entry -> policy.withinWindow(entry.getTime(), now))
                .collect(Collectors.toList());
    }

    private boolean isLbacBan(String staff) {
        return AUTO_STAFF.equals(staff);
    }

    private void broadcastOrLog(String message) {
        if (policy.broadcast()) {
            plugin.getServer().broadcastMessage(message);
        } else if (plugin.getLogger() != null) {
            plugin.getLogger().info(ChatColor.stripColor(message));
        }
    }

    private void logAutomaticMutationFailure(String action, String target,
                                             BanManager.BanMutationResult result) {
        if (result == BanManager.BanMutationResult.DATABASE_ERROR) {
            plugin.getLogger().warning("LBAC 自动" + action + "失败: target=" + target
                    + ", result=" + result);
        }
    }

    public long calculateBanDuration(int triggerCount) {
        return policy.durationForTrigger(triggerCount);
    }

    public void loadFromConfig(FileConfiguration config) {
        for (String entry : config.getStringList("warnings")) {
            String[] parts = entry.split(":");
            if (parts.length >= 5) {
                try {
                    WarnEntry warn = new WarnEntry(parts[0], parts[1], Long.parseLong(parts[2]), parts[3]);
                    if (Boolean.parseBoolean(parts[4])) {
                        warn = warn.revoke();
                    }
                    db.upsertWarning(warn);
                } catch (NumberFormatException e) {

                    continue;
                }
            }
        }
    }

}

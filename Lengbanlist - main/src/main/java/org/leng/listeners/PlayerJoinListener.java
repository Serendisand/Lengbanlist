package org.leng.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;
import org.leng.object.BanIpEntry;
import org.leng.object.ReportEntry;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.SaveIP;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.List;

public class PlayerJoinListener implements Listener {

    private final Lengbanlist plugin;

    public PlayerJoinListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.isFeatureEnabled("ban") || plugin.isFeatureEnabled("ban-ip")) {
            plugin.getBanManager().checkBanOnJoin(player);
        }

        SchedulerUtils.runAsync(plugin, () -> prepareJoin(player));

        if (plugin.isFeatureEnabled("vpn-detection") && player.getAddress() != null && player.getAddress().getAddress() != null) {
            String ip = player.getAddress().getAddress().getHostAddress();
            if (ip != null && org.leng.manager.IpAssociationManager.isRealIp(ip)) {
                SchedulerUtils.runAsync(plugin, () -> {
                    boolean isVpn = plugin.getIpAssociationManager().isVpnIp(ip);
                    if (isVpn) {
                        String action = plugin.getConfig().getString("vpn-detection.action", "warn");
                        SchedulerUtils.runTask(plugin, player, () -> handleVpnDetection(player, ip, action));
                    }
                });
            }
        }
    }

    private void prepareJoin(Player player) {
        SaveIP.saveIP(player);

        if (plugin.isFeatureEnabled("ip-association")) {
            List<String> associatedPlayers = plugin.getIpAssociationManager().getOtherPlayersOnIp(player);
            if (!associatedPlayers.isEmpty()) {
                SchedulerUtils.runTask(plugin, player, () -> notifyIpAssociation(player, associatedPlayers));
            }
        }

        if (plugin.isFeatureEnabled("report")) {
            List<ReportEntry> reports = plugin.getReportManager()
                    .getReportsByReporterWithStatus(player.getName(), "受理中");
            if (!reports.isEmpty()) {
                SchedulerUtils.runTask(plugin, player, () -> notifyHandledReports(player, reports));
            }
        }

        if (plugin.isFeatureEnabled("offline-warn") && plugin.getConfig().getBoolean("offline-warn.notify-on-join", true)) {
            int count = plugin.getWarnManager().countActiveWarnings(player.getName());
            if (count > 0 && player.isOnline()) {
                String msg = plugin.getModelManager().getCurrentModel().getPendingWarningsNotice(count);
                SchedulerUtils.runTask(plugin, player, () -> Utils.sendMessage(player, msg));
            }
        }
    }

    private void notifyIpAssociation(Player joined, List<String> associatedPlayers) {
        String message = plugin.prefix() + "§e玩家 §f" + joined.getName() + " §e的 IP 曾由以下玩家使用: §f"
                + String.join("§7, §f", associatedPlayers);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("lengbanlist.check") || online.isOp()) {
                Utils.sendMessage(online, "§7[§cIP关联§7] §f" + joined.getName() + " §e的 IP 存在关联账号");
            }
        }
        Bukkit.getConsoleSender().sendMessage("§7[§cIP关联§7] " + message);
    }

    private void notifyHandledReports(Player reporter, List<ReportEntry> reports) {
        Utils.sendMessage(reporter, plugin.prefix() + "§7——————————");
        Utils.sendMessage(reporter, plugin.prefix() + "§a你的举报已被处理。");
        reporter.spigot().sendMessage(
                new net.md_5.bungee.api.chat.TextComponent(plugin.prefix() + " "),
                Utils.clickableText("§a【我已阅读】", "/report ack " + reports.get(0).getId())
        );
        Utils.sendMessage(reporter, plugin.prefix() + "§7——————————");
    }

    private void handleVpnDetection(Player player, String ip, String action) {
        String prefix = plugin.prefix();
        switch (action.toLowerCase()) {
            case "ban":
                String banDurationStr = plugin.getConfig().getString("vpn-detection.ban-duration", "7d");
                String banReason = plugin.getConfig().getString("vpn-detection.ban-reason", "使用代理/VPN 登录");
                long duration = TimeUtils.parseTime(banDurationStr);
                if (duration <= 0) duration = TimeUtils.daysToMillis(7);
                long endTime = TimeUtils.calculateEndTime(duration);
                BanManager.BanMutationResult banResult = plugin.getBanManager().tryBanIp(
                        new BanIpEntry(ip, "VPN-Detection", endTime, banReason, false));
                if (!banResult.isApplied()) {
                    plugin.getLogger().warning("VPN 检测自动封禁失败: player=" + player.getName()
                            + ", ip=" + ip + ", result=" + banResult);
                    break;
                }
                player.kickPlayer("§c检测到代理/VPN 连接\n\n§f" + banReason + "\n§e请联系管理员解决");
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission("lengbanlist.check") || online.isOp()) {
                        online.sendMessage("§7[§cVPN检测§7] " + prefix + "§c" + player.getName() + " §e因使用代理/VPN 已被自动封禁");
                    }
                }
                Bukkit.getConsoleSender().sendMessage("§7[§cVPN检测§7] " + player.getName() + " 因使用代理/VPN 已被自动封禁 (IP: " + ip + ")");
                break;
            case "kick":
                String kickMsg = plugin.getConfig().getString("vpn-detection.kick-message", "请关闭代理/VPN后重新加入");
                player.kickPlayer("§c检测到代理/VPN 连接\n\n§f" + kickMsg);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission("lengbanlist.check") || online.isOp()) {
                        online.sendMessage("§7[§cVPN检测§7] " + prefix + "§e" + player.getName() + " §e因使用代理/VPN 被踢出");
                    }
                }
                Bukkit.getConsoleSender().sendMessage("§7[§cVPN检测§7] " + player.getName() + " 因使用代理/VPN 被踢出 (IP: " + ip + ")");
                break;
            default:
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission("lengbanlist.check") || online.isOp()) {
                        online.sendMessage("§7[§cVPN检测§7] " + prefix + "§e" + player.getName() + " §c可能正在使用代理/VPN 登录");
                    }
                }
                Bukkit.getConsoleSender().sendMessage("§7[§cVPN检测§7] " + player.getName() + " 可能正在使用代理/VPN (IP: " + ip + ")");
                break;
        }
    }
}

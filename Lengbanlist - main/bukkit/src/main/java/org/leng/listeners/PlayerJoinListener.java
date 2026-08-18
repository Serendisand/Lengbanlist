package org.leng.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;
import org.leng.manager.IpAssociationManager;
import org.leng.manager.ReportManager;
import org.leng.object.BanIpEntry;
import org.leng.object.ReportEntry;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.SaveIP;
import org.leng.utils.TimeUtils;

import java.util.List;
import java.util.stream.Collectors;

public class PlayerJoinListener implements Listener {
    private final Lengbanlist plugin;

    public PlayerJoinListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SaveIP.saveIP(player);
        String playerIp = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        if (playerIp != null) {
            plugin.getIpAssociationManager().recordLogin(player.getName(), playerIp);
        }

        if (plugin.isFeatureEnabled("ban") || plugin.isFeatureEnabled("ban-ip")) {
            String kickMessage = plugin.getBanManager().checkBanOnJoin(player.getName(), playerIp);
            if (kickMessage != null) {
                SchedulerUtils.runTask(plugin, player, () -> player.kickPlayer(kickMessage));
                return;
            }
        }

        if (plugin.isFeatureEnabled("ip-association")) {
            if (playerIp != null && plugin.getIpAssociationManager().hasSuspiciousLogin(player.getName(), playerIp)) {
                List<String> associatedPlayers = plugin.getIpAssociationManager().getSuspiciousLoginDetails(player.getName(), playerIp);
                String msg = plugin.prefix() + "§e玩家 §f" + player.getName() + " §e的 IP 曾由以下玩家使用: §f" + String.join("§7, §f", associatedPlayers);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission("lengbanlist.check") || online.isOp()) {
                        online.sendMessage("§7[§cIP关联§7] §f" + player.getName() + " §e的 IP 存在关联账号");
                    }
                }
                Bukkit.getConsoleSender().sendMessage("§7[§cIP关联§7] " + msg);
            }
        }

        if (plugin.isFeatureEnabled("vpn-detection")) {
            String ip = playerIp;
            if (IpAssociationManager.isRealIp(ip)) {
                SchedulerUtils.runAsync(plugin, () -> {
                    boolean isVpn = plugin.getIpAssociationManager().isVpnIp(ip);
                    if (isVpn) {
                        String action = plugin.getConfig().getString("vpn-detection.action", "warn");
                        SchedulerUtils.runTask(plugin, player, () -> handleVpnDetection(player, ip, action));
                    }
                });
            }
        }

        if (plugin.isFeatureEnabled("report")) {
            ReportManager reportManager = plugin.getReportManager();
            List<ReportEntry> reports = reportManager.getPendingReports().stream()
                    .filter(report -> report.getReporter().equals(player.getName()))
                    .filter(report -> !"未处理".equals(report.getStatus()))
                    .collect(Collectors.toList());

            if (!reports.isEmpty()) {
                player.sendMessage(plugin.prefix() + "§7——————————");
                player.sendMessage(plugin.prefix() + "§a你的举报已被处理。");
                player.spigot().sendMessage(
                    new net.md_5.bungee.api.chat.TextComponent(plugin.prefix() + " "),
                    org.leng.utils.Utils.clickableText("§a【我已阅读】", "/report ack " + reports.get(0).getId())
                );
                player.sendMessage(plugin.prefix() + "§7——————————");
            }
        }
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
                if (banResult.isApplied()) {
                    player.kickPlayer("§c检测到代理/VPN 连接\n\n§f" + banReason + "\n§e请联系管理员解决");
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.hasPermission("lengbanlist.check") || online.isOp()) {
                            online.sendMessage("§7[§cVPN检测§7] " + prefix + "§c" + player.getName() + " §e因使用代理/VPN 已被自动封禁");
                        }
                    }
                    Bukkit.getConsoleSender().sendMessage("§7[§cVPN检测§7] " + player.getName() + " 因使用代理/VPN 已被自动封禁 (IP: " + ip + ")");
                } else if (banResult == BanManager.BanMutationResult.DATABASE_ERROR) {
                    plugin.getLogger().warning("VPN 检测自动封禁失败: player=" + player.getName()
                            + ", ip=" + ip + ", result=" + banResult);
                }
                break;
            case "kick":
                String kickMsg = plugin.getConfig().getString("vpn-detection.kick-message", "请关闭代理/VPN后重新加入");
                player.kickPlayer("§c检测到代理/VPN 连接\n\n§f" + kickMsg);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission("lengbanlist.check") || online.isOp()) {
                        online.sendMessage("§7[§cVPN检测§7] " + prefix + "§e" + player.getName() + " §e因使用代理/VPN 已被踢出");
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

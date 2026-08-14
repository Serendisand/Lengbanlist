package org.leng.manager;

import org.leng.platform.LengbanlistPlatform;
import org.leng.platform.MessageSink;

public class SyncManager {
    private final LengbanlistPlatform plugin;

    public SyncManager(LengbanlistPlatform plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getDatabaseManager().isMySql();
    }

    public boolean isAutoSyncEnabled() {
        return plugin.getConfigBoolean("sync.auto-sync", true);
    }

    public long getAutoSyncIntervalSeconds() {
        return Math.max(10L, plugin.getConfigInt("sync.interval-seconds", 60));
    }

    /** 定时跨服同步由平台调度（Bukkit 用 SchedulerUtils，Fabric 用线程循环），此处仅提供单次同步。 */
    public void execute(MessageSink sender) {
        if (!isAvailable()) {
            sender.sendMessage(plugin.prefix() + "§c当前为 SQLite 数据库，仅 MySQL 共享数据库支持跨服同步。");
            return;
        }
        performSync(sender);
    }

    private void performSync(MessageSink sender) {
        plugin.runAsync(() -> {
            SyncResult result = new SyncResult();
            result.bans = plugin.getDatabaseManager().getAllActiveBans().size();
            result.ipBans = plugin.getDatabaseManager().getIpBans().size();
            result.mutes = plugin.getDatabaseManager().getMutes().size();
            result.warnings = plugin.getDatabaseManager().getWarnedPlayers().size();
            boolean muteCacheReloaded = plugin.getMuteManager().reloadMuteCache();
            if (sender == null) {
                if (!muteCacheReloaded) {
                    plugin.getLogger().warning("定时跨服同步未完成：禁言缓存刷新失败");
                } else {
                    plugin.getLogger().info("定时跨服同步完成：封禁 " + result.bans + " 条 / IP封禁 " + result.ipBans + " 条 / 禁言 " + result.mutes + " 条 / 警告 " + result.warnings + " 条");
                }
                return;
            }
            plugin.runSync(() -> {
                if (!muteCacheReloaded) {
                    sender.sendMessage(plugin.prefix() + "§c跨服同步未完成：禁言缓存刷新失败，请稍后重试。");
                    return;
                }
                sender.sendMessage(plugin.prefix() + "§a跨服同步完成：封禁 " + result.bans + " 条 / IP封禁 " + result.ipBans + " 条 / 禁言 " + result.mutes + " 条 / 警告 " + result.warnings + " 条，缓存已刷新");
            });
        });
    }

    public static class SyncResult {
        public int bans;
        public int ipBans;
        public int mutes;
        public int warnings;
    }
}

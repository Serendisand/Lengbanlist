package org.leng.manager;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.Utils;

public class SyncManager {
    private final Lengbanlist plugin;
    private SchedulerUtils.SchedulerTask autoSyncTask;

    public SyncManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getDatabaseManager().isMySql();
    }

    public boolean isAutoSyncEnabled() {
        return plugin.getConfig().getBoolean("sync.auto-sync", true);
    }

    public long getAutoSyncIntervalSeconds() {
        return Math.max(10L, plugin.getConfig().getLong("sync.interval-seconds", 60L));
    }

    public void startAutoSync() {
        stopAutoSync();
        if (!isAvailable() || !isAutoSyncEnabled()) {
            return;
        }
        long intervalTicks = getAutoSyncIntervalSeconds() * 20L;
        autoSyncTask = SchedulerUtils.runTaskTimerAsynchronously(plugin, () -> performSync(null), intervalTicks, intervalTicks);
        plugin.getLogger().info("定时跨服同步已启动，间隔 " + getAutoSyncIntervalSeconds() + " 秒");
    }

    public void stopAutoSync() {
        if (autoSyncTask != null) {
            autoSyncTask.cancel();
            autoSyncTask = null;
        }
    }

    public void execute(CommandSender sender) {
        if (!isAvailable()) {
            Utils.sendMessage(sender, plugin.prefix() + "§c当前为 SQLite 数据库，仅 MySQL 共享数据库支持跨服同步。");
            return;
        }
        performSync(sender);
    }

    private void performSync(CommandSender sender) {
        SchedulerUtils.runAsync(plugin, () -> {
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
            SchedulerUtils.runTask(plugin, () -> {
                if (!muteCacheReloaded) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c跨服同步未完成：禁言缓存刷新失败，请稍后重试。");
                    return;
                }
                Utils.sendMessage(sender, plugin.prefix() + "§a跨服同步完成：封禁 " + result.bans + " 条 / IP封禁 " + result.ipBans + " 条 / 禁言 " + result.mutes + " 条 / 警告 " + result.warnings + " 条，缓存已刷新");
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

package org.leng.manager;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.object.SyncEvent;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class SyncManager {

    private static final int EVENT_BATCH_SIZE = 500;

    private static final long CURSOR_UNINITIALIZED = -1L;

    private static final long ERROR_LOG_INTERVAL_MILLIS = 30000L;

    private final Lengbanlist plugin;
    private SchedulerUtils.SchedulerTask pollTask;
    private SchedulerUtils.SchedulerTask autoSyncTask;

    private volatile long cursor = CURSOR_UNINITIALIZED;
    private volatile long lastPollAt;
    private volatile long lastPollMillis;
    private volatile long appliedEvents;
    private volatile String lastError = "";
    private volatile long lastErrorLogAt;

    public SyncManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return plugin.getDatabaseManager().isNetworkDatabase();
    }

    public boolean isAutoSyncEnabled() {
        return booleanSetting("sync.auto-sync", true);
    }

    public boolean isEventPollEnabled() {
        return booleanSetting("sync.event-poll", true);
    }

    public long getPollSeconds() {
        return clamp(longSetting("sync.poll-seconds", 1L), 1L, 60L);
    }

    public long getAutoSyncIntervalSeconds() {
        return Math.max(10L, longSetting("sync.interval-seconds", 60L));
    }

    private long longSetting(String key, long fallback) {
        if (plugin.getStorageConfig() != null && plugin.getStorageConfig().contains(key)) {
            return plugin.getStorageConfig().getLong(key, fallback);
        }
        return plugin.getConfig().getLong(key, fallback);
    }

    private boolean booleanSetting(String key, boolean fallback) {
        if (plugin.getStorageConfig() != null && plugin.getStorageConfig().contains(key)) {
            return plugin.getStorageConfig().getBoolean(key, fallback);
        }
        return plugin.getConfig().getBoolean(key, fallback);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public void startAutoSync() {
        stopAutoSync();
        if (!isAvailable()) {
            return;
        }
        cursor = CURSOR_UNINITIALIZED;
        if (isEventPollEnabled()) {
            long intervalTicks = getPollSeconds() * 20L;
            pollTask = SchedulerUtils.runTaskTimerAsynchronously(plugin, this::pollEvents, intervalTicks, intervalTicks);
            plugin.getLogger().info("跨服事件同步已启动，拉取间隔 " + getPollSeconds() + " 秒");
        }
        if (isAutoSyncEnabled()) {
            long intervalTicks = getAutoSyncIntervalSeconds() * 20L;
            autoSyncTask = SchedulerUtils.runTaskTimerAsynchronously(plugin, () -> performSync(null), intervalTicks, intervalTicks);
            plugin.getLogger().info("跨服全量兜底同步已启动，间隔 " + getAutoSyncIntervalSeconds() + " 秒");
        }
    }

    public void stopAutoSync() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        if (autoSyncTask != null) {
            autoSyncTask.cancel();
            autoSyncTask = null;
        }
    }

    void pollEvents() {
        try {
            DatabaseManager db = plugin.getDatabaseManager();
            if (cursor == CURSOR_UNINITIALIZED) {
                cursor = db.getLatestSyncEventId();
                lastPollAt = System.currentTimeMillis();
                return;
            }
            long started = System.currentTimeMillis();
            Set<String> muteTargets = new HashSet<>();
            Set<String> warnPlayers = new HashSet<>();
            boolean bansChanged = false;
            boolean freezeChanged = false;
            boolean warnsAll = false;
            long newest = cursor;
            int processed = 0;
            int fetched;
            do {
                List<SyncEvent> events = db.getSyncEventsAfter(newest, EVENT_BATCH_SIZE);
                fetched = events.size();
                for (SyncEvent event : events) {
                    switch (event.scope()) {
                        case SyncEvent.SCOPE_BAN:
                        case SyncEvent.SCOPE_IP_BAN:
                            bansChanged = true;
                            break;
                        case SyncEvent.SCOPE_MUTE:
                            muteTargets.add(event.target());
                            break;
                        case SyncEvent.SCOPE_FREEZE:
                            freezeChanged = true;
                            break;
                        case SyncEvent.SCOPE_WARN:
                            if (SyncEvent.TARGET_ALL.equals(event.target())) {
                                warnsAll = true;
                            } else {
                                warnPlayers.add(event.target());
                            }
                            break;
                        default:
                            break;
                    }
                    newest = event.id();
                    processed++;
                }
            } while (fetched >= EVENT_BATCH_SIZE);

            applyInvalidations(bansChanged, freezeChanged, warnsAll, muteTargets, warnPlayers);
            if (newest != cursor) {
                appliedEvents += processed;
                cursor = newest;
            }
            lastPollAt = System.currentTimeMillis();
            lastPollMillis = lastPollAt - started;
            lastError = "";
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "" : t.getMessage());
            long now = System.currentTimeMillis();
            if (now - lastErrorLogAt >= ERROR_LOG_INTERVAL_MILLIS) {
                lastErrorLogAt = now;
                plugin.getLogger().log(Level.WARNING, "跨服事件拉取失败，稍后自动重试（游标停留在 " + cursor + "）：" + lastError);
            }
        }
    }

    private void applyInvalidations(boolean bansChanged, boolean freezeChanged, boolean warnsAll,
                                    Set<String> muteTargets, Set<String> warnPlayers) {
        if (bansChanged) {
            plugin.getDatabaseManager().reloadBanCache();
        }
        if (warnsAll) {
            plugin.getDatabaseManager().reloadWarnCache();
        } else if (!warnPlayers.isEmpty()) {
            for (String player : warnPlayers) {
                plugin.getDatabaseManager().invalidateWarn(player);
            }
        }
        if (!muteTargets.isEmpty() && plugin.getMuteManager() != null) {
            for (String target : muteTargets) {
                plugin.getMuteManager().invalidate(target);
            }
        }
        if (freezeChanged && plugin.getFreezeManager() != null) {
            plugin.getFreezeManager().reload();
        }
    }

    public void execute(CommandSender sender) {
        if (!isAvailable()) {
            Utils.sendMessage(sender, plugin.prefix() + "§c当前为单机 SQLite 数据库，仅共享数据库（MySQL / MariaDB / PostgreSQL）支持跨服同步。");
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
            result.freezes = plugin.getDatabaseManager().loadFreezes().size();

            plugin.getDatabaseManager().reloadBanCache();
            plugin.getDatabaseManager().reloadWarnCache();
            boolean freezeCacheReloaded = plugin.getFreezeManager().reload();
            boolean muteCacheReloaded = plugin.getMuteManager().reloadMuteCache();
            if (sender == null) {
                if (!muteCacheReloaded) {
                    plugin.getLogger().warning("定时跨服同步未完成：禁言缓存刷新失败");
                } else {
                    plugin.getLogger().info("定时跨服同步完成：封禁 " + result.bans + " 条 / IP封禁 " + result.ipBans + " 条 / 禁言 " + result.mutes + " 条 / 警告 " + result.warnings + " 条 / 冻结 " + result.freezes + " 条");
                }
                return;
            }
            SchedulerUtils.runTask(plugin, () -> {
                if (!muteCacheReloaded) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c跨服同步未完成：禁言缓存刷新失败，请稍后重试。");
                    return;
                }
                Utils.sendMessage(sender, plugin.prefix() + "§a跨服同步完成：封禁 " + result.bans + " 条 / IP封禁 " + result.ipBans + " 条 / 禁言 " + result.mutes + " 条 / 警告 " + result.warnings + " 条 / 冻结 " + result.freezes + " 条，缓存已刷新");
            });
        });
    }

    public long getCursor() {
        return cursor;
    }

    public long getLastPollAt() {
        return lastPollAt;
    }

    public long getLastPollMillis() {
        return lastPollMillis;
    }

    public long getAppliedEvents() {
        return appliedEvents;
    }

    public String getLastError() {
        return lastError;
    }

    public static class SyncResult {
        public int bans;
        public int ipBans;
        public int mutes;
        public int warnings;
        public int freezes;
    }
}

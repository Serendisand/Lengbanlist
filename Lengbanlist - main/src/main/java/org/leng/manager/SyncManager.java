package org.leng.manager;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.IpMatcher;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.SyncChannel;
import org.leng.utils.Utils;

public class SyncManager {
    private final Lengbanlist plugin;

    public SyncManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void syncPlayer(String target) {
        if (target == null) return;
        plugin.getMuteManager().refreshPlayerMute(target);
    }

    public void handleSync(byte type, String target) {
        if (target == null || target.isEmpty()) return;
        switch (type) {
            case SyncChannel.TYPE_PLAYER_BAN:
                SchedulerUtils.runTask(plugin, () -> plugin.getBanManager().kickOnlineIfBanned(target, false));
                break;
            case SyncChannel.TYPE_IP_BAN:
                SchedulerUtils.runTask(plugin, () -> plugin.getBanManager().kickOnlineIfBanned(target, true));
                break;
            case SyncChannel.TYPE_PLAYER_MUTE:
                plugin.getMuteManager().refreshPlayerMute(target);
                break;
            case SyncChannel.TYPE_IP_MUTE:
                plugin.getMuteManager().refreshPlayerMute(target);
                if (!IpMatcher.isCidr(target)) {
                    plugin.getMuteManager().registerIpMuteFallback(target);
                }
                break;
            case SyncChannel.TYPE_PLAYER_UNBAN:
            case SyncChannel.TYPE_IP_UNBAN:
                break;
            case SyncChannel.TYPE_PLAYER_UNMUTE:
                plugin.getMuteManager().refreshPlayerMute(target);
                break;
            default:
                plugin.getLogger().warning("收到未知类型的跨服同步消息 (type=" + type + ", target=" + target + ")，已忽略");
                break;
        }
    }

    public boolean isAvailable() {
        return plugin.getDatabaseManager().isMySql();
    }

    public void execute(CommandSender sender) {
        if (!isAvailable()) {
            Utils.sendMessage(sender, plugin.prefix() + "§c当前为 SQLite 数据库，仅 MySQL 共享数据库支持跨服同步。");
            return;
        }
        SchedulerUtils.runAsync(plugin, () -> {
            SyncResult result = new SyncResult();
            result.bans = plugin.getDatabaseManager().getAllActiveBans().size();
            result.ipBans = plugin.getDatabaseManager().getIpBans().size();
            result.mutes = plugin.getDatabaseManager().getMutes().size();
            result.warnings = plugin.getDatabaseManager().getWarnedPlayers().size();
            plugin.getMuteManager().reloadMuteCache();
            SchedulerUtils.runTask(plugin, () -> Utils.sendMessage(sender, plugin.prefix() + "§a跨服同步完成：封禁 " + result.bans + " 条 / IP封禁 " + result.ipBans + " 条 / 禁言 " + result.mutes + " 条 / 警告 " + result.warnings + " 条，缓存已刷新"));
        });
    }

    public static class SyncResult {
        public int bans;
        public int ipBans;
        public int mutes;
        public int warnings;
    }
}

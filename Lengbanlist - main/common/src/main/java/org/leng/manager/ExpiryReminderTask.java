package org.leng.manager;

import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.MuteEntry;
import org.leng.platform.LengbanlistPlatform;
import org.leng.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class ExpiryReminderTask implements Runnable {
    private final LengbanlistPlatform plugin;

    public ExpiryReminderTask(LengbanlistPlatform plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        long leadTime = plugin.getConfigInt("expiry-reminder.lead-time", 600) * 1000L;
        Model model = ModelManager.getCurrentModel();
        List<String> messages = new ArrayList<>();
        for (BanEntry ban : plugin.getDatabaseManager().getAllActiveBans()) {
            long endTime = ban.getTime();
            if (endTime != Long.MAX_VALUE && endTime - now <= leadTime) {
                messages.add(model.getExpiryReminder("封禁", ban.getTarget(), TimeUtils.getRemainingTime(endTime)));
            }
        }
        for (MuteEntry mute : plugin.getDatabaseManager().getAllMutes()) {
            long endTime = mute.getTime();
            if (endTime != Long.MAX_VALUE && endTime - now <= leadTime) {
                messages.add(model.getExpiryReminder("禁言", mute.getTarget(), TimeUtils.getRemainingTime(endTime)));
            }
        }
        if (messages.isEmpty()) {
            return;
        }
        plugin.runSync(() -> notifyStaff(messages));
    }

    /** 通知在线且有审计权限的玩家（由平台实现具体发送）。 */
    protected void notifyStaff(List<String> messages) {
        // 平台层通过覆盖此方法或注册回调实现；默认仅记录日志
        for (String msg : messages) {
            plugin.getLogger().info(msg);
        }
    }
}

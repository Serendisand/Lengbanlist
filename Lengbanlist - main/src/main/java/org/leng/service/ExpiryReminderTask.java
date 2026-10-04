package org.leng.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.MuteEntry;
import org.leng.util.SchedulerUtils;
import org.leng.util.TimeUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.leng.integration.ModelManager;

public class ExpiryReminderTask implements Runnable {
    private final Lengbanlist plugin;

    private final Map<String, Long> announced = new ConcurrentHashMap<>();

    public ExpiryReminderTask(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        long leadTime = plugin.getConfig().getInt("expiry-reminder.lead-time", 600) * 1000L;
        Model model = ModelManager.getCurrentModel();
        boolean english = TimeUtils.isEnglishLocale();
        List<String> messages = new ArrayList<>();

        long deadline = now + leadTime;
        for (BanEntry ban : plugin.getDatabaseManager().getBansExpiringBefore(deadline)) {
            long endTime = ban.getTime();
            if (endTime != Long.MAX_VALUE && endTime - now <= leadTime && claim("封禁", ban.getTarget(), endTime)) {
                messages.add(model.getExpiryReminder(english ? "ban" : "封禁", ban.getTarget(),
                        TimeUtils.formatRemaining(endTime, english)));
            }
        }
        for (MuteEntry mute : plugin.getDatabaseManager().getMutesExpiringBefore(deadline)) {
            long endTime = mute.getTime();
            if (endTime != Long.MAX_VALUE && endTime - now <= leadTime && claim("禁言", mute.getTarget(), endTime)) {
                messages.add(model.getExpiryReminder(english ? "mute" : "禁言", mute.getTarget(),
                        TimeUtils.formatRemaining(endTime, english)));
            }
        }
        if (messages.isEmpty()) {
            return;
        }
        SchedulerUtils.runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("lengbanlist.audit")) {
                    for (String msg : messages) {
                        player.sendMessage(msg);
                    }
                }
            }
        });
    }

    private boolean claim(String kind, String target, long endTime) {
        String key = kind + "|" + target + "|" + endTime;
        long now = System.currentTimeMillis();
        long keepUntil = Math.max(endTime, now + 60_000L);
        boolean fresh = announced.putIfAbsent(key, keepUntil) == null;
        Iterator<Map.Entry<String, Long>> it = announced.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= now) {
                it.remove();
            }
        }
        return fresh;
    }
}

package org.leng.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;
import org.leng.util.SchedulerUtils;
import org.leng.util.TimeUtils;
import org.leng.util.Utils;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener implements Listener {
    private static final long PENDING_CHAT_TTL_MS = 5 * 60 * 1000L;
    private static final int PENDING_CHAT_LIMIT = 256;

    private final Lengbanlist plugin;
    private final Map<String, Integer> badWordCount = new ConcurrentHashMap<>();
    private final Map<String, PendingChat> pendingChats = new ConcurrentHashMap<>();
    private static final Pattern MEOW_REPEAT = Pattern.compile(".*喵.*喵.*");

    private record PendingChat(String message, long at) {}

    private record FilterConfig(Pattern badWordPattern, int muteThreshold) {}

    private volatile FilterConfig filterConfig;

    public ChatListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void invalidateFilterCache() {
        filterConfig = null;
    }

    private FilterConfig filterConfig() {
        FilterConfig cached = filterConfig;
        if (cached == null) {
            // 把所有违禁词合成一条正则：每条聊天只跑一次匹配，
            // 而不是逐词 contains/replace（词多时是 O(词数 × 消息长度)）。
            StringBuilder alternation = new StringBuilder();
            for (String badWord : plugin.getChatConfig().getStringList("bad-words")) {
                if (badWord == null || badWord.isEmpty()) {
                    continue;
                }
                if (alternation.length() > 0) {
                    alternation.append('|');
                }
                alternation.append(Pattern.quote(badWord));
            }
            cached = new FilterConfig(
                    alternation.length() == 0 ? null : Pattern.compile(alternation.toString()),
                    plugin.getChatConfig().getInt("mute-threshold", 3));
            filterConfig = cached;
        }
        return cached;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (player.hasMetadata("lengbanlist-action")) {
            event.setCancelled(true);
            String wizardMessage = event.getMessage();
            SchedulerUtils.runTask(plugin, player, () -> plugin.getWizardManager().handle(player, wizardMessage));
            return;
        }

        String message = event.getMessage();

        if (plugin.isFeatureActive("mute") && plugin.getMuteManager().isPlayerMuted(player)) {
            event.setCancelled(true);
            player.sendMessage(plugin.prefix() + "§c你不准说话喵！");
            return;
        }

        if (!plugin.isFeatureEnabled("chat-filter")) {
            return;
        }

        FilterConfig filter = filterConfig();
        Pattern badWordPattern = filter.badWordPattern();
        int muteThreshold = filter.muteThreshold();

        boolean containsBadWord = false;
        if (badWordPattern != null) {
            Matcher matcher = badWordPattern.matcher(message);
            StringBuilder replaced = new StringBuilder(message.length());
            while (matcher.find()) {
                containsBadWord = true;
                matcher.appendReplacement(replaced,
                        Matcher.quoteReplacement("喵".repeat(matcher.group().length())));
            }
            if (containsBadWord) {
                matcher.appendTail(replaced);
                message = replaced.toString();
            }
        }

        if (containsBadWord) {
            int violations = badWordCount.merge(player.getName(), 1, Integer::sum);
            final boolean triggerMute = violations >= muteThreshold;
            final long muteDurationMillis;
            final String muteReason;
            if (triggerMute) {
                switch (violations - muteThreshold + 3) {
                    case 3: muteDurationMillis = TimeUtils.hoursToMillis(1); break;
                    case 4: muteDurationMillis = TimeUtils.hoursToMillis(12); break;
                    case 5: muteDurationMillis = TimeUtils.daysToMillis(1); break;
                    default: muteDurationMillis = TimeUtils.daysToMillis(7); break;
                }
                muteReason = "多次使用违禁词（第 " + violations + " 次触发）";
                badWordCount.remove(player.getName());
            } else {
                muteDurationMillis = 0L;
                muteReason = null;
            }

            player.sendMessage(plugin.prefix() + "§c警告：你的消息中包含违禁词，已被替换为「喵」。");
            // 禁言写入、警告写入和自动封禁检查都要在主线程做；聊天事件本身是异步的。
            SchedulerUtils.runTask(plugin, player, () -> {
                if (triggerMute) {
                    plugin.getMuteManager().mutePlayer(new MuteEntry(player.getName(), "System",
                            TimeUtils.calculateEndTime(muteDurationMillis), muteReason));
                    player.sendMessage(plugin.prefix() + "§c你因多次使用违禁词被自动禁言 "
                            + TimeUtils.formatDuration(muteDurationMillis) + "！");
                }
                plugin.getWarnManager().warnPlayer(player.getName(), "System", "使用违禁词");
            });
        }

        if (!containsBadWord && MEOW_REPEAT.matcher(message).find()) {
            rememberPendingChat(player.getName(), message);
            SchedulerUtils.runTask(plugin, () -> {
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.isOp()) {
                        Utils.sendMessage(admin,
                                Utils.clickableText("【正常】", "/allowmsg " + player.getName()),
                                Utils.clickableText("【违规】", "/warnmsg " + player.getName())
                        );
                    }
                }
            });
            event.setCancelled(true);
            return;
        }

        event.setMessage(message);
    }

    /** 暂存被拦截的聊天消息，等管理员点【正常】时原样放行。 */
    public void rememberPendingChat(String playerName, String message) {
        if (playerName == null || message == null || message.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (pendingChats.size() >= PENDING_CHAT_LIMIT) {
            pendingChats.entrySet().removeIf(entry -> now - entry.getValue().at() > PENDING_CHAT_TTL_MS);
            if (pendingChats.size() >= PENDING_CHAT_LIMIT) {
                pendingChats.clear();
            }
        }
        pendingChats.put(playerName.toLowerCase(Locale.ROOT), new PendingChat(message, now));
    }

    /**
     * 取走并清除待放行消息。
     *
     * @return 消息正文；不存在或超过 5 分钟返回 {@code null}
     */
    public String takePendingChat(String playerName) {
        if (playerName == null) {
            return null;
        }
        PendingChat pending = pendingChats.remove(playerName.toLowerCase(Locale.ROOT));
        if (pending == null || System.currentTimeMillis() - pending.at() > PENDING_CHAT_TTL_MS) {
            return null;
        }
        return pending.message();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        badWordCount.remove(event.getPlayer().getName());
        pendingChats.remove(event.getPlayer().getName().toLowerCase(Locale.ROOT));
    }
}

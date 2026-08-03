package org.leng;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;

import java.util.List;

public class BroadCastBanCountMessage implements Runnable {
    @Override
    public void run() {
        if (!Lengbanlist.getInstance().isEnabled()) {
            return;
        }

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        try {
                final List<String> messages = Lengbanlist.getInstance().getBroadcastFC().getStringList("messages");
                String template;
                if (messages != null && !messages.isEmpty()) {
                    template = messages.get(new java.util.Random().nextInt(messages.size()));
                } else {
                    template = Lengbanlist.getInstance().getBroadcastFC().getString("default-message");
                }
                if (template == null || template.isEmpty()) {
                    return;
                }

                int banCount = Lengbanlist.getInstance().getBanManager().getBanList().size();
                int banIpCount = Lengbanlist.getInstance().getBanManager().getBanIpList().size();
                int totalBans = banCount + banIpCount;

                final String replacedMessage = template
                        .replace("%s", String.valueOf(banCount))
                        .replace("%i", String.valueOf(banIpCount))
                        .replace("%t", String.valueOf(totalBans));

                for (Player player : Bukkit.getOnlinePlayers()) {
                    org.leng.utils.SchedulerUtils.runTask(Lengbanlist.getInstance(), player, () -> {
                        TextComponent mainMessage = new TextComponent(ChatColor.translateAlternateColorCodes('&', replacedMessage));
                        mainMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new ComponentBuilder("§a绳§b之§c于§d法§e！").create()));

                        TextComponent clickableComponent = new TextComponent("§f【§b点§c击§d查§e看§f】");
                        clickableComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lban list"));
                        clickableComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new ComponentBuilder("§a看看封禁列表§bawa").create()));

                        player.spigot().sendMessage(mainMessage, clickableComponent);
                    });
                }
        } catch (Exception e) {
            Lengbanlist.getInstance().getLogger().warning("广播任务执行出错: " + e.getMessage());
        }
    }
}

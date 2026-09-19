package org.leng.manager;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.Utils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BroadCastManager implements Runnable {

    private static final String HOVER_TITLE = "§a绳§b之§c于§d法§e！";
    private static final String BUTTON_TEXT = "§f【§b点§c击§d查§e看§f】";
    private static final String BUTTON_HOVER = "§a看看封禁列表§bawa";

    private final Lengbanlist plugin;

    public BroadCastManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.isEnabled() || Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        try {
            broadcast(null);
        } catch (Exception e) {
            plugin.getLogger().warning("广播任务执行出错: " + e.getMessage());
        }
    }

    public boolean broadcastNow(CommandSender sender) {
        return broadcast(sender);
    }

    private boolean broadcast(CommandSender sender) {
        String template = pickTemplate();
        if (template == null || template.isEmpty()) {
            if (sender != null) {
                Utils.sendMessage(sender, plugin.prefix() + "§c广播消息未配置，请在 broadcast.yml 中设置 default-message。");
            }
            return false;
        }

        final String replaced = render(template);
        for (Player player : Bukkit.getOnlinePlayers()) {
            SchedulerUtils.runTask(plugin, player, () -> send(player, replaced));
        }
        if (sender != null) {
            Utils.sendMessage(sender, plugin.prefix() + "§a已广播当前封禁人数。");
        }
        return true;
    }

    private String pickTemplate() {
        List<String> messages = plugin.getBroadcastFC().getStringList("messages");
        return messages.isEmpty()
                ? plugin.getBroadcastFC().getString("default-message")
                : messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
    }

    private String render(String template) {
        int banCount = plugin.getBanManager().countActiveBans();
        int banIpCount = plugin.getBanManager().countActiveIpBans();
        return template
                .replace("%s", String.valueOf(banCount))
                .replace("%i", String.valueOf(banIpCount))
                .replace("%t", String.valueOf(banCount + banIpCount));
    }

    private void send(Player player, String message) {
        TextComponent mainMessage = new TextComponent(
                ChatColor.translateAlternateColorCodes('&', plugin.prefix() + " " + message));
        mainMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(HOVER_TITLE).create()));

        TextComponent clickable = new TextComponent(BUTTON_TEXT);
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lban list"));
        clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(BUTTON_HOVER).create()));

        player.spigot().sendMessage(mainMessage, clickable);
    }
}

package org.leng.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

public class Utils {

    public static void sendMessage(CommandSender sender, String message) {
        if (SchedulerUtils.isFolia() && sender instanceof Entity) {
            LengbanlistSafe.runForEntity((Entity) sender, () -> sender.sendMessage(message));
            return;
        }
        sender.sendMessage(message);
    }

    public static void sendMessage(Player player, BaseComponent... components) {
        if (SchedulerUtils.isFolia()) {
            LengbanlistSafe.runForEntity(player, () -> player.spigot().sendMessage(components));
            return;
        }
        player.spigot().sendMessage(components);
    }

    public static void broadcast(String message) {
        if (!SchedulerUtils.isFolia()) {
            Bukkit.broadcastMessage(message);
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendMessage(player, message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    public static TextComponent clickableText(String text, String command) {
        TextComponent component = new TextComponent(ChatColor.translateAlternateColorCodes('&', text));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        return component;
    }

    public static String escapeCommandArg(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\').append(c);
            } else if (Character.isWhitespace(c)) {
                continue;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String getSenderName(CommandSender sender) {
        if (sender instanceof Player) {
            return sender.getName();
        }
        return "CONSOLE";
    }

    public static TextComponent clickableUrl(String text, String url) {
        TextComponent component = new TextComponent(ChatColor.translateAlternateColorCodes('&', text));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        return component;
    }

    private static class LengbanlistSafe {

        static void runForEntity(Entity entity, Runnable task) {
            org.leng.Lengbanlist plugin = org.leng.Lengbanlist.getInstance();
            if (plugin == null || !plugin.isEnabled()) return;
            SchedulerUtils.runTask(plugin, entity, task);
        }
    }
}

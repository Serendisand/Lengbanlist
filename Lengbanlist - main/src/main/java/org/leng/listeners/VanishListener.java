package org.leng.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.leng.Lengbanlist;

public class VanishListener implements Listener {

    private final Lengbanlist plugin;

    public VanishListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.isFeatureEnabled("vanish")) {
            plugin.getVanishManager().handleJoin(player);
        }
        if (plugin.getVanishManager().isVanished(player)) {
            event.setJoinMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getVanishManager().isVanished(event.getPlayer())) {
            event.setQuitMessage(null);
        }
    }
}

package org.leng.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.leng.Lengbanlist;

public class GuiCleanupListener implements Listener {
    private final Lengbanlist plugin;

    public GuiCleanupListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGuiSessionManager().clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().startsWith("§bLengbanlist")) {
            plugin.getGuiSessionManager().clear(event.getPlayer().getUniqueId());
        }
    }
}

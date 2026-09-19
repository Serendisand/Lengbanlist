package org.leng.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.leng.Lengbanlist;
import org.leng.object.FreezeEntry;
import org.leng.utils.Utils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeListener implements Listener {

    private static final long NOTICE_COOLDOWN_MS = 3000L;

    private final Lengbanlist plugin;
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

    public FreezeListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FreezeEntry entry = plugin.getFreezeManager().get(player);
        if (entry == null) {
            return;
        }
        plugin.getFreezeManager().notify(player, entry.reason());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!frozen(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getWorld() != null && to.getWorld() != null && !from.getWorld().equals(to.getWorld())) {
            blocked(event.getPlayer(), event);
            return;
        }
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        event.setTo(from);
        remind(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        blocked(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        blocked(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        blocked(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            return;
        }
        blocked(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        blocked(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        blocked(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        blocked(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        blocked(event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        blocked((Player) event.getWhoClicked(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        blocked((Player) event.getWhoClicked(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        blocked((Player) event.getDamager(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("freeze.block-commands", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!frozen(player)) {
            return;
        }
        if (allowedCommand(event.getMessage())) {
            return;
        }
        blocked(player, event);
    }

    private boolean allowedCommand(String message) {
        List<String> allowed = plugin.getConfig().getStringList("freeze.allowed-commands");
        if (allowed.isEmpty()) {
            return false;
        }
        return MuteCommandBlockPolicy.isBlocked(message, allowed, true);
    }

    private boolean frozen(Player player) {
        return plugin.isFeatureEnabled("freeze") && plugin.getFreezeManager().isFrozen(player);
    }

    private void blocked(Player player, Cancellable event) {
        if (!frozen(player)) {
            return;
        }
        event.setCancelled(true);
        remind(player);
    }

    private void remind(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_COOLDOWN_MS) {
            return;
        }
        lastNotice.put(player.getUniqueId(), now);
        Utils.sendMessage(player, plugin.getModelManager().getCurrentModel().getFreezeReminder());
    }
}

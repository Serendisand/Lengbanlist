package org.leng.listeners;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.utils.Utils;

import java.util.List;

public class ModelChoiceListener implements Listener {

    private static final String PICK_PREFIX = "MODEL_PICK:";
    private static final String PAGE_PREV = "MODEL_PAGE_PREV";
    private static final String PAGE_NEXT = "MODEL_PAGE_NEXT";

    private final Lengbanlist plugin;

    public ModelChoiceListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(ModelManager.MODEL_UI_TITLE)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return;
        }

        String action = lore.get(0).replace("§7", "");

        if (action.startsWith(PICK_PREFIX)) {
            pickModel(player, action.substring(PICK_PREFIX.length()));
            return;
        }

        int page = plugin.getGuiSessionManager().getPage(player.getUniqueId(), ModelManager.MODEL_VIEW);
        if (PAGE_PREV.equals(action)) {
            ModelManager.getInstance().openModelSelectionUI(player, page - 1);
        } else if (PAGE_NEXT.equals(action)) {
            ModelManager.getInstance().openModelSelectionUI(player, page + 1);
        }
    }

    private void pickModel(Player player, String modelName) {
        if (!plugin.isFeatureEnabled("model")) {
            plugin.sendFeatureDisabled(player);
            return;
        }
        if (!player.hasPermission("lengbanlist.model")) {
            Utils.sendMessage(player, plugin.prefix() + "§c不是你的工作喵！");
            return;
        }
        if (!ModelManager.getInstance().getModels().containsKey(modelName.toLowerCase())) {
            Utils.sendMessage(player, plugin.prefix() + "§c不认识这个模型喵。");
            return;
        }
        ModelManager.getInstance().switchModel(modelName);
        Utils.sendMessage(player, plugin.prefix() + "§a已切换到模型: " + modelName);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        player.closeInventory();
    }
}

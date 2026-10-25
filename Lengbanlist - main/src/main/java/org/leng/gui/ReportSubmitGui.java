package org.leng.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.leng.Lengbanlist;
import org.leng.service.VanishManager;
import org.leng.util.ErrorLog;
import org.leng.util.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 玩家侧举报界面：展示在线玩家头颅，点谁就在聊天栏输入举报理由。
 */
public final class ReportSubmitGui implements Listener {

    private static final String PLAYER_TITLE = "§bLengbanlist §8| §e举报";

    private static final String VIEW_PLAYERS = "report-submit";

    private static final String ACTION_TARGET = "SUBMIT_TARGET";
    private static final String ACTION_PREV = "SUBMIT_PREV";
    private static final String ACTION_NEXT = "SUBMIT_NEXT";
    private static final String ACTION_CLOSE = "GUI_CLOSE";

    private final Lengbanlist plugin;
    private final GuiKit kit;

    public ReportSubmitGui(Lengbanlist plugin) {
        this.plugin = plugin;
        this.kit = new GuiKit(plugin);
    }

    public void openPlayerList(Player player) {
        openPlayerList(player, 0);
    }

    public void openPlayerList(Player player, int page) {
        if (!Utils.canUse(plugin, player, "lengbanlist.report", "report")) {
            Utils.sendMessage(player, plugin.prefix() + "§c你没有权限使用此命令。");
            return;
        }
        if (!plugin.isFeatureActive("chest-ui")) {
            plugin.sendFeatureDisabled(player);
            return;
        }
        if (plugin.getReportManager().noticeReportBan(player)) {
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 54, PLAYER_TITLE);
        renderPlayerList(inventory, player, page);
        player.openInventory(inventory);
        plugin.getGuiSessionManager().setView(player.getUniqueId(), VIEW_PLAYERS);
    }

    private void renderPlayerList(Inventory inventory, Player viewer, int page) {
        GuiKit.fill(inventory, Material.GRAY_STAINED_GLASS_PANE, "§7 ");
        List<Player> targets = targets(viewer);
        int totalPages = Math.max(1, (targets.size() + GuiKit.PAGE_SIZE - 1) / GuiKit.PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        plugin.getGuiSessionManager().setPage(viewer.getUniqueId(), VIEW_PLAYERS, safePage);

        int start = safePage * GuiKit.PAGE_SIZE;
        for (int slot = 0; slot < GuiKit.CONTENT_SLOTS.length; slot++) {
            int index = start + slot;
            if (index >= targets.size()) {
                break;
            }
            Player target = targets.get(index);
            inventory.setItem(GuiKit.CONTENT_SLOTS[slot], kit.head(target, "§e" + target.getName(),
                    List.of("§7玩家ID: §f" + target.getName(),
                            "§7UUID: §8" + target.getUniqueId(),
                            "§e左键举报该玩家，随后在聊天栏输入理由"),
                    ACTION_TARGET, target.getName()));
        }

        if (targets.isEmpty()) {
            inventory.setItem(22, kit.item(Material.BARRIER, "§c没有可举报的在线玩家",
                    List.of("§7等有玩家在线后再来看看吧"), null, null));
        }
        if (safePage > 0) {
            inventory.setItem(GuiKit.SLOT_PREV, kit.item(Material.ARROW, "§e上一页",
                    List.of("§7第 " + (safePage + 1) + " / " + totalPages + " 页"), ACTION_PREV, null));
        }
        inventory.setItem(GuiKit.SLOT_BACK, kit.item(Material.BARRIER, "§c关闭",
                List.of("§7关闭界面"), ACTION_CLOSE, null));
        inventory.setItem(GuiKit.SLOT_INFO, kit.item(Material.PAPER,
                "§b第 " + (safePage + 1) + " / " + totalPages + " 页",
                List.of("§7共 " + targets.size() + " 位在线玩家"), null, null));
        if (safePage + 1 < totalPages) {
            inventory.setItem(GuiKit.SLOT_NEXT, kit.item(Material.ARROW, "§e下一页",
                    List.of("§7第 " + (safePage + 1) + " / " + totalPages + " 页"), ACTION_NEXT, null));
        }
    }

    private List<Player> targets(Player viewer) {
        List<Player> targets = new ArrayList<>();
        VanishManager vanish = plugin.getVanishManager();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            if (vanish != null && vanish.isVanished(online) && !vanish.canSee(viewer)) {
                continue;
            }
            targets.add(online);
        }
        targets.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        return targets;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!PLAYER_TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        String action = kit.action(item);
        if (action == null) {
            return;
        }
        try {
            handleClick(player, action, kit.data(item), event.getView().getTopInventory());
        } catch (Exception e) {
            ErrorLog.record(plugin, "举报 GUI 点击处理失败", e);
            Utils.sendMessage(player, plugin.prefix() + "§c处理失败，请查看控制台。");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (PLAYER_TITLE.equals(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    private void handleClick(Player player, String action, String data, Inventory inventory) {
        if (!Utils.canUse(plugin, player, "lengbanlist.report", "report")) {
            player.closeInventory();
            Utils.sendMessage(player, plugin.prefix() + "§c你没有权限使用此命令。");
            return;
        }
        switch (action) {
            case ACTION_TARGET -> {
                player.closeInventory();
                plugin.getWizardManager().startReportReason(player, data);
            }
            case ACTION_PREV -> {
                int page = plugin.getGuiSessionManager().getPage(player.getUniqueId(), VIEW_PLAYERS);
                GuiKit.click(player);
                renderPlayerList(inventory, player, page - 1);
            }
            case ACTION_NEXT -> {
                int page = plugin.getGuiSessionManager().getPage(player.getUniqueId(), VIEW_PLAYERS);
                GuiKit.click(player);
                renderPlayerList(inventory, player, page + 1);
            }
            case ACTION_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }
}

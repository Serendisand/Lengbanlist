package org.leng.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.leng.Lengbanlist;
import org.leng.service.ReportManager;
import org.leng.service.VanishManager;
import org.leng.util.ErrorLog;
import org.leng.util.Utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 玩家侧举报界面：展示在线玩家头颅，点谁就选一条预设理由提交举报。
 */
public final class ReportSubmitGui implements Listener {

    private static final String PLAYER_TITLE = "§bLengbanlist §8| §e举报";
    private static final String REASON_TITLE = "§bLengbanlist §8| §e举报理由";
    private static final Set<String> TITLES = Set.of(PLAYER_TITLE, REASON_TITLE);

    private static final String VIEW_PLAYERS = "report-submit";

    private static final String ACTION_TARGET = "SUBMIT_TARGET";
    private static final String ACTION_REASON = "SUBMIT_REASON";
    private static final String ACTION_BACK = "SUBMIT_BACK";
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
                            "§e左键选择举报理由"),
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

    /**
     * 理由选择界面：列出 config.yml 的 preset-reasons，点一条直接提交。
     */
    public void openReason(Player player, String target) {
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
        String safeTarget = target == null ? "" : target.trim();
        if (safeTarget.isEmpty()) {
            Utils.sendMessage(player, plugin.prefix() + "§c玩家名不能为空喵。");
            return;
        }
        List<String> reasons = presetReasons();
        if (reasons.isEmpty()) {
            // 没配预设理由时退回聊天输入，别把举报功能卡死
            player.closeInventory();
            plugin.getWizardManager().startReportReason(player, safeTarget);
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, 54, REASON_TITLE);
        GuiKit.fill(inventory, Material.GRAY_STAINED_GLASS_PANE, "§7 ");
        int slot = 0;
        for (String reason : reasons) {
            if (slot >= GuiKit.CONTENT_SLOTS.length) {
                plugin.getLogger().warning("preset-reasons 超过 " + GuiKit.PAGE_SIZE
                        + " 个，举报 GUI 只显示前 " + GuiKit.PAGE_SIZE + " 个");
                break;
            }
            inventory.setItem(GuiKit.CONTENT_SLOTS[slot++], kit.item(Material.PAPER, "§e" + reason,
                    List.of("§7举报对象：§f" + safeTarget, "§e点击提交该理由"),
                    ACTION_REASON, safeTarget, reason));
        }
        inventory.setItem(GuiKit.SLOT_BACK, kit.item(Material.ARROW, "§e返回玩家列表",
                List.of("§7重新选择举报对象"), ACTION_BACK, null));
        inventory.setItem(GuiKit.SLOT_INFO, kit.item(Material.BOOK, "§b举报对象：§f" + safeTarget,
                List.of("§7请选择一个举报理由"), null, null));
        inventory.setItem(50, kit.item(Material.BARRIER, "§c关闭",
                List.of("§7关闭界面"), ACTION_CLOSE, null));
        player.openInventory(inventory);
        plugin.getGuiSessionManager().setView(player.getUniqueId(), "report-reason:" + safeTarget);
    }

    private List<String> presetReasons() {
        List<String> reasons = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("preset-reasons");
        if (section == null) {
            return reasons;
        }
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                reasons.add(value);
            }
        }
        return reasons;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLES.contains(event.getView().getTitle())) {
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
            handleClick(player, action, kit.data(item), kit.extra(item), event.getView().getTopInventory());
        } catch (Exception e) {
            ErrorLog.record(plugin, "举报 GUI 点击处理失败", e);
            Utils.sendMessage(player, plugin.prefix() + "§c处理失败，请查看控制台。");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (TITLES.contains(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    private void handleClick(Player player, String action, String data, String extra, Inventory inventory) {
        if (!Utils.canUse(plugin, player, "lengbanlist.report", "report")) {
            player.closeInventory();
            Utils.sendMessage(player, plugin.prefix() + "§c你没有权限使用此命令。");
            return;
        }
        switch (action) {
            case ACTION_TARGET -> {
                GuiKit.click(player);
                openReason(player, data);
            }
            case ACTION_REASON -> {
                player.closeInventory();
                ReportManager manager = plugin.getReportManager();
                ReportManager.SubmitOutcome outcome = manager.submitReport(player, data, extra);
                manager.sendSubmitFeedback(player, outcome);
            }
            case ACTION_BACK -> {
                GuiKit.click(player);
                openPlayerList(player);
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

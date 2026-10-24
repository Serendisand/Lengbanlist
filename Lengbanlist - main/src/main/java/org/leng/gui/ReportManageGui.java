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
import org.leng.object.ReportEntry;
import org.leng.service.ReportManager;
import org.leng.util.ErrorLog;
import org.leng.util.IpMatcher;
import org.leng.util.TimeUtils;
import org.leng.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 管理侧举报界面：列表 → 详情 → 受理 / 关闭 / 封禁 / 警告 / 传送。
 */
public final class ReportManageGui implements Listener {

    private static final String LIST_TITLE = "§bLengbanlist §8| §c举报管理";
    private static final String DETAIL_TITLE = "§bLengbanlist §8| §c举报详情";
    private static final String DURATION_TITLE = "§bLengbanlist §8| §c封禁时长";
    private static final String CONFIRM_BAN_TITLE = "§bLengbanlist §8| §c确认封禁";
    private static final String CONFIRM_CLOSE_TITLE = "§bLengbanlist §8| §c确认关闭";
    private static final String WARN_REASON_TITLE = "§bLengbanlist §8| §e警告原因";
    private static final Set<String> TITLES = Set.of(
            LIST_TITLE, DETAIL_TITLE, DURATION_TITLE, CONFIRM_BAN_TITLE,
            CONFIRM_CLOSE_TITLE, WARN_REASON_TITLE);

    private static final String VIEW_LIST = "report-manage";
    private static final Set<String> BAN_DURATIONS = Set.of("1d", "7d", "30d", "90d", "forever", "auto");

    private static final String ACTION_DETAIL = "MANAGE_DETAIL";
    private static final String ACTION_ACCEPT = "MANAGE_ACCEPT";
    private static final String ACTION_CLOSE_CONFIRM = "MANAGE_CLOSE_CONFIRM";
    private static final String ACTION_CLOSE_EXECUTE = "MANAGE_CLOSE_EXECUTE";
    private static final String ACTION_BAN = "MANAGE_BAN";
    private static final String ACTION_BAN_CONFIRM = "MANAGE_BAN_CONFIRM";
    private static final String ACTION_BAN_EXECUTE = "MANAGE_BAN_EXECUTE";
    private static final String ACTION_WARN = "MANAGE_WARN";
    private static final String ACTION_WARN_REASON = "MANAGE_WARN_REASON";
    private static final String ACTION_WARN_CUSTOM = "MANAGE_WARN_CUSTOM";
    private static final String ACTION_TELEPORT = "MANAGE_TELEPORT";
    private static final String ACTION_REFRESH_LIST = "MANAGE_REFRESH_LIST";
    private static final String ACTION_BACK_LIST = "MANAGE_BACK_LIST";
    private static final String ACTION_PREV = "MANAGE_PREV";
    private static final String ACTION_NEXT = "MANAGE_NEXT";
    private static final String ACTION_CLOSE = "GUI_CLOSE";

    private final Lengbanlist plugin;
    private final GuiKit kit;

    public ReportManageGui(Lengbanlist plugin) {
        this.plugin = plugin;
        this.kit = new GuiKit(plugin);
    }

    public void openList(Player player) {
        if (!requireAdmin(player)) {
            return;
        }
        if (!plugin.isFeatureActive("chest-ui")) {
            plugin.sendFeatureDisabled(player);
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 54, LIST_TITLE);
        renderList(inventory, player, 0);
        player.openInventory(inventory);
        plugin.getGuiSessionManager().setView(player.getUniqueId(), VIEW_LIST);
    }

    public void openDetail(Player player, String reportId) {
        if (!requireAdmin(player)) {
            return;
        }
        ReportEntry report = plugin.getReportManager().getReport(reportId);
        if (report == null) {
            Utils.sendMessage(player, plugin.prefix() + "§c未找到举报编号: " + reportId);
            openList(player);
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 54, DETAIL_TITLE);
        renderDetail(inventory, player, report);
        player.openInventory(inventory);
        plugin.getGuiSessionManager().setView(player.getUniqueId(), "report-detail:" + reportId);
    }

    private void renderList(Inventory inventory, Player player, int page) {
        GuiKit.fill(inventory, Material.GRAY_STAINED_GLASS_PANE, "§7 ");
        List<ReportEntry> reports = plugin.getReportManager().getPendingReports();
        int totalPages = Math.max(1, (reports.size() + GuiKit.PAGE_SIZE - 1) / GuiKit.PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        plugin.getGuiSessionManager().setPage(player.getUniqueId(), VIEW_LIST, safePage);

        int start = safePage * GuiKit.PAGE_SIZE;
        for (int slot = 0; slot < GuiKit.CONTENT_SLOTS.length; slot++) {
            int index = start + slot;
            if (index >= reports.size()) {
                break;
            }
            ReportEntry report = reports.get(index);
            List<String> lore = new ArrayList<>();
            lore.add("§7状态：" + GuiKit.statusText(report.getStatus()));
            lore.add("§7举报人：§f" + safe(report.getReporter()));
            lore.add("§7时间：§f" + TimeUtils.timestampToReadable(report.getTimestamp()));
            lore.add("§7原因：");
            for (String line : GuiKit.wrap(report.getReason(), 24)) {
                lore.add("§f  " + line);
            }
            lore.add("§a左键查看并处理");
            inventory.setItem(GuiKit.CONTENT_SLOTS[slot], kit.item(Material.PAPER,
                    "§e#" + GuiKit.shortId(report.getId()) + " §f" + safe(report.getTarget()),
                    lore, ACTION_DETAIL, report.getId()));
        }

        if (reports.isEmpty()) {
            inventory.setItem(22, kit.item(Material.LIME_DYE, "§a暂无待处理举报",
                    List.of("§7有新举报时会出现在这里"), null, null));
        }
        if (safePage > 0) {
            inventory.setItem(GuiKit.SLOT_PREV, kit.item(Material.ARROW, "§e上一页",
                    List.of("§7第 " + (safePage + 1) + " / " + totalPages + " 页"), ACTION_PREV, null));
        }
        inventory.setItem(GuiKit.SLOT_BACK, kit.item(Material.COMPASS, "§b刷新列表",
                List.of("§7重新读取待处理举报"), ACTION_REFRESH_LIST, null));
        inventory.setItem(GuiKit.SLOT_INFO, kit.item(Material.PAPER,
                "§b第 " + (safePage + 1) + " / " + totalPages + " 页",
                List.of("§7共 " + reports.size() + " 条待处理举报"), null, null));
        if (safePage + 1 < totalPages) {
            inventory.setItem(GuiKit.SLOT_NEXT, kit.item(Material.ARROW, "§e下一页",
                    List.of("§7第 " + (safePage + 1) + " / " + totalPages + " 页"), ACTION_NEXT, null));
        }
        inventory.setItem(50, kit.item(Material.BARRIER, "§c关闭",
                List.of("§7关闭界面"), ACTION_CLOSE, null));
    }

    private void renderDetail(Inventory inventory, Player player, ReportEntry report) {
        GuiKit.fill(inventory, Material.GRAY_STAINED_GLASS_PANE, "§7 ");
        boolean finalized = ReportEntry.isFinalized(report.getStatus());
        boolean accepted = ReportEntry.STATUS_ACCEPTED.equals(report.getStatus());

        List<String> info = new ArrayList<>();
        info.add("§7状态：" + GuiKit.statusText(report.getStatus()));
        info.add("§7被举报人：§f" + safe(report.getTarget()));
        info.add("§7举报人：§f" + safe(report.getReporter()));
        info.add("§7时间：§f" + TimeUtils.timestampToReadable(report.getTimestamp()));
        info.add("§7原因：");
        for (String line : GuiKit.wrap(report.getReason(), 28)) {
            info.add("§f  " + line);
        }
        info.add("§8完整编号: " + report.getId());
        inventory.setItem(4, kit.item(Material.WRITTEN_BOOK,
                "§e举报 #" + GuiKit.shortId(report.getId()), info, null, null));

        if (finalized) {
            inventory.setItem(19, kit.item(Material.GRAY_DYE, "§7举报已结束",
                    List.of("§7不能再受理或关闭"), null, null));
        } else if (accepted) {
            inventory.setItem(19, kit.item(Material.GRAY_DYE, "§7已受理",
                    List.of("§7该举报正在处理中"), null, null));
        } else {
            inventory.setItem(19, kit.item(Material.LIME_DYE, "§a受理举报",
                    List.of("§7将状态改为「受理中」并通知举报人"), ACTION_ACCEPT, report.getId()));
        }

        if (finalized) {
            inventory.setItem(21, kit.item(Material.GRAY_DYE, "§7举报已结束",
                    List.of("§7不能再关闭"), null, null));
        } else {
            inventory.setItem(21, kit.item(Material.RED_DYE, "§c关闭举报",
                    List.of("§7关闭前需要再次确认"), ACTION_CLOSE_CONFIRM, report.getId()));
        }

        if (finalized) {
            inventory.setItem(23, kit.item(Material.GRAY_DYE, "§7举报已结束",
                    List.of("§7不能再封禁"), null, null));
        } else if (isIpTarget(report.getTarget())) {
            inventory.setItem(23, kit.item(Material.GRAY_DYE, "§7暂不支持 IP 举报封禁",
                    List.of("§7请使用 /lban add <IP> 手动封禁"), null, null));
        } else {
            inventory.setItem(23, kit.item(Material.IRON_AXE, "§c封禁并处理举报",
                    List.of("§7先选择封禁时长", "§7封禁后举报状态变为「已处理」"),
                    ACTION_BAN, report.getId()));
        }

        if (!Utils.canUse(plugin, player, "lengbanlist.warn", "warn")) {
            inventory.setItem(25, kit.item(Material.GRAY_DYE, "§7无权警告",
                    List.of("§7需要 lengbanlist.warn 权限"), null, null));
        } else {
            inventory.setItem(25, kit.item(Material.PAPER, "§e警告被举报人",
                    List.of("§7选择原因并记录一次警告"), ACTION_WARN, report.getId()));
        }

        Player target = Bukkit.getPlayer(report.getTarget());
        if (target != null && !target.getUniqueId().equals(player.getUniqueId())) {
            inventory.setItem(30, kit.item(Material.ENDER_PEARL, "§b传送到被举报人",
                    List.of("§7点击传送到 " + target.getName()), ACTION_TELEPORT, report.getId()));
        } else {
            inventory.setItem(30, kit.item(Material.GRAY_DYE, "§7被举报人不在线",
                    List.of("§7无法传送"), null, null));
        }

        inventory.setItem(45, kit.item(Material.ARROW, "§e返回举报列表",
                List.of("§7回到待处理列表"), ACTION_BACK_LIST, null));
        inventory.setItem(49, kit.item(Material.COMPASS, "§b刷新",
                List.of("§7重新读取该举报的状态"), ACTION_DETAIL, report.getId()));
        inventory.setItem(53, kit.item(Material.BARRIER, "§c关闭",
                List.of("§7关闭界面"), ACTION_CLOSE, null));
    }

    private void openDuration(Player player, String reportId) {
        if (!requireAdmin(player)) {
            return;
        }
        ReportEntry report = plugin.getReportManager().getReport(reportId);
        if (report == null) {
            Utils.sendMessage(player, plugin.prefix() + "§c未找到举报编号: " + reportId);
            openList(player);
            return;
        }
        if (ReportEntry.isFinalized(report.getStatus())) {
            Utils.sendMessage(player, plugin.prefix() + "§c该举报已处理，无法再次操作。");
            openList(player);
            return;
        }
        if (isIpTarget(report.getTarget())) {
            Utils.sendMessage(player, plugin.prefix() + "§c暂不支持从举报界面封禁 IP，请使用 /lban add <IP>。");
            openDetail(player, reportId);
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, 54, DURATION_TITLE);
        GuiKit.fill(inventory, Material.GRAY_STAINED_GLASS_PANE, "§7 ");
        inventory.setItem(10, durationItem(Material.CLOCK, "1 天", "1d", reportId));
        inventory.setItem(12, durationItem(Material.CLOCK, "7 天", "7d", reportId));
        inventory.setItem(14, durationItem(Material.CLOCK, "30 天", "30d", reportId));
        inventory.setItem(16, durationItem(Material.CLOCK, "90 天", "90d", reportId));
        inventory.setItem(28, durationItem(Material.BEDROCK, "永久", "forever", reportId));
        inventory.setItem(30, durationItem(Material.COMPARATOR, "自动（按处罚策略）", "auto", reportId));
        inventory.setItem(45, kit.item(Material.ARROW, "§e返回举报详情",
                List.of("§7不封禁，返回详情"), ACTION_DETAIL, reportId));
        inventory.setItem(53, kit.item(Material.BARRIER, "§c关闭",
                List.of("§7关闭界面"), ACTION_CLOSE, null));
        player.openInventory(inventory);
        plugin.getGuiSessionManager().setView(player.getUniqueId(), "report-ban:" + reportId);
    }

    private ItemStack durationItem(Material material, String label, String spec, String reportId) {
        return kit.item(material, "§e封禁 " + label,
                List.of("§7时长：§f" + label, "§7点击后进入确认界面"),
                ACTION_BAN_CONFIRM, reportId, spec);
    }

    private void openConfirmBan(Player player, String reportId, String durationSpec) {
        if (!requireAdmin(player)) {
            return;
        }
        if (durationSpec == null || !BAN_DURATIONS.contains(durationSpec)) {
            Utils.sendMessage(player, plugin.prefix() + "§c无效的封禁时长。");
            return;
        }
        ReportEntry report = plugin.getReportManager().getReport(reportId);
        if (report == null) {
            Utils.sendMessage(player, plugin.prefix() + "§c未找到举报编号: " + reportId);
            openList(player);
            return;
        }
        if (ReportEntry.isFinalized(report.getStatus())) {
            Utils.sendMessage(player, plugin.prefix() + "§c该举报已处理，无法再次操作。");
            openList(player);
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, 54, CONFIRM_BAN_TITLE);
        GuiKit.fill(inventory, Material.GRAY_STAINED_GLASS_PANE, "§7 ");
        inventory.setItem(20, kit.item(Material.LIME_WOOL, "§a确认封禁",
                List.of("§7被举报人：§f" + safe(report.getTarget()),
                        "§7时长：§f" + durationLabel(durationSpec),
                        "§c封禁后举报状态变为「已处理」"),
                ACTION_BAN_EXECUTE, reportId, durationSpec));
        inventory.setItem(24, kit.item(Material.RED_WOOL, "§c取消",
                List.of("§7返回举报详情"), ACTION_DETAIL, reportId));
        player.openInventory(inventory);
        plugin.getGuiSessionManager().setView(player.getUniqueId(), "report-ban-confirm:" + reportId);
    }

    private void openConfirmClose(Player player, String reportId) {
        if (!requireAdmin(player)) {
            return;
        }
        ReportEntry report = plugin.getReportManager().getReport(reportId);
        if (report == null) {
            Utils.sendMessage(player, plugin.prefix() + "§c未找到举报编号: " + reportId);
            openList(player);
            return;
        }
        if (ReportEntry.isFinalized(report.getStatus())) {
            Utils.sendMessage(player, plugin.prefix() + "§c该举报已处理，无法再次操作。");
            openList(player);
            return;
        }
        Inventory inventory = Bukkit.createInventory(null, 54, CONFIRM_CLOSE_TITLE);
        GuiKit.fill(inventory, Material.GRAY_STAINED_GLASS_PANE, "§7 ");
        inventory.setItem(20, kit.item(Material.LIME_WOOL, "§a确认关闭举报",
                List.of("§7举报人会收到关闭通知", "§c关闭后不能撤销"), ACTION_CLOSE_EXECUTE, reportId));
        inventory.setItem(24, kit.item(Material.RED_WOOL, "§c取消",
                List.of("§7返回举报详情"), ACTION_DETAIL, reportId));
        player.openInventory(inventory);
        plugin.getGuiSessionManager().setView(player.getUniqueId(), "report-close:" + reportId);
    }

    private void openWarnReason(Player player, String reportId) {
        if (!requireAdmin(player)) {
            return;
        }
        if (!Utils.canUse(plugin, player, "lengbanlist.warn", "warn")) {
            Utils.sendMessage(player, plugin.prefix() + "§c你没有权限警告玩家。");
            return;
        }
        ReportEntry report = plugin.getReportManager().getReport(reportId);
        if (report == null) {
            Utils.sendMessage(player, plugin.prefix() + "§c未找到举报编号: " + reportId);
            openList(player);
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, 54, WARN_REASON_TITLE);
        GuiKit.fill(inventory, Material.GRAY_STAINED_GLASS_PANE, "§7 ");
        List<String> reasons = presetReasons();
        int slot = 0;
        for (String reason : reasons) {
            if (slot >= GuiKit.CONTENT_SLOTS.length) {
                plugin.getLogger().warning("preset-reasons 超过 " + GuiKit.PAGE_SIZE
                        + " 个，警告 GUI 只显示前 " + GuiKit.PAGE_SIZE + " 个");
                break;
            }
            inventory.setItem(GuiKit.CONTENT_SLOTS[slot++], kit.item(Material.PAPER, "§e" + reason,
                    List.of("§7警告 " + report.getTarget() + " 的原因"),
                    ACTION_WARN_REASON, reportId, reason));
        }
        inventory.setItem(GuiKit.SLOT_BACK, kit.item(Material.ARROW, "§e返回举报详情",
                List.of("§7不警告，返回详情"), ACTION_DETAIL, reportId));
        inventory.setItem(GuiKit.SLOT_INFO, kit.item(Material.WRITABLE_BOOK, "§a自定义原因",
                List.of("§7在聊天栏输入警告原因"), ACTION_WARN_CUSTOM, reportId));
        inventory.setItem(50, kit.item(Material.BARRIER, "§c关闭",
                List.of("§7关闭界面"), ACTION_CLOSE, null));
        player.openInventory(inventory);
        plugin.getGuiSessionManager().setView(player.getUniqueId(), "report-warn:" + reportId);
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
            ErrorLog.record(plugin, "举报管理 GUI 点击处理失败", e);
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
        if (!requireAdmin(player)) {
            player.closeInventory();
            return;
        }
        switch (action) {
            case ACTION_DETAIL -> openDetail(player, data);
            case ACTION_ACCEPT -> {
                ReportManager manager = plugin.getReportManager();
                ReportManager.ActionResult result = manager.acceptReport(data, player.getName());
                manager.sendAcceptFeedback(player, result, data);
                openDetail(player, data);
            }
            case ACTION_CLOSE_CONFIRM -> openConfirmClose(player, data);
            case ACTION_CLOSE_EXECUTE -> {
                ReportManager manager = plugin.getReportManager();
                ReportManager.ActionResult result = manager.closeReport(data, player.getName());
                manager.sendCloseFeedback(player, result, data);
                openList(player);
            }
            case ACTION_BAN -> openDuration(player, data);
            case ACTION_BAN_CONFIRM -> openConfirmBan(player, data, extra);
            case ACTION_BAN_EXECUTE -> {
                if (!BAN_DURATIONS.contains(extra)) {
                    Utils.sendMessage(player, plugin.prefix() + "§c无效的封禁时长。");
                    return;
                }
                player.closeInventory();
                player.performCommand("lban handle " + data + " " + extra);
                openList(player);
            }
            case ACTION_WARN -> openWarnReason(player, data);
            case ACTION_WARN_REASON -> {
                if (extra == null || extra.isBlank()) {
                    Utils.sendMessage(player, plugin.prefix() + "§c警告原因不能为空。");
                    return;
                }
                ReportEntry report = plugin.getReportManager().getReport(data);
                if (report == null) {
                    Utils.sendMessage(player, plugin.prefix() + "§c未找到举报编号: " + data);
                    openList(player);
                    return;
                }
                if (!Utils.canUse(plugin, player, "lengbanlist.warn", "warn")) {
                    Utils.sendMessage(player, plugin.prefix() + "§c你没有权限警告玩家。");
                    return;
                }
                plugin.getWarnManager().warnPlayer(report.getTarget(), player.getName(), extra);
                Utils.sendMessage(player, plugin.prefix() + "§a已警告玩家 " + report.getTarget() + "。");
                openDetail(player, data);
            }
            case ACTION_WARN_CUSTOM -> {
                ReportEntry report = plugin.getReportManager().getReport(data);
                if (report == null) {
                    Utils.sendMessage(player, plugin.prefix() + "§c未找到举报编号: " + data);
                    openList(player);
                    return;
                }
                player.closeInventory();
                plugin.getWizardManager().startWarnTarget(player, report.getTarget(), data);
            }
            case ACTION_TELEPORT -> {
                ReportEntry report = plugin.getReportManager().getReport(data);
                Player target = report == null ? null : Bukkit.getPlayer(report.getTarget());
                if (target == null) {
                    Utils.sendMessage(player, plugin.prefix() + "§c被举报人不在线。");
                    return;
                }
                player.closeInventory();
                player.teleport(target);
                Utils.sendMessage(player, plugin.prefix() + "§a已传送到玩家 " + target.getName());
            }
            case ACTION_PREV -> {
                int page = plugin.getGuiSessionManager().getPage(player.getUniqueId(), VIEW_LIST);
                GuiKit.click(player);
                renderList(inventory, player, page - 1);
            }
            case ACTION_NEXT -> {
                int page = plugin.getGuiSessionManager().getPage(player.getUniqueId(), VIEW_LIST);
                GuiKit.click(player);
                renderList(inventory, player, page + 1);
            }
            case ACTION_REFRESH_LIST, ACTION_BACK_LIST -> openList(player);
            case ACTION_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private boolean requireAdmin(Player player) {
        if (!Utils.canUse(plugin, player, "lengbanlist.admin", "admin")) {
            Utils.sendMessage(player, plugin.prefix() + "§c你没有权限使用此命令。");
            return false;
        }
        return true;
    }

    private boolean isIpTarget(String target) {
        return target != null && IpMatcher.isValidIpOrCidrOrWildcard(target);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String durationLabel(String spec) {
        return switch (spec) {
            case "1d" -> "1 天";
            case "7d" -> "7 天";
            case "30d" -> "30 天";
            case "90d" -> "90 天";
            case "forever" -> "永久";
            case "auto" -> "自动（按处罚策略）";
            default -> spec;
        };
    }
}

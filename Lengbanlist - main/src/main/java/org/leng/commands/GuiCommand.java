package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.leng.Lengbanlist;
import org.leng.manager.GuiSessionManager;
import org.leng.manager.ModelManager;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.FreezeEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class GuiCommand implements Listener {

    private static final String TITLE = "§bLengbanlist";
    private static final int GUI_PAGE_SIZE = 28;
    private static final int[] GUI_CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private static final String PREFIX_COMMAND = "/";
    private static final String ACTION_SPONSOR = "ACTION_SPONSOR";
    private static final String REPORT_PREFIX = "REPORT:";

    private final Lengbanlist plugin;

    public GuiCommand(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void openChestUI(Player player) {
        Inventory chest = Bukkit.createInventory(null, 54, TITLE);
        player.openInventory(chest);
        GuiSessionManager gui = plugin.getGuiSessionManager();
        gui.setView(player.getUniqueId(), "menu");
        gui.setPage(player.getUniqueId(), "menu", 0);
        renderGuiMenu(player, chest);
    }

    private void renderGuiMenu(Player player, Inventory chest) {
        ItemStack glass = createGuiItem(Material.BLUE_STAINED_GLASS_PANE, "§7我只是个装饰物");
        for (int i = 0; i < 54; i++) {
            chest.setItem(i, glass);
        }

        put(chest, player, 10, "lengbanlist.ban", "ban",
                createGuiItem(Material.REDSTONE_BLOCK, "§a添加封禁", "§7/lban add", "§7聊天栏向导：玩家/IP → 时间 → 原因"));
        put(chest, player, 12, "lengbanlist.banip", "ban-ip",
                createGuiItem(Material.LAVA_BUCKET, "§c封禁IP", "§7/lban add <IP>", "§7聊天栏向导：IP → 时间 → 原因"));
        put(chest, player, 14, "lengbanlist.mute", "mute",
                createGuiItem(Material.BARRIER, "§a禁言玩家", "§7/lban mute", "§7聊天栏向导：玩家 → 时间 → 原因"));
        put(chest, player, 16, "lengbanlist.warn", "warn",
                createGuiItem(Material.PAPER, "§a警告玩家", "§7/lban warn", "§7聊天栏向导：玩家 → 原因"));

        put(chest, player, 19, "lengbanlist.unban", "unban",
                createGuiItem(Material.EMERALD_BLOCK, "§a解除封禁", "§7/lban remove", "§7聊天栏向导：输入玩家名或 IP"));
        put(chest, player, 21, "lengbanlist.mute", "mute",
                createGuiItem(Material.MILK_BUCKET, "§a解除禁言", "§7/lban unmute", "§7聊天栏向导：输入玩家名"));
        put(chest, player, 23, "lengbanlist.list", "ban",
                createGuiItem(Material.RED_WOOL, "§c封禁列表", "VIEW_BANS", "§7查看封禁玩家/IP列表"));
        put(chest, player, 25, "lengbanlist.listmute", "mute",
                createGuiItem(Material.GRAY_WOOL, "§c禁言列表", "VIEW_MUTES", "§7查看禁言玩家列表"));

        put(chest, player, 28, "lengbanlist.admin", "report",
                createGuiItem(Material.PAPER, "§e举报列表", "VIEW_REPORTS", "§7查看待处理举报列表"));
        put(chest, player, 30, "lengbanlist.freeze", "freeze",
                createGuiItem(Material.PACKED_ICE, "§b冻结列表", "VIEW_FREEZES", "§7查看当前被冻结的玩家"));
        put(chest, player, 32, "lengbanlist.freeze", "freeze",
                createGuiItem(Material.BLUE_ICE, "§b冻结玩家", "§7/lban freeze", "§7聊天栏向导：玩家 → 理由"));
        put(chest, player, 34, "lengbanlist.freeze", "freeze",
                createGuiItem(Material.ICE, "§b解除冻结", "§7/lban unfreeze", "§7聊天栏向导：输入玩家名或 all"));

        put(chest, player, 37, "lengbanlist.vanish", "vanish",
                createGuiItem(Material.FEATHER, "§d隐身/现身 (" + (plugin.getVanishManager().isVanished(player) ? "隐身中" : "可见") + ")",
                        "§7/lban vanish", "§7其他玩家完全看不见你（含手持物品与盔甲）"));
        put(chest, player, 39, "lengbanlist.model", "model",
                createGuiItem(Material.NAME_TAG, "§a切换模型 (" + ModelManager.getInstance().getCurrentModelName() + ")",
                        "§7/lban model", "§7当前模型: " + ModelManager.getInstance().getCurrentModelName()));
        put(chest, player, 41, "lengbanlist.list", "ban",
                createGuiItem(Material.WRITABLE_BOOK, "§a查看封禁名单", "§7/lban list", "§7在聊天栏输出封禁名单"));
        chest.setItem(43, createGuiItem(Material.BOOK, "§a帮助信息", "§7/lban help", "§7显示帮助信息"));

        put(chest, player, 46, "lengbanlist.toggle", "broadcast",
                createGuiItem(Material.LEVER, "§a切换自动广播 (" + (plugin.isBroadcastEnabled() ? "开启" : "关闭") + ")",
                        "§7/lban toggle", "§7开启或关闭自动广播"));
        put(chest, player, 48, "lengbanlist.broadcast", "broadcast",
                createGuiItem(Material.NOTE_BLOCK, "§a广播封禁人数", "§7/lban a", "§7广播当前封禁人数"));
        put(chest, player, 50, "lengbanlist.reload", "reload",
                createGuiItem(Material.COMPARATOR, "§a重新加载配置", "§7/lban reload", "§7重新加载插件配置"));
        chest.setItem(52, createGuiItem(Material.GOLD_INGOT, "§6赞助作者", ACTION_SPONSOR,
                "§7点击获取赞助链接：https://afdian.com/a/lengmc"));
    }

    private void put(Inventory chest, Player player, int slot, String permission, String feature, ItemStack item) {
        if (!Utils.canUse(plugin, player, permission, feature)) {
            return;
        }
        chest.setItem(slot, item);
    }

    private void renderGuiList(Player player, Inventory inventory, String view) {
        GuiSessionManager gui = plugin.getGuiSessionManager();
        int page = gui.getPage(player.getUniqueId(), view);

        ItemStack glass = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "§7 ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, glass);
        }

        int start = page * GUI_PAGE_SIZE;

        if ("bans".equals(view)) {
            List<Object> list = new ArrayList<>();
            list.addAll(plugin.getBanManager().getBanList());
            list.addAll(plugin.getBanManager().getBanIpList());
            for (int s = 0; s < GUI_CONTENT_SLOTS.length; s++) {
                int index = start + s;
                if (index >= list.size()) {
                    break;
                }
                Object entry = list.get(index);
                if (entry instanceof BanEntry) {
                    BanEntry ban = (BanEntry) entry;
                    inventory.setItem(GUI_CONTENT_SLOTS[s], createGuiItem(Material.RED_WOOL,
                            "§c" + ban.getTarget(),
                            "§7处理人：" + ban.getStaff(),
                            "§7原因：" + ban.getReason(),
                            "§7解封时间：" + TimeUtils.timestampToReadable(ban.getTime())));
                } else if (entry instanceof BanIpEntry) {
                    BanIpEntry banIp = (BanIpEntry) entry;
                    inventory.setItem(GUI_CONTENT_SLOTS[s], createGuiItem(Material.BLACK_WOOL,
                            "§c" + banIp.getIp(),
                            "§7处理人：" + banIp.getStaff(),
                            "§7原因：" + banIp.getReason(),
                            "§7解封时间：" + TimeUtils.timestampToReadable(banIp.getTime())));
                }
            }
        } else if ("mutes".equals(view)) {
            List<MuteEntry> list = plugin.getMuteManager().getMuteList();
            for (int s = 0; s < GUI_CONTENT_SLOTS.length; s++) {
                int index = start + s;
                if (index >= list.size()) {
                    break;
                }
                MuteEntry mute = list.get(index);
                inventory.setItem(GUI_CONTENT_SLOTS[s], createGuiItem(Material.GRAY_WOOL,
                        "§c" + mute.getTarget(),
                        "§7处理人：" + mute.getStaff(),
                        "§7原因：" + mute.getReason(),
                        "§7解禁时间：" + TimeUtils.timestampToReadable(mute.getTime())));
            }
        } else if ("freezes".equals(view)) {
            List<FreezeEntry> list = new ArrayList<>(plugin.getFreezeManager().all());
            for (int s = 0; s < GUI_CONTENT_SLOTS.length; s++) {
                int index = start + s;
                if (index >= list.size()) {
                    break;
                }
                FreezeEntry freeze = list.get(index);
                inventory.setItem(GUI_CONTENT_SLOTS[s], createGuiItem(Material.PACKED_ICE,
                        "§c" + freeze.player(),
                        "§7处理人：" + freeze.staff(),
                        "§7理由：" + freeze.reason(),
                        "§7冻结时间：" + TimeUtils.timestampToReadable(freeze.time())));
            }
        } else if ("reports".equals(view)) {
            List<ReportEntry> list = plugin.getReportManager().getPendingReports();
            for (int s = 0; s < GUI_CONTENT_SLOTS.length; s++) {
                int index = start + s;
                if (index >= list.size()) {
                    break;
                }
                ReportEntry report = list.get(index);
                inventory.setItem(GUI_CONTENT_SLOTS[s], createGuiItem(Material.PAPER,
                        "§e举报编号：" + report.getId(),
                        "§7" + REPORT_PREFIX + report.getId(),
                        "§7被举报人：" + report.getTarget(),
                        "§7举报人：" + report.getReporter(),
                        "§7原因：" + report.getReason()));
            }
        }

        int totalPages = guiTotalPages(view);
        inventory.setItem(SLOT_PREV, createGuiItem(Material.ARROW, "§e上一页", "PAGE_PREV", "§7第 " + (page + 1) + " / " + totalPages + " 页"));
        inventory.setItem(SLOT_BACK, createGuiItem(Material.BARRIER, "§c返回主菜单", "VIEW_MENU", "§7点击返回主菜单"));
        inventory.setItem(SLOT_INFO, createGuiItem(Material.PAPER, "§b" + (page + 1) + " / " + totalPages, "§7页码", "§7使用上一页/下一页按钮翻页"));
        inventory.setItem(SLOT_NEXT, createGuiItem(Material.ARROW, "§e下一页", "PAGE_NEXT", "§7第 " + (page + 1) + " / " + totalPages + " 页"));
    }

    private int guiTotalPages(String view) {
        int size;
        if ("bans".equals(view)) {
            size = plugin.getBanManager().getBanList().size() + plugin.getBanManager().getBanIpList().size();
        } else if ("mutes".equals(view)) {
            size = plugin.getMuteManager().getMuteList().size();
        } else if ("freezes".equals(view)) {
            size = plugin.getFreezeManager().count();
        } else if ("reports".equals(view)) {
            size = plugin.getReportManager().getPendingReports().size();
        } else {
            size = 0;
        }
        return Math.max(1, (size + GUI_PAGE_SIZE - 1) / GUI_PAGE_SIZE);
    }

    private ItemStack createGuiItem(Material material, String displayName, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(line);
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        GuiSessionManager gui = plugin.getGuiSessionManager();
        String view = gui.getView(player.getUniqueId());
        if (view != null && view.startsWith("alts:")) {
            return;
        }

        if (!plugin.isFeatureEnabled("chest-ui")) {
            plugin.sendFeatureDisabled(player);
            player.closeInventory();
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }
        ItemMeta clickMeta = clickedItem.getItemMeta();
        if (clickMeta.getLore() == null || clickMeta.getLore().isEmpty()) {
            return;
        }

        String command = clickMeta.getLore().get(0).replace("§7", "");
        if (command.isEmpty()) {
            return;
        }

        if (command.startsWith(REPORT_PREFIX)) {
            if (!Utils.canUse(plugin, player, "lengbanlist.admin", "report")) {
                Utils.sendMessage(player, plugin.prefix() + "§c不是你的工作喵！");
                return;
            }
            player.closeInventory();
            player.performCommand("lban handle " + command.substring(REPORT_PREFIX.length()) + " auto");
            return;
        }

        switch (command) {
            case "VIEW_BANS":
                if (!Utils.canUse(plugin, player, "lengbanlist.list", "ban")) {
                    Utils.sendMessage(player, plugin.prefix() + "§c不是你的工作喵！");
                    return;
                }
                gui.setView(player.getUniqueId(), "bans");
                gui.setPage(player.getUniqueId(), "bans", 0);
                renderGuiList(player, event.getView().getTopInventory(), "bans");
                return;
            case "VIEW_MUTES":
                if (!Utils.canUse(plugin, player, "lengbanlist.listmute", "mute")) {
                    Utils.sendMessage(player, plugin.prefix() + "§c不是你的工作喵！");
                    return;
                }
                gui.setView(player.getUniqueId(), "mutes");
                gui.setPage(player.getUniqueId(), "mutes", 0);
                renderGuiList(player, event.getView().getTopInventory(), "mutes");
                return;
            case "VIEW_FREEZES":
                if (!Utils.canUse(plugin, player, "lengbanlist.freeze", "freeze")) {
                    Utils.sendMessage(player, plugin.prefix() + "§c不是你的工作喵！");
                    return;
                }
                gui.setView(player.getUniqueId(), "freezes");
                gui.setPage(player.getUniqueId(), "freezes", 0);
                renderGuiList(player, event.getView().getTopInventory(), "freezes");
                return;
            case "VIEW_REPORTS":
                if (!Utils.canUse(plugin, player, "lengbanlist.admin", "report")) {
                    Utils.sendMessage(player, plugin.prefix() + "§c不是你的工作喵！");
                    return;
                }
                gui.setView(player.getUniqueId(), "reports");
                gui.setPage(player.getUniqueId(), "reports", 0);
                renderGuiList(player, event.getView().getTopInventory(), "reports");
                return;
            case "VIEW_MENU":
                gui.setView(player.getUniqueId(), "menu");
                gui.setPage(player.getUniqueId(), "menu", 0);
                renderGuiMenu(player, event.getView().getTopInventory());
                return;
            case "PAGE_PREV":
                if (view == null) {
                    return;
                }
                int prevPage = gui.getPage(player.getUniqueId(), view) - 1;
                if (prevPage < 0) {
                    return;
                }
                playClick(player);
                gui.setPage(player.getUniqueId(), view, prevPage);
                renderGuiList(player, event.getView().getTopInventory(), view);
                return;
            case "PAGE_NEXT":
                if (view == null) {
                    return;
                }
                int nextPage = gui.getPage(player.getUniqueId(), view) + 1;
                if (nextPage >= guiTotalPages(view)) {
                    return;
                }
                playClick(player);
                gui.setPage(player.getUniqueId(), view, nextPage);
                renderGuiList(player, event.getView().getTopInventory(), view);
                return;
            default:
                handleMenuAction(player, command);
                return;
        }
    }

    private void handleMenuAction(Player player, String command) {
        if (!command.startsWith(PREFIX_COMMAND)) {
            if (ACTION_SPONSOR.equals(command)) {
                player.closeInventory();
                player.spigot().sendMessage(
                        new net.md_5.bungee.api.chat.TextComponent(plugin.prefix() + "§6赞助作者："),
                        Utils.clickableUrl("§e【点击打开爱发电】", "https://afdian.com/a/lengmc")
                );
            }
            return;
        }

        playClick(player);
        player.closeInventory();
        switch (command) {
            case "/lban add":
                startWizard(player, "ban");
                break;
            case "/lban add <IP>":
                startWizard(player, "ipban");
                break;
            case "/lban remove":
                startWizard(player, "unban");
                break;
            case "/lban mute":
                startWizard(player, "mute");
                break;
            case "/lban unmute":
                startWizard(player, "unmute");
                break;
            case "/lban warn":
                startWizard(player, "warn");
                break;
            case "/lban freeze":
                startWizard(player, "freeze");
                break;
            case "/lban unfreeze":
                startWizard(player, "unfreeze");
                break;
            case "/lban model":
                if (!Utils.canUse(plugin, player, "lengbanlist.model", "model")) {
                    Utils.sendMessage(player, plugin.prefix() + "§c不是你的工作喵！");
                    return;
                }
                ModelManager.getInstance().openModelSelectionUI(player);
                break;
            default:
                player.performCommand(command.substring(1));
                break;
        }
    }

    private void playClick(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void startWizard(Player player, String action) {
        plugin.getWizardManager().start(player, action);
    }
}

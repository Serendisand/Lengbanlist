package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.leng.Lengbanlist;
import org.leng.manager.GuiSessionManager;
import org.leng.manager.IpAssociationManager.AltAccount;
import org.leng.models.Model;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class AltsCommand implements CommandExecutor, Listener {
    private static final int PAGE_SIZE = 28;
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final Lengbanlist plugin;

    public AltsCommand(Lengbanlist plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isFeatureEnabled("alts")) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        if (!sender.hasPermission("lengbanlist.alts")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
            return true;
        }
        if (args.length < 1 || args[0].isEmpty()) {
            Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式：/alts <玩家名>");
            return true;
        }
        String target = args[0];
        if (target.contains(".")) {
            Utils.sendMessage(sender, plugin.prefix() + "§c§l参数应为玩家名，不能是 IP：/alts <玩家名>");
            return true;
        }
        execute(sender, target);
        return true;
    }

    public void execute(CommandSender sender, String target) {
        List<AltAccount> list = plugin.getIpAssociationManager().getAlts(target);
        if (!(sender instanceof Player)) {
            Model model = plugin.getModelManager().getCurrentModel();
            if (list.isEmpty()) {
                Utils.sendMessage(sender, model.getNoAlts(target));
            } else {
                Utils.sendMessage(sender, model.getAltsResult(target, list.size()));
                for (AltAccount alt : list) {
                    String status = alt.banned ? " §c[封禁中]" : (alt.currentIp ? " §a[当前同IP]" : " §7[历史IP]");
                    Utils.sendMessage(sender, " §f" + alt.name + status);
                }
            }
            return;
        }

        Player player = (Player) sender;
        GuiSessionManager gui = plugin.getGuiSessionManager();
        String view = "alts:" + target;
        Inventory inventory = Bukkit.createInventory(null, 54, "§bLengbanlist");
        renderPage(inventory, cappedList(list), view, 0);
        player.openInventory(inventory);
        gui.setView(player.getUniqueId(), view);
        gui.setPage(player.getUniqueId(), view, 0);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§bLengbanlist")) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        GuiSessionManager gui = plugin.getGuiSessionManager();
        String view = gui.getView(player.getUniqueId());
        if (view == null || !view.startsWith("alts:")) {
            return;
        }
        event.setCancelled(true);
        handleClick(player, event.getSlot(), event.getView().getTopInventory(), view);
    }

    public void handleClick(Player player, int slot, Inventory inventory, String view) {
        GuiSessionManager gui = plugin.getGuiSessionManager();
        if (slot == 45) {
            int page = gui.getPage(player.getUniqueId(), view);
            if (page > 0) {
                gui.setPage(player.getUniqueId(), view, page - 1);
                renderPage(inventory, cappedList(plugin.getIpAssociationManager().getAlts(targetOf(view))), view, page - 1);
            }
        } else if (slot == 53) {
            int page = gui.getPage(player.getUniqueId(), view);
            List<AltAccount> list = cappedList(plugin.getIpAssociationManager().getAlts(targetOf(view)));
            int totalPages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (page < totalPages - 1) {
                gui.setPage(player.getUniqueId(), view, page + 1);
                renderPage(inventory, list, view, page + 1);
            }
        } else if (slot == 48) {
            gui.setView(player.getUniqueId(), "menu");
            player.performCommand("lban open");
        }
    }

    private String targetOf(String view) {
        return view.startsWith("alts:") ? view.substring("alts:".length()) : "";
    }

    private List<AltAccount> cappedList(List<AltAccount> list) {
        int maxScan = Math.max(1, plugin.getConfig().getInt("alts.max-scan", 45));
        if (list.size() > maxScan) {
            return new ArrayList<>(list.subList(0, maxScan));
        }
        return list;
    }

    private void renderPage(Inventory inventory, List<AltAccount> list, String view, int page) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§7 ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, glass);
        }
        int totalPages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int start = page * PAGE_SIZE;
        for (int s = 0; s < CONTENT_SLOTS.length; s++) {
            int index = start + s;
            if (index >= list.size()) {
                break;
            }
            inventory.setItem(CONTENT_SLOTS[s], createAltItem(list.get(index)));
        }
        inventory.setItem(45, createControlItem(Material.ARROW, "§e上一页", "§7第 " + (page + 1) + " / " + totalPages + " 页"));
        inventory.setItem(49, createControlItem(Material.PAPER, "§b" + (page + 1) + " / " + totalPages, "§7共 " + list.size() + " 个账号"));
        inventory.setItem(53, createControlItem(Material.ARROW, "§e下一页", "§7第 " + (page + 1) + " / " + totalPages + " 页"));
        inventory.setItem(48, createControlItem(Material.BARRIER, "§c返回主菜单", "§7点击返回主菜单"));
    }

    private ItemStack createAltItem(AltAccount alt) {
        Material material = alt.banned ? Material.RED_WOOL : (alt.currentIp ? Material.GREEN_WOOL : Material.GRAY_WOOL);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f" + alt.name);
        List<String> lore = new ArrayList<>();
        if (alt.banned) {
            lore.add("§c封禁中");
        } else if (alt.currentIp) {
            lore.add("§a当前同 IP");
        } else {
            lore.add("§7历史 IP");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createControlItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> loreList = new ArrayList<>();
        loreList.add(lore);
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }
}

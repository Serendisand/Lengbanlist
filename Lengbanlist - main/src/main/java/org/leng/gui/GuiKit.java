package org.leng.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.leng.object.ReportEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 新 GUI 共用的箱子工具：统一物品构建、PDC 动作存储和分页布局。
 */
public final class GuiKit {

    public static final int PAGE_SIZE = 28;
    public static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    public static final int SLOT_PREV = 45;
    public static final int SLOT_BACK = 48;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private final NamespacedKey actionKey;
    private final NamespacedKey dataKey;
    private final NamespacedKey extraKey;

    public GuiKit(Plugin plugin) {
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.dataKey = new NamespacedKey(plugin, "gui_data");
        this.extraKey = new NamespacedKey(plugin, "gui_extra");
    }

    public ItemStack item(Material material, String name, String action, String... lore) {
        return item(material, name, List.of(lore), action, null, null);
    }

    public ItemStack item(Material material, String name, List<String> lore, String action, String data) {
        return item(material, name, lore, action, data, null);
    }

    public ItemStack item(Material material, String name, List<String> lore,
                          String action, String data, String extra) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            apply(meta, name, lore, action, data, extra);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack head(Player target, String name, List<String> lore, String action, String data) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta raw = item.getItemMeta();
        if (raw instanceof SkullMeta meta) {
            meta.setOwningPlayer(target);
            apply(meta, name, lore, action, data, null);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void apply(ItemMeta meta, String name, List<String> lore,
                       String action, String data, String extra) {
        if (name != null) {
            meta.setDisplayName(name);
        }
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(new ArrayList<>(lore));
        }
        if (action != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        }
        if (data != null) {
            meta.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, data);
        }
        if (extra != null) {
            meta.getPersistentDataContainer().set(extraKey, PersistentDataType.STRING, extra);
        }
    }

    public String action(ItemStack item) {
        return read(item, actionKey);
    }

    public String data(ItemStack item) {
        return read(item, dataKey);
    }

    public String extra(ItemStack item) {
        return read(item, extraKey);
    }

    private String read(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static void fill(Inventory inventory, Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, item);
        }
    }

    public static String shortId(String id) {
        if (id == null || id.isEmpty()) {
            return "?";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
    }

    public static String statusText(String status) {
        if (status == null || status.isBlank()) {
            return "§e" + ReportEntry.STATUS_PENDING;
        }
        return switch (status) {
            case ReportEntry.STATUS_PENDING -> "§e" + status;
            case ReportEntry.STATUS_ACCEPTED -> "§b" + status;
            case ReportEntry.STATUS_READ -> "§9" + status;
            case ReportEntry.STATUS_CLOSED -> "§7" + status;
            case ReportEntry.STATUS_HANDLED -> "§a" + status;
            default -> "§7" + status;
        };
    }

    public static List<String> wrap(String text, int width) {
        String clean = ChatColor.stripColor(text == null ? "" : text)
                .replace('\n', ' ').trim();
        List<String> lines = new ArrayList<>();
        if (clean.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < clean.length(); i++) {
            line.append(clean.charAt(i));
            if (line.length() >= width) {
                lines.add(line.toString());
                line.setLength(0);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    public static void click(Player player) {
        if (player != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }
}

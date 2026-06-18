package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelManager {
    private static ModelManager instance;
    private static Map<String, Model> models = new HashMap<>();
    private static Model currentModel;
    private boolean enabled = true;

    public static ModelManager getInstance() {
        if (instance == null) {
            instance = new ModelManager();
        }
        return instance;
    }

    private ModelManager() {
        loadModel("Default");
        loadModel("English");
        loadModel("HuTao");
        loadModel("Furina");
        loadModel("Zhongli");
        loadModel("Keqing");
        loadModel("Xiao");
        loadModel("Ayaka");
        loadModel("Zero");
        loadModel("Herta");
        loadModel("Nahida");
        loadModel("Klee");
        loadModel("YaeMiko");

        String modelName = Lengbanlist.getInstance().getConfig().getString("Model", "Default");
        switchModel(modelName.toLowerCase());
    }

    public static void loadModel(String modelName) {
        try {
            Class<?> modelClass = Class.forName("org.leng.models." + modelName);
            Model model = (Model) modelClass.getDeclaredConstructor().newInstance();
            models.put(modelName.toLowerCase(), model);
        } catch (Exception e) {
            Lengbanlist.getInstance().getServer().getConsoleSender().sendMessage("§c模型 " + modelName + " 加载失败！");
            e.printStackTrace();
        }
    }

    public static Model getCurrentModel() {
        return currentModel;
    }

    public static String getCurrentModelName() {
        return currentModel != null ? currentModel.getName() : "未知模型";
    }

    public static void switchModel(String modelName) {
        String lowerCaseModelName = modelName.toLowerCase();
        if (models.containsKey(lowerCaseModelName)) {
            currentModel = models.get(lowerCaseModelName);
            Lengbanlist.getInstance().getConfig().set("Model", currentModel.getName());
            Lengbanlist.getInstance().saveConfig();
            Lengbanlist.getInstance().getServer().getConsoleSender().sendMessage("§a已切换到模型: " + currentModel.getName());
        } else {
            Lengbanlist.getInstance().getServer().getConsoleSender().sendMessage("§c模型 " + modelName + " 不存在。");
        }
    }

    public Map<String, Model> getModels() {
        return models;
    }

    public void reloadModel() {
        String modelName = Lengbanlist.getInstance().getConfig().getString("Model", "Default");
        switchModel(modelName.toLowerCase());
        Lengbanlist.getInstance().getServer().getConsoleSender().sendMessage("§a模型已重新加载，当前模型: " + currentModel.getName());
    }

    public static Material getModelMaterial(String modelName) {
        FileConfiguration config = Lengbanlist.getInstance().getConfig();
        String materialName = config.getString("models." + modelName.toLowerCase() + ".material", "PAPER");
        Material material = Material.matchMaterial(materialName);
        return material != null ? material : Material.PAPER;
    }

    public void openModelSelectionUI(Player player) {
        Inventory modelSelectionUI = Bukkit.createInventory(null, 27, "§b选择模型");

        ItemStack glass = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < 27; i++) {
            modelSelectionUI.setItem(i, glass);
        }

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int index = 0;
        for (Map.Entry<String, Model> entry : models.entrySet()) {
            if (index >= slots.length) {
                break;
            }
            String modelName = entry.getKey();
            ItemStack item = new ItemStack(getModelMaterial(modelName));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a" + modelName);
                List<String> lore = new ArrayList<>();
                lore.add("§7点击选择此模型");
                lore.add("§7当前模型: " + getCurrentModelName());
                meta.setLore(lore);
                if (entry.getValue() == currentModel) {
                    meta.addEnchant(Enchantment.PROTECTION, 1, true);
                }
                item.setItemMeta(meta);
            }
            modelSelectionUI.setItem(slots[index], item);
            index++;
        }
        player.openInventory(modelSelectionUI);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

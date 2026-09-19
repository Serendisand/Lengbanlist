package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.models.CustomModel;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelManager {
    private static final String BASE_FILE_NAME = "_base.yml";

    private static ModelManager instance;
    private static Map<String, Model> models = new HashMap<>();
    private static Model currentModel;
    private static FileConfiguration baseConfig;
    private boolean enabled = true;

    public static ModelManager getInstance() {
        if (instance == null) {
            instance = new ModelManager();
        }
        return instance;
    }

    private ModelManager() {

        loadBaseConfig();
        loadCustomModels();

        String modelName = Lengbanlist.getInstance().getConfig().getString("Model", "Default");
        if (!models.containsKey(modelName.toLowerCase())) {

            Lengbanlist.getInstance().getLogger().warning("配置的模型 " + modelName + " 当前不可用，回退到 Default（可 /lban models refresh 拉取）");
            modelName = "Default";
        }
        switchModel(modelName.toLowerCase());
    }

    private void loadBaseConfig() {
        YamlConfiguration merged = new YamlConfiguration();
        YamlConfiguration jarDefaults = loadJarDefaults();
        if (jarDefaults != null) {
            copyValues(merged, jarDefaults);
        }

        File userBase = new File(Lengbanlist.getInstance().getDataFolder(), "models/" + BASE_FILE_NAME);
        if (userBase.exists()) {
            try {
                copyValues(merged, YamlConfiguration.loadConfiguration(userBase));
            } catch (Exception e) {
                Lengbanlist.getInstance().getLogger().warning("加载自定义全局文本 " + BASE_FILE_NAME + " 失败：" + e.getMessage());
            }
        }
        baseConfig = merged;
    }

    private YamlConfiguration loadJarDefaults() {
        try (InputStream in = Lengbanlist.getInstance().getResource("models/" + BASE_FILE_NAME)) {
            if (in == null) {
                return null;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml;
        } catch (IOException | InvalidConfigurationException e) {
            Lengbanlist.getInstance().getLogger().warning("加载内置全局文本失败：" + e.getMessage());
            return null;
        }
    }

    private void copyValues(ConfigurationSection target, ConfigurationSection source) {
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key)) {
                continue;
            }
            target.set(key, source.get(key));
        }
    }

    public static FileConfiguration getBaseConfig() {
        return baseConfig;
    }

    private void loadCustomModels() {

        models.clear();

        File modelsDir = new File(Lengbanlist.getInstance().getDataFolder(), "models");
        if (!modelsDir.exists() || !modelsDir.isDirectory()) {
            return;
        }

        File[] yamlFiles = modelsDir.listFiles((dir, name) -> (name.endsWith(".yml") || name.endsWith(".yaml"))
                && !name.startsWith("_"));
        if (yamlFiles == null || yamlFiles.length == 0) {
            return;
        }

        for (File file : yamlFiles) {
            try {
                FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String modelName = yaml.getString("name");
                if (modelName != null) {
                    modelName = modelName.trim();
                }
                if (modelName == null || modelName.isEmpty()) {
                    Lengbanlist.getInstance().getLogger().warning("跳过模型文件 " + file.getName() + "：缺少 'name' 字段");
                    continue;
                }

                String lowerName = modelName.toLowerCase();

                if (models.containsKey(lowerName)) {
                    Lengbanlist.getInstance().getLogger().warning("跳过自定义模型 " + modelName + "（来自 " + file.getName() + "）：与内置模型 " + lowerName + " 冲突，内置模型优先");
                    continue;
                }

                CustomModel model = new CustomModel(modelName, yaml, baseConfig);
                models.put(lowerName, model);
            } catch (Exception e) {
                if (e instanceof org.bukkit.configuration.InvalidConfigurationException) {
                    Lengbanlist.getInstance().getLogger().warning("跳过模型文件 " + file.getName() + "：YAML 格式错误，请检查语法");
                } else {
                    Lengbanlist.getInstance().getLogger().warning("加载自定义模型文件 " + file.getName() + " 失败：" + e.getMessage());
                }
            }
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

        loadBaseConfig();
        loadCustomModels();

        String modelName = Lengbanlist.getInstance().getConfig().getString("Model", "Default");
        switchModel(modelName.toLowerCase());
        Lengbanlist.getInstance().getServer().getConsoleSender().sendMessage("§a模型已重新加载，当前模型: " + currentModel.getName());
    }

    private static final Map<String, Material> MODEL_MATERIALS = new HashMap<>();

    static {
        MODEL_MATERIALS.put("default", Material.PAPER);
        MODEL_MATERIALS.put("english", Material.BOOK);
        MODEL_MATERIALS.put("hutao", Material.RED_TULIP);
        MODEL_MATERIALS.put("furina", Material.WATER_BUCKET);
        MODEL_MATERIALS.put("zhongli", Material.DEEPSLATE);
        MODEL_MATERIALS.put("keqing", Material.AMETHYST_SHARD);
        MODEL_MATERIALS.put("xiao", Material.FEATHER);
        MODEL_MATERIALS.put("ayaka", Material.SNOWBALL);
        MODEL_MATERIALS.put("zero", Material.REDSTONE);
        MODEL_MATERIALS.put("herta", Material.KNOWLEDGE_BOOK);
        MODEL_MATERIALS.put("nahida", Material.OAK_SAPLING);
        MODEL_MATERIALS.put("klee", Material.TNT);
        MODEL_MATERIALS.put("yaemiko", Material.PINK_DYE);
    }

    public static Material getModelMaterial(String modelName) {
        Material material = MODEL_MATERIALS.get(modelName.toLowerCase());
        return material != null ? material : Material.PAPER;
    }

    private static Enchantment selectionGlow;

    public static void applySelectionGlow(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        if (selectionGlow == null) {
            selectionGlow = Enchantment.getByKey(NamespacedKey.minecraft("protection"));
        }
        if (selectionGlow != null) {
            meta.addEnchant(selectionGlow, 1, true);
        }
    }

    public static final String MODEL_UI_TITLE = "§b选择模型";
    public static final String MODEL_VIEW = "models";

    private static final int[] MODEL_UI_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public void openModelSelectionUI(Player player) {
        openModelSelectionUI(player, Lengbanlist.getInstance().getGuiSessionManager()
                .getPage(player.getUniqueId(), MODEL_VIEW));
    }

    public void openModelSelectionUI(Player player, int page) {
        List<String> names = new ArrayList<>(models.keySet());
        names.sort(String::compareTo);

        int perPage = MODEL_UI_SLOTS.length;
        int totalPages = Math.max(1, (names.size() + perPage - 1) / perPage);
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        GuiSessionManager sessions = Lengbanlist.getInstance().getGuiSessionManager();
        sessions.setView(player.getUniqueId(), MODEL_VIEW);
        sessions.setPage(player.getUniqueId(), MODEL_VIEW, safePage);

        Inventory modelSelectionUI = Bukkit.createInventory(null, 54, MODEL_UI_TITLE);

        ItemStack glass = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < 54; i++) {
            modelSelectionUI.setItem(i, glass);
        }

        for (int slot = 0; slot < perPage; slot++) {
            int index = safePage * perPage + slot;
            if (index >= names.size()) {
                break;
            }
            String modelName = names.get(index);
            ItemStack item = new ItemStack(getModelMaterial(modelName));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a" + modelName);
                List<String> lore = new ArrayList<>();
                lore.add("§7MODEL_PICK:" + modelName);
                lore.add("§7当前模型: " + getCurrentModelName());
                meta.setLore(lore);
                if (models.get(modelName) == currentModel) {
                    applySelectionGlow(meta);
                }
                item.setItemMeta(meta);
            }
            modelSelectionUI.setItem(MODEL_UI_SLOTS[slot], item);
        }

        modelSelectionUI.setItem(45, pageItem(Material.ARROW, "§e上一页", "MODEL_PAGE_PREV",
                "§7第 " + (safePage + 1) + " / " + totalPages + " 页"));
        modelSelectionUI.setItem(49, pageItem(Material.PAPER, "§b" + (safePage + 1) + " / " + totalPages,
                "MODEL_PAGE_INFO", "§7共 " + names.size() + " 个可用模型"));
        modelSelectionUI.setItem(53, pageItem(Material.ARROW, "§e下一页", "MODEL_PAGE_NEXT",
                "§7第 " + (safePage + 1) + " / " + totalPages + " 页"));
        player.openInventory(modelSelectionUI);
    }

    private ItemStack pageItem(Material material, String displayName, String action, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + action);
            lore.add(description);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public int modelPageCount() {
        return Math.max(1, (models.size() + MODEL_UI_SLOTS.length - 1) / MODEL_UI_SLOTS.length);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

package org.leng.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.object.FreezeEntry;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager {

    private static final int TITLE_FADE_IN_TICKS = 10;
    private static final int TITLE_STAY_TICKS = 70;
    private static final int TITLE_FADE_OUT_TICKS = 20;

    private final Lengbanlist plugin;
    private final File file;
    private final Map<UUID, FreezeEntry> frozen = new ConcurrentHashMap<>();

    public FreezeManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "frozen.yml");
        load();
    }

    public boolean isFrozen(Player player) {
        return player != null && frozen.containsKey(player.getUniqueId());
    }

    public FreezeEntry get(Player player) {
        return player == null ? null : frozen.get(player.getUniqueId());
    }

    public FreezeEntry getByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (FreezeEntry entry : frozen.values()) {
            if (entry.player().equalsIgnoreCase(name)) {
                return entry;
            }
        }
        return null;
    }

    public Collection<FreezeEntry> all() {
        return List.copyOf(frozen.values());
    }

    public int count() {
        return frozen.size();
    }

    public boolean freeze(Player target, String staff, String reason) {
        FreezeEntry entry = new FreezeEntry(
                target.getUniqueId().toString(),
                target.getName(),
                staff,
                System.currentTimeMillis(),
                reason == null ? "" : reason);
        if (frozen.putIfAbsent(target.getUniqueId(), entry) != null) {
            return false;
        }
        save();
        return true;
    }

    public void notify(Player target, String reason) {
        Model model = ModelManager.getCurrentModel();
        if (model == null) {
            return;
        }
        String message = model.getFreezeNotify(reason);
        String title = model.getFreezeTitle();
        String subtitle = model.getFreezeSubtitle(reason);
        SchedulerUtils.runTask(plugin, target, () -> {
            if (!target.isOnline()) {
                return;
            }
            Utils.sendMessage(target, message);
            target.sendTitle(title, subtitle, TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS);
        });
    }

    public boolean unfreeze(FreezeEntry entry) {
        UUID uuid = entry == null ? null : entry.uniqueId();
        if (uuid == null) {
            return false;
        }
        if (frozen.remove(uuid) == null) {
            return false;
        }
        save();
        return true;
    }

    public int unfreezeAll() {
        int removed = frozen.size();
        if (removed == 0) {
            return 0;
        }
        frozen.clear();
        save();
        return removed;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("frozen");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("跳过无效的冻结记录: " + key);
                continue;
            }
            frozen.put(uuid, new FreezeEntry(
                    key,
                    section.getString(key + ".player", ""),
                    section.getString(key + ".staff", "CONSOLE"),
                    section.getLong(key + ".time", System.currentTimeMillis()),
                    section.getString(key + ".reason", "")));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, FreezeEntry> entry : frozen.entrySet()) {
            String base = "frozen." + entry.getKey() + ".";
            FreezeEntry value = entry.getValue();
            yaml.set(base + "player", value.player());
            yaml.set(base + "staff", value.staff());
            yaml.set(base + "reason", value.reason());
            yaml.set(base + "time", value.time());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存冻结记录失败: " + e.getMessage());
        }
    }
}

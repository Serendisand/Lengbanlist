package org.leng.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.models.Model;
import org.leng.object.FreezeEntry;
import org.leng.utils.ErrorLog;
import org.leng.utils.SchedulerUtils;
import org.leng.utils.Utils;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager {

    private static final int TITLE_FADE_IN_TICKS = 10;
    private static final int TITLE_STAY_TICKS = 70;
    private static final int TITLE_FADE_OUT_TICKS = 20;

    private final Lengbanlist plugin;
    private final DatabaseManager db;
    private final Map<String, FreezeEntry> frozen = new ConcurrentHashMap<>();

    public FreezeManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        importLegacyFile();
        reload();
    }

    public boolean isFrozen(Player player) {
        return player != null && frozen.containsKey(key(player.getName()));
    }

    public FreezeEntry get(Player player) {
        return player == null ? null : frozen.get(key(player.getName()));
    }

    public FreezeEntry getByName(String name) {
        return name == null || name.isEmpty() ? null : frozen.get(key(name));
    }

    public Collection<FreezeEntry> all() {
        return List.copyOf(frozen.values());
    }

    public int count() {
        return frozen.size();
    }

    public boolean freeze(Player target, String staff, String reason) {
        String key = key(target.getName());
        if (frozen.containsKey(key)) {
            return false;
        }
        FreezeEntry entry = new FreezeEntry(
                target.getName(),
                staff,
                System.currentTimeMillis(),
                reason == null ? "" : reason);
        try {
            db.saveFreeze(entry);
        } catch (Exception e) {
            ErrorLog.record(plugin, "冻结记录写入数据库失败: " + target.getName(), e);
            return false;
        }
        frozen.put(key, entry);
        return true;
    }

    public boolean unfreeze(FreezeEntry entry) {
        if (entry == null) {
            return false;
        }
        String key = entry.key();
        try {
            db.deleteFreeze(entry.player());
        } catch (Exception e) {
            ErrorLog.record(plugin, "冻结记录删除失败: " + entry.player(), e);
            return false;
        }
        return frozen.remove(key) != null;
    }

    public int unfreezeAll() {
        if (frozen.isEmpty()) {
            return 0;
        }
        try {
            db.deleteAllFreezes();
        } catch (Exception e) {
            ErrorLog.record(plugin, "清空冻结记录失败", e);
            return 0;
        }
        int removed = frozen.size();
        frozen.clear();
        return removed;
    }

    public boolean reload() {
        try {
            List<FreezeEntry> entries = db.loadFreezes();
            frozen.clear();
            for (FreezeEntry entry : entries) {
                frozen.put(entry.key(), entry);
            }
            return true;
        } catch (Exception e) {
            ErrorLog.record(plugin, "读取冻结记录失败，沿用现有缓存", e);
            return false;
        }
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

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private void importLegacyFile() {
        File file = new File(plugin.getDataFolder(), "frozen.yml");
        if (!file.exists()) {
            return;
        }
        int imported = 0;
        try {
            ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("frozen");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    String player = section.getString(key + ".player", "");
                    if (player.isEmpty()) {
                        continue;
                    }
                    db.saveFreeze(new FreezeEntry(
                            player,
                            section.getString(key + ".staff", "CONSOLE"),
                            section.getLong(key + ".time", System.currentTimeMillis()),
                            section.getString(key + ".reason", "")));
                    imported++;
                }
            }
            File backup = new File(plugin.getDataFolder(), "frozen.yml.migrated");
            if (file.renameTo(backup)) {
                plugin.getLogger().info("已把 frozen.yml 中的 " + imported + " 条冻结记录迁移到数据库");
            }
        } catch (Exception e) {
            ErrorLog.record(plugin, "迁移 frozen.yml 到数据库失败", e);
        }
    }
}

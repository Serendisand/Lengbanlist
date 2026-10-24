package org.leng.migration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.leng.Lengbanlist;
import org.leng.util.ErrorLog;

import java.io.File;
import java.io.IOException;

/**
 * 配置键迁移入口。
 *
 * <p>新增迁移时在这里加一个独立方法，并在 {@code Lengbanlist.onLoad()} 对应配置文件
 * 加载完成之后调用；不要让迁移逻辑散落在插件主类里。
 */
public class ConfigMigrationManager {

    private final Lengbanlist plugin;

    public ConfigMigrationManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    /** config.yml 自身的键迁移。 */
    public void migrateRootConfig() {
        FileConfiguration config = plugin.getConfig();
        if (!config.contains("update-check.enabled")) {
            config.set("update-check.enabled", config.getBoolean("features.update-check", true));
            plugin.saveConfig();
        }
    }

    /**
     * storage.yml 的旧键迁移：旧 {@code database} 段与 {@code history-retention-days}。
     *
     * @param storageFile 已加载的 storage.yml 文件，用于写回
     */
    public void migrateStorageConfig(File storageFile) {
        FileConfiguration root = plugin.getConfig();
        FileConfiguration storage = plugin.getStorageConfig();
        if (storage == null) {
            return;
        }

        boolean migrated = false;
        ConfigurationSection legacyDatabase = root.getConfigurationSection("database");
        if (legacyDatabase != null) {
            for (String key : legacyDatabase.getKeys(true)) {
                if (legacyDatabase.isConfigurationSection(key)) {
                    continue;
                }
                storage.set("database." + key, legacyDatabase.get(key));
            }
            root.set("database", null);
            migrated = true;
        }
        if (root.contains("history-retention-days")) {
            storage.set("database.retention.history-days", root.getInt("history-retention-days", 7));
            root.set("history-retention-days", null);
            migrated = true;
        }
        if (!migrated) {
            return;
        }

        try {
            storage.save(storageFile);
            plugin.saveConfig();
            plugin.getLogger().info(plugin.consoleText("storage-migrated"));
        } catch (IOException e) {
            plugin.getLogger().warning(plugin.consoleText("storage-migrate-failed",
                    "error", e.getMessage()));
        }
    }

    /**
     * 旧版放在 config.yml 的广播设置迁移到 broadcast.yml。
     *
     * @param broadcastFile 已加载的 broadcast.yml 文件，用于写回
     */
    public void migrateBroadcastConfig(File broadcastFile) {
        FileConfiguration root = plugin.getConfig();
        FileConfiguration broadcast = plugin.getBroadcastFC();
        if (broadcast == null) {
            return;
        }

        boolean broadcastChanged = false;
        boolean configChanged = false;

        if (!broadcast.contains("opensendtime")) {
            broadcast.set("opensendtime", root.getBoolean("opensendtime", true));
            broadcastChanged = true;
        }
        if (!broadcast.contains("sendtime")) {
            broadcast.set("sendtime", root.getInt("sendtime", 5));
            broadcastChanged = true;
        }
        if (root.contains("opensendtime")) {
            root.set("opensendtime", null);
            configChanged = true;
        }
        if (root.contains("sendtime")) {
            root.set("sendtime", null);
            configChanged = true;
        }

        if (broadcastChanged) {
            try {
                broadcast.save(broadcastFile);
            } catch (IOException e) {
                ErrorLog.record(plugin, "迁移广播设置到 broadcast.yml 失败", e);
                return;
            }
        }
        if (configChanged) {
            plugin.saveConfig();
        }
        if (broadcastChanged || configChanged) {
            plugin.getLogger().info("广播设置已迁移到 broadcast.yml");
        }
    }
}

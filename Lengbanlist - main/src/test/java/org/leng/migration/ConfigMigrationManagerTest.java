package org.leng.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigMigrationManagerTest {

    @Mock Lengbanlist plugin;

    @TempDir Path tempDir;

    @Test
    void rootConfigGetsUpdateCheckKey() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("features.update-check", false);
        when(plugin.getConfig()).thenReturn(root);

        new ConfigMigrationManager(plugin).migrateRootConfig();

        assertTrue(root.contains("update-check.enabled"));
        assertFalse(root.getBoolean("update-check.enabled"));
        verify(plugin).saveConfig();
    }

    @Test
    void storageConfigMigratesLegacyDatabaseAndRetention() throws Exception {
        YamlConfiguration root = new YamlConfiguration();
        root.createSection("database");
        root.set("database.type", "mysql");
        root.set("history-retention-days", 14);
        YamlConfiguration storage = new YamlConfiguration();
        File storageFile = tempDir.resolve("storage.yml").toFile();
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("ConfigMigrationManagerTest"));
        when(plugin.getConfig()).thenReturn(root);
        when(plugin.getStorageConfig()).thenReturn(storage);

        new ConfigMigrationManager(plugin).migrateStorageConfig(storageFile);

        assertEquals("mysql", storage.getString("database.type"));
        assertEquals(14, storage.getInt("database.retention.history-days"));
        assertFalse(root.contains("database"));
        assertFalse(root.contains("history-retention-days"));
        assertTrue(storageFile.exists());
        verify(plugin).saveConfig();
    }

    @Test
    void broadcastConfigMovesSettingsOutOfRootConfig() throws Exception {
        YamlConfiguration root = new YamlConfiguration();
        root.set("opensendtime", false);
        root.set("sendtime", 7);
        YamlConfiguration broadcast = new YamlConfiguration();
        File broadcastFile = tempDir.resolve("broadcast.yml").toFile();
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("ConfigMigrationManagerTest"));
        when(plugin.getConfig()).thenReturn(root);
        when(plugin.getBroadcastFC()).thenReturn(broadcast);

        new ConfigMigrationManager(plugin).migrateBroadcastConfig(broadcastFile);

        assertFalse(broadcast.getBoolean("opensendtime", true));
        assertEquals(7, broadcast.getInt("sendtime"));
        assertFalse(root.contains("opensendtime"));
        assertFalse(root.contains("sendtime"));
        assertTrue(broadcastFile.exists());
        verify(plugin).saveConfig();
    }

    @Test
    void broadcastConfigDoesNotOverwriteExistingBroadcastValues() throws Exception {
        YamlConfiguration root = new YamlConfiguration();
        root.set("opensendtime", false);
        root.set("sendtime", 7);
        YamlConfiguration broadcast = new YamlConfiguration();
        broadcast.set("opensendtime", true);
        broadcast.set("sendtime", 3);
        File broadcastFile = tempDir.resolve("broadcast.yml").toFile();
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("ConfigMigrationManagerTest"));
        when(plugin.getConfig()).thenReturn(root);
        when(plugin.getBroadcastFC()).thenReturn(broadcast);

        new ConfigMigrationManager(plugin).migrateBroadcastConfig(broadcastFile);

        assertTrue(broadcast.getBoolean("opensendtime"));
        assertEquals(3, broadcast.getInt("sendtime"));
        assertFalse(root.contains("opensendtime"));
        assertFalse(root.contains("sendtime"));
    }
}

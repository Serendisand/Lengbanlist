package org.leng.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class StorageMigrationManagerTest {

    @Mock Lengbanlist plugin;
    @TempDir Path tempDir;

    private DatabaseManager db;

    @BeforeEach
    void setUp() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.type", "sqlite");
        config.set("database.sqlite.file", "lengbanlist.db");
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("StorageMigrationTest"));
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        db = new DatabaseManager(plugin);
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void yamlImport_runsOnlyOnceAcrossRestarts() throws Exception {
        long end = System.currentTimeMillis() + 600_000L;
        Files.writeString(tempDir.resolve("ban-list.yml"),
                "ban-list:\n  - \"Alice:staff:" + end + ":作弊:false\"\n", StandardCharsets.UTF_8);

        new StorageMigrationManager(plugin, db).migrateYamlIfNeeded();
        assertEquals(1, db.countBanHistory("Alice"));

        new StorageMigrationManager(plugin, db).migrateYamlIfNeeded();
        assertEquals(1, db.countBanHistory("Alice"), "重复迁移会给封禁表塞重复行，把历史翻倍");
        assertTrue(db.isPlayerBanned("Alice"));
    }

    @Test
    void ipAndMuteYaml_importOnceEach() throws Exception {
        long end = System.currentTimeMillis() + 600_000L;
        Files.writeString(tempDir.resolve("banip-list.yml"),
                "banip-list:\n  - \"203.0.113.9:staff:" + end + ":VPN:false\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("mute-list.yml"),
                "mute-list:\n  - \"Bob:staff:" + end + ":刷屏\"\n", StandardCharsets.UTF_8);

        StorageMigrationManager migration = new StorageMigrationManager(plugin, db);
        migration.migrateYamlIfNeeded();
        migration.migrateYamlIfNeeded();

        assertTrue(db.isIpBanned("203.0.113.9"));
        assertEquals(1, db.countIpBanHistory("203.0.113.9"));
        assertEquals(1, db.getAllMutes().size());
    }
}

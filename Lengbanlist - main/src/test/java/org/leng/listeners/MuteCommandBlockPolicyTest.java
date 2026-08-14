package org.leng.listeners;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MuteCommandBlockPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void testIsBlockedMatchesConfigured() {
        List<String> blocked = Arrays.asList("/msg", "/tell");
        assertTrue(MuteCommandBlockPolicy.isBlocked("/msg hi", blocked, true));
        assertTrue(MuteCommandBlockPolicy.isBlocked("/tell user hi", blocked, true));
    }

    @Test
    void testIsBlockedNoMatch() {
        List<String> blocked = Arrays.asList("/msg");
        assertFalse(MuteCommandBlockPolicy.isBlocked("/ban user", blocked, true));
    }

    @Test
    void testIsBlockedCaseInsensitive() {
        List<String> blocked = Arrays.asList("/msg");
        assertTrue(MuteCommandBlockPolicy.isBlocked("/MSG hi", blocked, true));
    }

    @Test
    void testIsBlockedStripsSlash() {
        List<String> blocked = Arrays.asList("msg");
        assertTrue(MuteCommandBlockPolicy.isBlocked("/msg hi", blocked, true));
    }

    @Test
    void testIsBlockedStripsNamespace() {
        List<String> blocked = Arrays.asList("me");
        assertTrue(MuteCommandBlockPolicy.isBlocked("/minecraft:me hi", blocked, true));
    }

    @Test
    void testIsBlockedNoStripNamespace() {
        List<String> blocked = Arrays.asList("me");
        assertFalse(MuteCommandBlockPolicy.isBlocked("/minecraft:me hi", blocked, false));
        
        List<String> blockedFull = Arrays.asList("minecraft:me");
        assertTrue(MuteCommandBlockPolicy.isBlocked("/minecraft:me hi", blockedFull, false));
    }

    @Test
    void testConfiguredCommandStripsLeadingSlash() {
        List<String> blocked = Arrays.asList("/msg");
        assertTrue(MuteCommandBlockPolicy.isBlocked("/msg hi", blocked, true));
    }

    @Test
    void testMigrateLegacyConfigMigrates() throws Exception {
        File chatConfigFile = tempDir.resolve("chatconfig.yml").toFile();
        FileConfiguration chatConfig = new YamlConfiguration();
        FileConfiguration legacyConfig = new YamlConfiguration();
        
        legacyConfig.set("mute-blocked-commands", Arrays.asList("/msg", "/tell"));
        
        boolean migrated = MuteCommandBlockPolicy.migrateLegacyConfig(chatConfig, legacyConfig, chatConfigFile);
        
        assertTrue(migrated);
        assertEquals(legacyConfig.getStringList("mute-blocked-commands"), chatConfig.getStringList("mute-blocked-commands"));
        
        // Verify file actually saved
        FileConfiguration savedConfig = new YamlConfiguration();
        savedConfig.load(chatConfigFile);
        assertEquals(legacyConfig.getStringList("mute-blocked-commands"), savedConfig.getStringList("mute-blocked-commands"));
    }

    @Test
    void testMigrateLegacyConfigNoMigration() throws Exception {
        File chatConfigFile = tempDir.resolve("chatconfig.yml").toFile();
        FileConfiguration chatConfig = new YamlConfiguration();
        FileConfiguration legacyConfig = new YamlConfiguration();
        
        chatConfig.set("mute-blocked-commands", Arrays.asList("/chat"));
        legacyConfig.set("mute-blocked-commands", Arrays.asList("/msg"));
        
        boolean migrated = MuteCommandBlockPolicy.migrateLegacyConfig(chatConfig, legacyConfig, chatConfigFile);
        
        assertFalse(migrated);
        assertEquals(Arrays.asList("/chat"), chatConfig.getStringList("mute-blocked-commands"));
    }
}

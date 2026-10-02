package org.leng.extension;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * extensions.yml 的迁移与读取优先级。
 *
 * <p>这是升级路径上最容易出事的一环：迁移写错，用户所有功能开关会一起回到默认值。
 * 其中 {@link #manualEditsSurviveReload()} 专门盯住"迁移判定必须来自文件本身"——
 * 若改成看调用方传入的配置对象，用户手改过的开关会在下次启动被静默覆盖。
 */
class ExtensionsConfigTest {

    @TempDir Path tmp;

    private File extensionsFile() {
        return tmp.resolve(ExtensionsConfig.FILE_NAME).toFile();
    }

    private static ConfigurationSection legacy(String... keysAndValues) {
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            config.set("features." + keysAndValues[i], Boolean.parseBoolean(keysAndValues[i + 1]));
        }
        return config.getConfigurationSection("features");
    }

    @Test
    void migratesExistingFeatureChoicesOnFirstRun() {
        ExtensionsConfig.Loaded loaded = ExtensionsConfig.load(
                extensionsFile(), legacy("ban", "true", "vanish", "false", "sync", "false"));

        assertEquals(3, loaded.count());
        assertNotNull(loaded.message());
        assertTrue(extensionsFile().exists(), "迁移后必须落盘，否则下次启动会再迁一次");
        assertTrue(loaded.config().getBoolean("enabled.ban"));
        assertFalse(loaded.config().getBoolean("enabled.vanish"), "用户关掉的功能不能被迁移成开启");
        assertFalse(loaded.config().getBoolean("enabled.sync"));
    }

    @Test
    void migrationIsNotRepeated() {
        ExtensionsConfig.load(extensionsFile(), legacy("ban", "true"));

        ExtensionsConfig.Loaded second = ExtensionsConfig.load(extensionsFile(), legacy("ban", "true"));

        assertEquals(0, second.count(), "已有 enabled 段就不该再迁移");
        assertNull(second.message());
    }

    @Test
    void manualEditsSurviveReload() throws Exception {
        ExtensionsConfig.load(extensionsFile(), legacy("ban", "true"));

        // 用户后来手动把 ban 关掉并落盘
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(extensionsFile());
        onDisk.set("enabled.ban", false);
        onDisk.save(extensionsFile());

        ExtensionsConfig.Loaded reloaded = ExtensionsConfig.load(extensionsFile(), legacy("ban", "true"));

        assertEquals(0, reloaded.count());
        assertFalse(reloaded.config().getBoolean("enabled.ban"),
                "用户手改的值必须保留——迁移判定要看文件，不能看调用方传来的对象");
    }

    @Test
    void emptyLegacySectionStillCreatesFile() {
        ExtensionsConfig.Loaded loaded = ExtensionsConfig.load(extensionsFile(), null);

        assertEquals(0, loaded.count());
        assertNull(loaded.message(), "没有可迁移项时不该打扰用户");
        assertTrue(extensionsFile().exists());
    }

    @Test
    void extensionsFileWinsOverLegacy() {
        YamlConfiguration extensions = new YamlConfiguration();
        extensions.set("enabled.ban", false);
        YamlConfiguration config = new YamlConfiguration();
        config.set("features.ban", true);

        assertFalse(ExtensionsConfig.isEnabled(extensions, config, "ban"),
                "新文件是权威来源，旧键不该覆盖它");
    }

    @Test
    void missingKeyFallsBackToLegacyThenToTrue() {
        YamlConfiguration extensions = new YamlConfiguration();
        YamlConfiguration config = new YamlConfiguration();
        config.set("features.vanish", false);

        assertFalse(ExtensionsConfig.isEnabled(extensions, config, "vanish"),
                "新文件里没有的键要回退到 config.yml 的 features.*");

        assertTrue(ExtensionsConfig.isEnabled(extensions, config, "freeze"),
                "两处都没有的键沿用旧默认值 true");
    }

    @Test
    void toleratesNullInputs() {
        assertFalse(ExtensionsConfig.isEnabled(null, null, null));
        assertFalse(ExtensionsConfig.isEnabled(null, null, ""));
        assertTrue(ExtensionsConfig.isEnabled(null, null, "ban"));
    }
}

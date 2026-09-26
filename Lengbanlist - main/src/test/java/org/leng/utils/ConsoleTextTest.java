package org.leng.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleTextTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private static final List<String> MODEL_KEYS = List.of(
            "ready", "loading", "tip", "placeholder-hook", "auto-update", "shutdown", "farewell");

    private static Set<String> placeholders(String template) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static YamlConfiguration resource(String path) {
        try (InputStream in = ConsoleTextTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " 不在测试类路径上");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("读取 " + path + " 失败: " + e.getMessage(), e);
        }
    }

    @Test
    void everyEntryHasBothLanguages() {
        for (ConsoleText entry : ConsoleText.values()) {
            assertFalse(entry.chinese().isEmpty(), entry.name() + " 缺中文");
            assertFalse(entry.english().isEmpty(), entry.name() + " 缺英文");
            assertFalse(entry.chinese().equals(entry.english()), entry.name() + " 的中英文是同一串，八成漏翻了");
        }
    }

    @Test
    void bothLanguagesUseTheSamePlaceholders() {
        for (ConsoleText entry : ConsoleText.values()) {
            assertEquals(placeholders(entry.chinese()), placeholders(entry.english()),
                    entry.name() + " 两种语言的占位符不一致");
        }
    }

    @Test
    void placeholdersAreFilled() {
        assertEquals("模型 Default", ConsoleText.fill("模型 {model}", "model", "Default"));
        assertEquals("版本 2.1.2 | 模型 Default",
                ConsoleText.fill("版本 {version} | 模型 {model}", "version", "2.1.2", "model", "Default"));
        assertEquals("原样 {unknown}", ConsoleText.fill("原样 {unknown}", "model", "Default"));
        assertEquals("空值", ConsoleText.fill("空值{tail}", "tail", null));
        assertEquals("", ConsoleText.fill(null));
    }

    @Test
    void lookupIsCaseInsensitiveAndTolerantOfDashes() {
        assertEquals(ConsoleText.LOADING, ConsoleText.of("loading"));
        assertEquals(ConsoleText.PLACEHOLDER_HOOK, ConsoleText.of("placeholder-hook"));
        assertEquals(ConsoleText.AUTO_UPDATE, ConsoleText.of("AUTO_UPDATE"));
        assertNull(ConsoleText.of("nope"));
        assertNull(ConsoleText.of(null));
        assertEquals("", ConsoleText.builtIn("nope"));
    }

    @Test
    void modelFilesOnlyUseKnownConsoleKeys() {
        for (String path : new String[]{"/models/_base.yml", "/models/english.yml"}) {
            ConfigurationSection console = resource(path).getConfigurationSection("console");
            assertNotNull(console, path + " 缺少 console 段");
            for (String key : console.getKeys(false)) {
                assertNotNull(ConsoleText.of(key), path + " 里的 console." + key + " 在内置表里没有对应项");
                assertFalse(console.getString(key, "").isEmpty(), path + " 的 console." + key + " 是空的");
            }
            assertTrue(console.getKeys(false).containsAll(MODEL_KEYS),
                    path + " 缺少这些控制台键: " + MODEL_KEYS);
        }
    }

    @Test
    void modelFilesMatchTheBuiltInDefaults() {
        for (String path : new String[]{"/models/_base.yml", "/models/english.yml"}) {
            ConfigurationSection console = resource(path).getConfigurationSection("console");
            boolean chinese = path.contains("_base");
            for (String key : MODEL_KEYS) {
                ConsoleText entry = ConsoleText.of(key);
                assertEquals(chinese ? entry.chinese() : entry.english(), console.getString(key),
                        "console." + key + " 与 ConsoleText 里的默认值不一致；改文案要两处一起改（" + path + "）");
            }
        }
    }
}

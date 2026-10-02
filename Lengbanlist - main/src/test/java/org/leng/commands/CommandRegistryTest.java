package org.leng.commands;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CommandRegistryTest {

    @Mock Lengbanlist plugin;

    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        // 真实运行里 JavaPlugin.getLogger() 不会是 null；这里要给出真实值，
        // 否则扩展注册表在出错路径上会因为拿不到 logger 而再次抛 NPE，掩盖真正的失败原因。
        lenient().when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getGlobal());
        lenient().when(plugin.getPluginVersion()).thenReturn("2.1.6");
        lenient().when(plugin.getAltsCommand()).thenReturn(mock(GuiCommands.Alts.class));
        registry = new CommandRegistry(plugin);
    }

    private List<CommandRegistry.Spec> specs() {
        List<CommandRegistry.Spec> specs = registry.specs();
        assertFalse(specs.isEmpty(), "命令表不该是空的");
        return specs;
    }

    private static YamlConfiguration resource(String path) throws Exception {
        try (InputStream in = CommandRegistryTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " 不在测试类路径上");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static ConfigurationSection section(String path, String name) throws Exception {
        ConfigurationSection found = resource(path).getConfigurationSection(name);
        assertNotNull(found, path + " 缺少 " + name + " 段");
        return found;
    }

    @Test
    void everySpecIsFullyDeclared() {
        for (CommandRegistry.Spec spec : specs()) {
            assertFalse(spec.name().isEmpty(), "命令名不能为空");
            assertFalse(spec.feature().isEmpty(), spec.name() + " 缺少功能键");
            assertFalse(spec.permission().isEmpty(), spec.name() + " 缺少权限节点");
            assertFalse(spec.usage().isEmpty(), spec.name() + " 缺少用法");
            assertNotNull(spec.executor(), spec.name() + " 没有执行器");
        }
    }

    @Test
    void commandNamesAndUsagesAreUnique() {
        Set<String> names = new HashSet<>();
        Set<String> usages = new HashSet<>();
        for (CommandRegistry.Spec spec : specs()) {
            assertTrue(names.add(spec.name()), "命令名重复: " + spec.name());
            assertTrue(usages.add(spec.usage()), "用法重复: " + spec.usage());
        }
    }

    @Test
    void permissionsAreDeclaredInPluginYml() throws Exception {
        ConfigurationSection declared = section("/plugin.yml", "permissions");
        for (CommandRegistry.Spec spec : specs()) {
            assertTrue(declared.contains(spec.permission()),
                    spec.name() + " 的权限节点 " + spec.permission() + " 没写进 plugin.yml，玩家会拿不到权限");
        }
    }

    @Test
    void featureKeysExistInConfigYml() throws Exception {
        ConfigurationSection features = section("/config.yml", "features");
        for (CommandRegistry.Spec spec : specs()) {
            assertTrue(features.contains(spec.feature()),
                    spec.name() + " 的功能键 features." + spec.feature() + " 没写进 config.yml，开关会一直是默认值");
        }
    }

    @Test
    void helpFeaturesCoverEveryCommand() {
        for (CommandRegistry.Spec spec : specs()) {
            String feature = CommandRegistry.featureForUsage(spec.usage());
            assertNotNull(feature, spec.name() + " 的用法串匹配不到 HELP_FEATURES，/lban help 会漏掉这一行");
            assertTrue(feature.equals(spec.feature()),
                    spec.name() + " 在 HELP_FEATURES 里记的是 " + feature + "，但命令表里是 " + spec.feature());
        }
    }
}

package org.leng.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.leng.api.CommandSpec;
import org.leng.api.ExtensionContext;
import org.leng.api.LengbanlistExtension;
import org.leng.extension.ExtensionRegistry;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * 扩展认领 {@code /lban <sub>} 子命令（{@link CommandSpec#parent}）。
 *
 * <p>这条机制存在的唯一理由是<b>保住既有命令界面</b>：{@code /lban vanish} 不能因为
 * vanish 搬进扩展就变成 {@code /vanish}。因此这里重点验证三件事：
 *
 * <ol>
 *   <li>子命令带 {@code parent} 标记，不会被当成顶层命令占用命令名</li>
 *   <li>功能未生效时子命令不被认领（落到核心的"未知子命令"分支）</li>
 *   <li>没有权限时不执行，但仍算"已认领"（否则会漏出核心的未知提示）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ExtensionSubcommandTest {

    private static final String PERMISSION = "lengbanlist.vanish";

    @Mock Lengbanlist plugin;
    @Mock CommandSender sender;

    private ExtensionRegistry extensions;
    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getGlobal());
        lenient().when(plugin.getPluginVersion()).thenReturn("2.1.6");
        lenient().when(sender.hasPermission(PERMISSION)).thenReturn(true);
        extensions = new ExtensionRegistry(plugin);
        registry = new CommandRegistry(plugin, extensions);
    }

    private void installSubcommand(String name, String feature, CommandExecutor executor) throws Exception {
        extensions.register(new LengbanlistExtension() {
            @Override
            public String id() {
                return feature;
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public Set<String> features() {
                return Set.of(feature);
            }

            @Override
            public void onEnable(ExtensionContext context) {
                context.commands().register(CommandSpec.sub(name, "lban", feature, PERMISSION,
                        "/lban " + name, "测试子命令", executor));
            }
        });
    }

    private static CommandExecutor recording(AtomicReference<String> seen) {
        return (s, c, label, args) -> {
            seen.set(label + "|" + String.join(",", args));
            return true;
        };
    }

    private void givenVanishActive() {
        lenient().when(plugin.isFeatureEnabled("vanish")).thenReturn(true);
    }

    @Test
    void subcommandIsMarkedWithParentSoItNeverTakesATopLevelName() throws Exception {
        installSubcommand("vanish", "vanish", (s, c, l, a) -> true);
        givenVanishActive();

        registry.refreshSubcommands();

        assertTrue(registry.subcommandNames().contains("vanish"), "生效的子命令应被收录");
        assertTrue(registry.specs().stream().anyMatch(s -> "vanish".equals(s.name()) && s.isSubcommand()),
                "必须带 parent 标记，refresh() 才会跳过它、不去占用顶层命令名");
    }

    @Test
    void dispatchForwardsTheSubcommandLabelAndTailArgs() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        installSubcommand("vanish", "vanish", recording(seen));
        givenVanishActive();
        registry.refreshSubcommands();

        boolean claimed = registry.dispatchSubcommand("lban", "vanish", sender, "lban",
                new String[]{"vanish", "on"});

        assertTrue(claimed);
        assertEquals("lban vanish|on", seen.get(),
                "执行器应拿到 'lban vanish' 作 label，且参数里不含子命令名本身");
    }

    @Test
    void subcommandNameIsMatchedCaseInsensitively() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        installSubcommand("vanish", "vanish", recording(seen));
        givenVanishActive();
        registry.refreshSubcommands();

        assertTrue(registry.dispatchSubcommand("lban", "VaNiSh", sender, "lban", new String[]{"VaNiSh"}));
        assertEquals("lban VaNiSh|", seen.get());
    }

    @Test
    void unknownSubcommandIsNotClaimed() {
        registry.refreshSubcommands();

        assertFalse(registry.dispatchSubcommand("lban", "nope", sender, "lban", new String[]{"nope"}),
                "没被认领时返回 false，调用方才会走自己的未知提示");
    }

    @Test
    void disabledFeatureIsNotClaimed() throws Exception {
        installSubcommand("vanish", "vanish", (s, c, l, a) -> true);
        lenient().when(plugin.isFeatureEnabled("vanish")).thenReturn(false);

        registry.refreshSubcommands();

        assertFalse(registry.subcommandNames().contains("vanish"), "功能关闭时不该收录");
        assertFalse(registry.dispatchSubcommand("lban", "vanish", sender, "lban", new String[]{"vanish"}));
    }

    @Test
    void deniedPermissionIsClaimedButNotExecuted() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        installSubcommand("vanish", "vanish", recording(seen));
        givenVanishActive();
        registry.refreshSubcommands();

        CommandSender denied = org.mockito.Mockito.mock(CommandSender.class);
        lenient().when(denied.hasPermission(PERMISSION)).thenReturn(false);

        boolean claimed = registry.dispatchSubcommand("lban", "vanish", denied, "lban",
                new String[]{"vanish"});

        assertTrue(claimed, "无权限也算已认领，否则会漏出核心的未知子命令提示");
        assertNull(seen.get(), "但绝不能执行");
    }

    @Test
    void otherParentIsNeverClaimed() throws Exception {
        installSubcommand("vanish", "vanish", (s, c, l, a) -> true);
        givenVanishActive();
        registry.refreshSubcommands();

        assertFalse(registry.dispatchSubcommand("other", "vanish", sender, "other", new String[]{"vanish"}),
                "只服务 /lban 这个父命令");
    }

    @Test
    void unregisterAllDropsSubcommands() throws Exception {
        installSubcommand("vanish", "vanish", (s, c, l, a) -> true);
        givenVanishActive();
        registry.refreshSubcommands();
        assertFalse(registry.subcommandNames().isEmpty());

        registry.unregisterAll();

        assertTrue(registry.subcommandNames().isEmpty(), "停服/停用时子命令要一并清掉");
    }
}

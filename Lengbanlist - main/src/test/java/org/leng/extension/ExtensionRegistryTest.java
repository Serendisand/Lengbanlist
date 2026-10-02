package org.leng.extension;

import org.bukkit.command.CommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.leng.api.CommandSpec;
import org.leng.api.ExtensionContext;
import org.leng.api.ExtensionLoadException;
import org.leng.api.LengbanlistExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * 扩展注册表与统一门控。
 *
 * <p>重点是那张四象限表：<b>已安装 / 未安装</b> × <b>开关开 / 关</b>。
 * 改造前只有"开关"一个维度，而且缺失的键默认放行，导致关不掉、也说不清。
 */
@ExtendWith(MockitoExtension.class)
class ExtensionRegistryTest {

    private static final String CORE_VERSION = "2.1.6";

    @Mock Lengbanlist plugin;

    private ExtensionRegistry registry;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getGlobal());
        lenient().when(plugin.getPluginVersion()).thenReturn(CORE_VERSION);
        registry = new ExtensionRegistry(plugin);
    }

    private static CommandExecutor noop() {
        return (sender, command, label, args) -> true;
    }

    private static CommandSpec spec(String name, String feature, String permission) {
        return CommandSpec.of(name, feature, permission, "/" + name, name + " 描述", noop());
    }

    /** 一个可控的假扩展。 */
    private static final class FakeExtension implements LengbanlistExtension {

        private final String id;
        private final String requiredApi;
        private final List<CommandSpec> specs = new ArrayList<>();
        private boolean failOnEnable;
        private boolean disableCalled;

        FakeExtension(String id) {
            this(id, "");
        }

        FakeExtension(String id, String requiredApi) {
            this.id = id;
            this.requiredApi = requiredApi;
        }

        FakeExtension with(CommandSpec... commandSpecs) {
            specs.addAll(List.of(commandSpecs));
            return this;
        }

        FakeExtension failing() {
            this.failOnEnable = true;
            return this;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String version() {
            return "1.0.0";
        }

        @Override
        public String requiredApi() {
            return requiredApi;
        }

        @Override
        public void onEnable(ExtensionContext context) throws Exception {
            if (failOnEnable) {
                throw new IllegalStateException("故意失败");
            }
            context.commands().register(specs.toArray(new CommandSpec[0]));
        }

        @Override
        public void onDisable() {
            disableCalled = true;
        }
    }

    // ------------------------------------------------------------ 注册

    @Test
    void registersCommandsAndExposesThem() throws Exception {
        FakeExtension ext = new FakeExtension("vanish").with(spec("vanish", "vanish", "lengbanlist.vanish"));

        ExtensionContext context = registry.register(ext);

        assertNotNull(context);
        assertEquals("vanish", context.extensionId());
        assertEquals(1, registry.commandSpecs().size());
        assertEquals("vanish", registry.ownerOf("vanish"));
        assertTrue(registry.isRegistered("vanish"));
    }

    @Test
    void duplicateIdIsRejected() throws Exception {
        registry.register(new FakeExtension("freeze"));

        ExtensionLoadException error = assertThrows(ExtensionLoadException.class,
                () -> registry.register(new FakeExtension("freeze")));

        assertTrue(error.getMessage().contains("freeze"));
    }

    @Test
    void incompatibleApiRangeIsRejectedWithReadableReason() {
        FakeExtension ext = new FakeExtension("webpanel", "[3.0,4.0)");

        ExtensionLoadException error = assertThrows(ExtensionLoadException.class,
                () -> registry.register(ext));

        assertTrue(error.getMessage().contains("[3.0,4.0)"), "原因里要写清要求的范围: " + error.getMessage());
        assertTrue(error.getMessage().contains(CORE_VERSION), "原因里要写清当前核心版本");
        assertFalse(registry.isRegistered("webpanel"));
    }

    @Test
    void compatibleApiRangeIsAccepted() throws Exception {
        registry.register(new FakeExtension("sync", "[2.0,3.0)"));
        assertTrue(registry.isRegistered("sync"));
    }

    @Test
    void failedEnableRollsBackCompletely() {
        FakeExtension ext = new FakeExtension("chestui")
                .with(spec("open", "chest-ui", "lengbanlist.open"))
                .failing();

        assertThrows(ExtensionLoadException.class, () -> registry.register(ext));

        assertFalse(registry.isRegistered("chestui"), "失败的注册必须完整回滚");
        assertTrue(registry.commandSpecs().isEmpty(), "不得留下半截命令");
        assertNull(registry.ownerOf("chest-ui"));
    }

    @Test
    void unregisterRemovesCommandsAndCallsOnDisable() throws Exception {
        FakeExtension ext = new FakeExtension("broadcast").with(spec("a", "broadcast", "lengbanlist.broadcast"));
        registry.register(ext);

        registry.unregister("broadcast");

        assertFalse(registry.isRegistered("broadcast"));
        assertTrue(registry.commandSpecs().isEmpty());
        assertTrue(ext.disableCalled, "必须回调扩展的 onDisable，让它释放资源");
    }

    @Test
    void reRegisteringSameCommandNameIsIdempotent() throws Exception {
        registry.register(new FakeExtension("x").with(
                spec("dup", "x", "p"),
                spec("dup", "x", "p")));

        assertEquals(1, registry.commandSpecs().size(), "同名命令应覆盖而不是叠加");
    }

    @Test
    void incompleteSpecIsRejected() throws Exception {
        FakeExtension ext = new FakeExtension("broken")
                .with(new CommandSpec("bad", "", "perm", "/bad", "d", List.of(), noop(), null));

        assertThrows(ExtensionLoadException.class, () -> registry.register(ext));
    }

    @Test
    void tabCompleterFallsBackToExecutor() {
        CommandExecutor both = new CommandExecutor() {
            @Override
            public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                                     String label, String[] args) {
                return true;
            }
        };
        // 只有 executor 的情况：补全器为 null
        CommandSpec plain = CommandSpec.of("a", "f", "p", "/a", "d", both);
        assertNull(plain.tabCompleter());

        // executor 同时实现 TabCompleter：应自动沿用（Bukkit 里 TabExecutor 就是两者合一）
        CommandSpec selfCompleting = CommandSpec.of("b", "f", "p", "/b", "d",
                new org.bukkit.command.TabExecutor() {
                    @Override
                    public boolean onCommand(org.bukkit.command.CommandSender sender,
                                             org.bukkit.command.Command command, String label, String[] args) {
                        return true;
                    }

                    @Override
                    public List<String> onTabComplete(org.bukkit.command.CommandSender sender,
                                                      org.bukkit.command.Command command, String alias, String[] args) {
                        return List.of();
                    }
                });
        assertNotNull(selfCompleting.tabCompleter(), "executor 同时实现 TabCompleter 时应自动沿用");
    }

    // ------------------------------------------------------------ 统一门控四象限

    @Test
    void installedAndSwitchOnIsActive() throws Exception {
        registry.register(new FakeExtension("freeze").with(spec("f", "freeze", "p")));
        lenient().when(plugin.isFeatureEnabled("freeze")).thenReturn(true);

        assertTrue(registry.isFeatureActive("freeze"));
    }

    @Test
    void installedButSwitchOffIsInactive() throws Exception {
        registry.register(new FakeExtension("freeze").with(spec("f", "freeze", "p")));
        lenient().when(plugin.isFeatureEnabled("freeze")).thenReturn(false);

        assertFalse(registry.isFeatureActive("freeze"), "扩展装了但开关关了，功能不该生效");
    }

    @Test
    void unknownFeatureKeyFallsBackToSwitchOnly() {
        // 没有任何提供者认领 freeze。Phase 0 阶段还有 22 个功能尚未登记为提供者，
        // 若此时把"无提供者"当作"未安装"而返回 false，这些功能会被整体关停、
        // 直接破坏既有行为。所以未知键必须沿用旧语义（只看开关）。
        lenient().when(plugin.isFeatureEnabled("freeze")).thenReturn(true);

        assertFalse(registry.isRegistered("freeze"));
        assertNull(registry.ownerOf("freeze"));
        assertTrue(registry.isFeatureActive("freeze"));
    }

    @Test
    void unregisteredExtensionNoLongerOwnsItsFeature() throws Exception {
        registry.register(new FakeExtension("freeze").with(spec("f", "freeze", "p")));
        lenient().when(plugin.isFeatureEnabled("freeze")).thenReturn(true);
        assertEquals("freeze", registry.ownerOf("freeze"));
        assertTrue(registry.isFeatureActive("freeze"));

        registry.unregister("freeze");

        assertNull(registry.ownerOf("freeze"), "卸载后不该再认领该功能键");
        assertFalse(registry.isRegistered("freeze"));
    }

    @Test
    void unregisterAllClearsEverything() throws Exception {
        registry.register(new FakeExtension("a").with(spec("a1", "a", "p")));
        registry.register(new FakeExtension("b").with(spec("b1", "b", "p")));

        registry.unregisterAll();

        assertTrue(registry.ids().isEmpty());
        assertTrue(registry.commandSpecs().isEmpty());
    }
}

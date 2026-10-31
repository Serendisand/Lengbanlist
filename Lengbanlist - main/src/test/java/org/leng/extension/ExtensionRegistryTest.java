package org.leng.extension;

import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.leng.api.ApiVersion;
import org.leng.api.CommandSpec;
import org.leng.api.ExtensionContext;
import org.leng.api.ExtensionLoadException;
import org.leng.api.LengbanlistExtension;
import org.leng.api.SelfGatedExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * 扩展注册表与统一门控。
 *
 * <p>重点是那张四象限表：<b>已安装 / 未安装</b> × <b>开关开 / 关</b>。
 * 改造前只有"开关"一个维度，而且缺失的键默认放行，导致关不掉、也说不清。
 */
@ExtendWith(MockitoExtension.class)
class ExtensionRegistryTest {

    private static final String API_VERSION = ApiVersion.CONTRACT_VERSION;

    @Mock Lengbanlist plugin;

    private ExtensionRegistry registry;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getGlobal());
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
        assertTrue(error.getMessage().contains(API_VERSION), "原因里要写清当前契约版本");
        assertFalse(registry.isRegistered("webpanel"));
    }

    @Test
    void compatibleApiRangeIsAccepted() throws Exception {
        registry.register(new FakeExtension("sync", "[2.0,3.0)"));
        assertTrue(registry.isRegistered("sync"));
    }

    /** 契约判断跟插件版本脱钩：插件发 3.0 不该把按 2.x 契约写的扩展挡掉。 */
    @Test
    void pluginVersionDoesNotAffectContractCheck() throws Exception {
        lenient().when(plugin.getPluginVersion()).thenReturn("3.0.0");

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

    // ------------------------------------------------------------ 自带开关的扩展

    /** 开关自己说了算的假扩展，用来钉住"核心配置不再参与判断"。 */
    private static final class SelfGatedFake implements LengbanlistExtension, SelfGatedExtension {

        private final String id;
        private boolean switchOn;
        private Consumer<ExtensionContext> onEnableHook = context -> {
        };

        SelfGatedFake(String id, boolean switchOn) {
            this.id = id;
            this.switchOn = switchOn;
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
        public Set<String> features() {
            return Set.of(id);
        }

        @Override
        public void onEnable(ExtensionContext context) {
            onEnableHook.accept(context);
            context.commands().register(spec("lua-" + id, id, "p"));
        }

        @Override
        public boolean isFeatureEnabled(String feature) {
            return switchOn;
        }
    }

    @Test
    void selfGatedExtensionIgnoresCoreConfig() throws Exception {
        registry.register(new SelfGatedFake("kit", true));
        // 核心配置里根本没有 kit 这个键，就算有也不该被读
        lenient().when(plugin.isFeatureEnabled("kit")).thenReturn(false);

        assertTrue(registry.isFeatureActive("kit"),
                "自带开关的扩展不该被核心 config.yml 的取值影响——否则开关又有两个来源了");
    }

    @Test
    void selfGatedExtensionCanTurnItselfOff() throws Exception {
        registry.register(new SelfGatedFake("kit", false));
        lenient().when(plugin.isFeatureEnabled("kit")).thenReturn(true);

        assertFalse(registry.isFeatureActive("kit"), "扩展自己的开关关掉就该失效");
    }

    @Test
    void selfGatedSwitchIsReReadEveryTime() throws Exception {
        SelfGatedFake ext = new SelfGatedFake("kit", true);
        registry.register(ext);
        assertTrue(registry.isFeatureActive("kit"));

        ext.switchOn = false;

        assertFalse(registry.isFeatureActive("kit"),
                "开关必须每次现读，不能在注册时快照——脚本改了配置重载后要立刻生效");
    }

    @Test
    void ownFeatureKeyAnswersActiveDuringOnEnable() throws Exception {
        AtomicBoolean active = new AtomicBoolean();
        SelfGatedFake ext = new SelfGatedFake("kit", true);
        ext.onEnableHook = context -> active.set(context.isFeatureActive("kit"));

        registry.register(ext);

        assertTrue(active.get(), "onEnable 里问自己的开关时，功能键还没登记，也要答得出来");
    }

    @Test
    void legacyCoreToggleIsMigratedToExtensionConfig(@TempDir Path tempDir) throws Exception {
        YamlConfiguration core = new YamlConfiguration();
        core.set("features.kit", false);
        when(plugin.getConfig()).thenReturn(core);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        registry.register(new SelfGatedFake("kit", true));

        File configFile = new File(tempDir.toFile(), "extensions/kit/config.yml");
        assertTrue(configFile.isFile(), "迁移应生成扩展自己的配置文件");
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(configFile);
        assertFalse(migrated.getBoolean("features.kit", true), "旧核心开关的值要原样搬过去");
    }

    @Test
    void extensionOwnFeatureKeyWinsOverLegacyCoreKey(@TempDir Path tempDir) throws Exception {
        YamlConfiguration core = new YamlConfiguration();
        core.set("features.kit", false);
        when(plugin.getConfig()).thenReturn(core);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        File dir = new File(tempDir.toFile(), "extensions/kit");
        assertTrue(dir.mkdirs());
        YamlConfiguration own = new YamlConfiguration();
        own.set("features.kit", true);
        own.save(new File(dir, "config.yml"));

        registry.register(new SelfGatedFake("kit", true));

        YamlConfiguration after = YamlConfiguration.loadConfiguration(new File(dir, "config.yml"));
        assertTrue(after.getBoolean("features.kit"), "扩展已经自己写了这个键时，旧核心键不得覆盖它");
    }

    @Test
    void externalJavaExtensionsAreListedAndToggleable(@TempDir Path tempDir) throws Exception {
        Plugin owner = mock(Plugin.class, withSettings()
                .extraInterfaces(LengbanlistExtension.class, SelfGatedExtension.class));
        LengbanlistExtension external = (LengbanlistExtension) owner;
        when(external.id()).thenReturn("kit");
        when(external.name()).thenReturn("Kit");
        when(external.version()).thenReturn("1.0.0");
        when(external.requiredApi()).thenReturn("");
        when(external.features()).thenReturn(Set.of("kit"));
        when(((SelfGatedExtension) owner).isFeatureEnabled(anyString())).thenReturn(true);
        when(owner.getDataFolder()).thenReturn(tempDir.toFile());
        when(owner.getName()).thenReturn("Lengbanlist-Ext-Kit");

        registry.register(external);

        List<ExtensionRegistry.ExtensionInfo> listed = registry.externalExtensions();
        assertEquals(1, listed.size(), "只列外部 Java 扩展，内置提供者和 Lua 不在里面");
        assertEquals("kit", listed.get(0).id());
        assertTrue(listed.get(0).enabled());

        assertTrue(registry.setExternalEnabled("kit", false));

        YamlConfiguration saved = YamlConfiguration.loadConfiguration(
                new File(tempDir.toFile(), "config.yml"));
        assertFalse(saved.getBoolean("enabled", true), "开关要写进扩展自己的 config.yml");
    }
}

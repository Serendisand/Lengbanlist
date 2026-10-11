package org.leng.extension.lua;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.leng.api.CommandSpec;
import org.leng.api.ExtensionContext;
import org.leng.api.ExtensionLoadException;
import org.leng.api.LengbanlistExtension;
import org.leng.extension.ExtensionRegistry;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 脚本 → 核心的整条装载链：目录 → 沙箱 → 命令表 / 事件表。
 *
 * <p>跑的是真的 {@link ExtensionRegistry} 与真的 {@link LuaExtension}，只把
 * {@link Lengbanlist} 换成 mock（没有服务端也能验的部分全在这里）。要钉住的四件事：
 *
 * <ol>
 *   <li>脚本声明的命令，翻译成 {@link CommandSpec} 后字段不漏不错（含 {@code parent}）</li>
 *   <li>脚本崩、超时、要的 API 版本不对——<b>只死它自己</b>，不掀掉核心</li>
 *   <li>开关只在扩展自己的 config.yml 里，且"禁用"不等于"卸载"</li>
 *   <li>随 jar 发布的官方脚本本身能跑（语法、门面调用、事件名在这里就会红）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class LuaExtensionLoadTest {

    private static final String PREFIX = "§8[§6L§8] ";

    @Mock Lengbanlist plugin;

    @TempDir Path dataFolder;

    private ExtensionRegistry registry;
    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getLogger()).thenReturn(Logger.getGlobal());
        lenient().when(plugin.getPluginVersion()).thenReturn("2.1.6");
        lenient().when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        lenient().when(plugin.prefix()).thenReturn(PREFIX);
        registry = new ExtensionRegistry(plugin);
    }

    // ------------------------------------------------------------------ 工具

    private File write(String id, String script) throws Exception {
        Path directory = dataFolder.resolve("extensions").resolve(id);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(LuaExtension.ENTRY_FILE), script, StandardCharsets.UTF_8);
        return directory.toFile();
    }

    private LuaExtension install(String id, String script) throws Exception {
        LuaExtension extension = new LuaExtension(plugin, registry, write(id, script));
        registry.register(extension);
        return extension;
    }

    private CommandSender sender() {
        CommandSender sender = mock(CommandSender.class);
        lenient().when(sender.getName()).thenReturn("Uki");
        lenient().when(sender.hasPermission(anyString())).thenReturn(true);
        lenient().doAnswer(call -> {
            messages.add(call.getArgument(0));
            return null;
        }).when(sender).sendMessage(anyString());
        return sender;
    }

    private CommandSpec spec(String name) {
        return registry.commandSpecs().stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有注册出命令 " + name));
    }

    // ------------------------------------------------------------------ 命令声明

    @Test
    void commandDeclarationBecomesANormalCommandSpec() throws Exception {
        LuaExtension extension = install("tp", """
                leng.name("传送")
                leng.version("1.2.3")
                leng.api("1.0")
                leng.command{
                  name = "tp",
                  parent = "lban",
                  permission = "lengbanlist.admin",
                  usage = "/lban tp <玩家名>",
                  description = "传送到玩家身边",
                  aliases = {"tpa", "tpto"},
                  run = function(ctx) return true end,
                }
                """);

        CommandSpec spec = spec("tp");
        assertEquals("lban", spec.parent(), "子命令必须带上父命令，否则会去占顶层命令名");
        assertEquals("tp", spec.feature(), "功能键默认取扩展名，也就是目录名");
        assertEquals("lengbanlist.admin", spec.permission());
        assertEquals("/lban tp <玩家名>", spec.usage());
        assertEquals("传送到玩家身边", spec.description());
        assertEquals(List.of("tpa", "tpto"), spec.aliases());
        assertEquals("传送", extension.name());
        assertEquals("1.2.3", extension.version());
    }

    @Test
    void permissionFallsBackToTheFeatureKey() throws Exception {
        install("quiet", """
                leng.command{
                  name = "quiet",
                  parent = "lban",
                  run = function(ctx) return true end,
                }
                """);

        assertEquals("lengbanlist.quiet", spec("quiet").permission(),
                "没写权限时按 lengbanlist.<功能键> 生成，绝不留下空权限（空权限 = 人人可用）");
    }

    /** 没有 parent 的就是顶层命令，会真的占一个命令名。 */
    @Test
    void commandWithoutParentIsATopLevelCommand() throws Exception {
        install("sc", """
                leng.command{
                  name = "sc",
                  run = function(ctx) return true end,
                }
                """);

        assertEquals("", spec("sc").parent());
    }

    // ------------------------------------------------------------------ 回调

    @Test
    void scriptSeesArgsSenderAndRepliesThroughTheCorePrefix() throws Exception {
        install("echo", """
                leng.command{
                  name = "echo",
                  parent = "lban",
                  run = function(ctx)
                    ctx.reply(ctx.sender.name .. ":" .. table.concat(ctx.args, ","))
                    return true
                  end,
                }
                """);

        boolean handled = spec("echo").executor()
                .onCommand(sender(), null, "lban echo", new String[]{"a", "b"});

        assertTrue(handled);
        assertEquals(List.of(PREFIX + "Uki:a,b"), messages);
    }

    /**
     * 子命令返回 false 时不能再往上报 false：{@code /lban} 的分派会把它当成"没人认领"，
     * 于是脚本明明跑了、还提示了用法，玩家却看到"未知子命令"。
     */
    @Test
    void falseFromASubcommandStillClaimsTheCommand() throws Exception {
        install("grumpy", """
                leng.command{
                  name = "grumpy",
                  parent = "lban",
                  usage = "/lban grumpy <名字>",
                  run = function(ctx) return false end,
                }
                """);

        boolean handled = spec("grumpy").executor()
                .onCommand(sender(), null, "lban grumpy", new String[0]);

        assertTrue(handled);
        assertEquals(List.of(PREFIX + "§c用法: §f/lban grumpy <名字>"), messages);
    }

    @Test
    void tabCompletionComesFromTheScript() throws Exception {
        install("pick", """
                leng.command{
                  name = "pick",
                  parent = "lban",
                  usage = "/lban pick <名字>",
                  run = function(ctx) return true end,
                  complete = function(ctx) return {"甲", "乙"} end,
                }
                """);

        List<String> completed = spec("pick").tabCompleter()
                .onTabComplete(sender(), null, "lban pick", new String[]{"甲"});

        assertEquals(List.of("甲", "乙"), completed);
    }

    @Test
    void commandsCannotBeRegisteredFromACallback() throws Exception {
        install("sneaky", """
                leng.command{
                  name = "sneaky",
                  parent = "lban",
                  run = function(ctx)
                    leng.command{ name = "smuggled", run = function() return true end }
                    return true
                  end,
                }
                """);

        spec("sneaky").executor().onCommand(sender(), null, "lban sneaky", new String[0]);

        assertTrue(registry.commandSpecs().stream().noneMatch(s -> s.name().equals("smuggled")),
                "运行期注册命令会让命令表与帮助对不上，必须被挡住");
        assertTrue(messages.isEmpty(), "出错只记日志，不往玩家脸上甩栈");
    }

    // ------------------------------------------------------------------ 故障隔离

    @Test
    void syntaxErrorKillsOnlyThatExtension() throws Exception {
        ExtensionLoadException error = assertThrows(ExtensionLoadException.class,
                () -> install("broken", "leng.command{"));

        assertTrue(error.getMessage().contains("语法错误"), error.getMessage());
        assertFalse(registry.isRegistered("broken"));
        assertTrue(registry.commandSpecs().isEmpty(), "半截注册必须回滚干净");
    }

    @Test
    void runawayScriptIsAbortedAndReported() throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            ExtensionLoadException error = assertThrows(ExtensionLoadException.class,
                    () -> install("hang", "while true do end"));
            assertTrue(error.getMessage().contains("中断"), error.getMessage());
        });

        assertFalse(registry.isRegistered("hang"), "跑飞的脚本不能留在表里，否则下次调用还卡主线程");
    }

    @Test
    void apiVersionMismatchIsRefusedWithAReadableReason() throws Exception {
        ExtensionLoadException error = assertThrows(ExtensionLoadException.class,
                () -> install("fancy", """
                        leng.api("9.9")
                        leng.command{ name = "x", run = function(ctx) return true end }
                        """));

        assertTrue(error.getMessage().contains("API"), error.getMessage());
        assertFalse(registry.isRegistered("fancy"));
    }

    @Test
    void unknownEventsAreRefusedAndListedBack() throws Exception {
        ExtensionLoadException error = assertThrows(ExtensionLoadException.class,
                () -> install("weird", "leng.on('player_sneeze', function(ctx) end)"));

        assertTrue(error.getMessage().contains("不支持的事件"), error.getMessage());
    }

    /** 异步事件是明确不支持的：把理由写进报错，作者才不会以为是拼错了名字。 */
    @Test
    void asyncEventsAreRefusedWithTheReason() throws Exception {
        ExtensionLoadException error = assertThrows(ExtensionLoadException.class,
                () -> install("chatty", "leng.on('player_chat', function(ctx) end)"));

        assertTrue(error.getMessage().contains("异步"), error.getMessage());
    }

    // ------------------------------------------------------------------ 开关

    @Test
    void switchLivesInTheExtensionsOwnConfigAndDisabledIsNotUnloaded() throws Exception {
        File directory = write("tp", """
                leng.command{
                  name = "tp",
                  parent = "lban",
                  run = function(ctx) return true end,
                }
                """);
        Files.writeString(new File(directory, LuaExtension.CONFIG_FILE).toPath(), "enabled: false\n");
        LuaExtension extension = new LuaExtension(plugin, registry, directory);
        registry.register(extension);

        assertFalse(extension.enabled());
        assertFalse(registry.isFeatureActive("tp"), "开关关着，功能就不生效");
        assertEquals(1, registry.commandSpecs().size(), "禁用 ≠ 卸载：声明还在，只是门控关着");
    }

    @Test
    void missingConfigMeansEnabled() throws Exception {
        LuaExtension extension = install("tp", """
                leng.command{ name = "tp", parent = "lban", run = function(ctx) return true end }
                """);

        assertTrue(extension.enabled(), "没有 config.yml 就是默认开着");
        assertTrue(registry.isFeatureActive("tp"));
    }

    @Test
    void disabledSwitchIsRereadAfterEnable() throws Exception {
        File directory = write("tp", """
                leng.command{ name = "tp", parent = "lban", run = function(ctx) return true end }
                """);
        Files.writeString(new File(directory, LuaExtension.CONFIG_FILE).toPath(), "enabled: false\n");
        registry.register(new LuaExtension(plugin, registry, directory));
        assertFalse(registry.isFeatureActive("tp"));

        LuaConfigWriter.writeEnabled(new File(directory, LuaExtension.CONFIG_FILE), true);
        registry.unregister("tp");
        registry.register(new LuaExtension(plugin, registry, directory));

        assertTrue(registry.isFeatureActive("tp"), "重新加载后要读到新开关");
    }

    // ------------------------------------------------------------------ 装载器

    @Test
    void collidingFeatureKeyIsReportedInsteadOfSilentlyLosingTheSwitch() throws Exception {
        registry.register(new LengbanlistExtension() {
            @Override
            public String id() {
                return "builtin-tp";
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public Set<String> features() {
                return Set.of("tp");
            }

            @Override
            public void onEnable(ExtensionContext context) {
            }
        });
        write("tp", "leng.command{ name = 'tp', parent = 'lban', run = function(ctx) return true end }");

        LuaLoader loader = new LuaLoader(plugin, registry);
        loader.loadAll();

        assertFalse(registry.isRegistered("tp"), "功能键已被占，扩展不能装上去");
        assertTrue(loader.failures().containsKey("tp"), "但必须留下可读原因，不能悄悄跳过");
    }

    @Test
    void loaderSkipsDirectoriesWithoutEntryScript() throws Exception {
        Files.createDirectories(dataFolder.resolve("extensions").resolve("empty"));

        LuaLoader loader = new LuaLoader(plugin, registry);
        loader.loadAll();

        assertTrue(loader.extensions().isEmpty());
        assertTrue(loader.failures().isEmpty(), "连入口脚本都没有的目录不算扩展，也不必报错");
    }

    @Test
    void unloadAllClearsCommandsAndTasks() throws Exception {
        install("tp", """
                leng.command{ name = "tp", parent = "lban", run = function(ctx) return true end }
                """);
        assertFalse(registry.commandSpecs().isEmpty());

        registry.unregisterAll();

        assertTrue(registry.commandSpecs().isEmpty(), "卸载后命令声明要一并消失，命令名才能让给别人");
    }

    // ------------------------------------------------------------------ 官方脚本

    /**
     * 直接装载随 jar 发布的官方扩展脚本。
     *
     * <p>它们平时只在真实服务端跑，单元测试里没人碰——语法错、门面调用写错、
     * 事件名拼错都不会被发现。这里把"能不能装起来"钉住。
     */
    @Test
    void shippedExtensionsLoadCleanly() throws Exception {
        for (String id : new String[]{"tp", "staffchat"}) {
            Path directory = dataFolder.resolve("extensions").resolve(id);
            Files.createDirectories(directory);
            try (InputStream in = getClass().getResourceAsStream("/extensions/" + id + "/" + LuaExtension.ENTRY_FILE)) {
                assertTrue(in != null, "jar 里没有 /extensions/" + id + "/" + LuaExtension.ENTRY_FILE);
                Files.copy(in, directory.resolve(LuaExtension.ENTRY_FILE));
            }
        }

        LuaLoader loader = new LuaLoader(plugin, registry);
        loader.loadAll();

        assertTrue(loader.failures().isEmpty(), () -> "官方扩展加载失败: " + loader.failures());
        assertTrue(registry.isRegistered("tp"));
        assertTrue(registry.isRegistered("staffchat"));
        assertEquals("lban", spec("tp").parent(), "/lban tp 必须还是 /lban 的子命令");
        assertEquals("", spec("sc").parent(), "/sc 依旧是顶层命令");
        assertEquals("lengbanlist.staffchat", spec("sc").permission(), "权限节点不能变，老用户的 LuckPerms 配置要照旧");
    }
}

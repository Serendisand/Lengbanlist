package org.leng.extension.lua;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
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
import org.mockito.ArgumentCaptor;
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
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
        Path root = dataFolder.resolve("extensions");
        Files.createDirectories(root);
        Path file = root.resolve(id + LuaExtension.ENTRY_SUFFIX);
        Files.writeString(file, script, StandardCharsets.UTF_8);
        return file.toFile();
    }

    /** 扩展自己的配置：{@code extensions/<id>/config.yml}，与脚本不在同一层。 */
    private File configFile(String id) {
        return dataFolder.resolve("extensions").resolve(id)
                .resolve(LuaExtension.CONFIG_FILE).toFile();
    }

    private void writeConfig(String id, String content) throws Exception {
        File config = configFile(id);
        Files.createDirectories(config.getParentFile().toPath());
        Files.writeString(config.toPath(), content, StandardCharsets.UTF_8);
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

    /**
     * 抓一段插件日志。
     *
     * <p>给管理员的提示（旧布局该往哪挪）本身就是行为的一部分，不验证等于没写。
     * 用完就摘掉 handler——{@code Logger.getGlobal()} 是全局的，留着会串到别的测试里。
     */
    private List<String> captureLog(Runnable action) {
        Logger logger = Logger.getGlobal();
        List<String> lines = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(String.valueOf(record.getMessage()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return lines;
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

    // ------------------------------------------------------------------ 事件订阅

    /**
     * 文档里那段"从零做一个扩展"的示例，连同事件桥本身。
     *
     * <p>事件桥此前没有任何覆盖：注册要经过 Bukkit 的 {@code PluginManager}，触发要走事件分发，
     * 这两样在单元测试里都没有。这里把 {@code PluginManager} 换成 mock、抓住注册进去的监听器，
     * 直接调它的 {@code @EventHandler} 方法——分发是 Bukkit 的，回调之后的逻辑才是我们的。
     */
    @Test
    void documentedWelcomeExampleGreetsOnJoin() throws Exception {
        PluginManager pluginManager = pluginManagerMock();

        File directory = write("welcome", """
                leng.name("欢迎语")
                leng.version("1.0.0")
                leng.api("1.0")
                local TEMPLATE = leng.config("message", "默认欢迎语")
                local function greet(player) player:send(string.format(TEMPLATE, player.name)) end
                leng.on("player_join", function(ctx) greet(ctx.player) end)
                """);
        writeConfig("welcome", "enabled: true\nmessage: \"欢迎回来，%s\"\n");
        registry.register(new LuaExtension(plugin, registry, directory));

        List<String> received = new ArrayList<>();
        ((LuaEventBridge) registeredListener(pluginManager))
                .onPlayerJoin(new PlayerJoinEvent(playerWithSink(received), ""));

        assertEquals(List.of("欢迎回来，Uki"), received, "欢迎语要来自扩展自己的 config.yml");
    }

    /** 一个订阅者抛异常，不能影响同一个事件上的其它订阅者，也不能漏到核心。 */
    @Test
    void oneFailingEventHandlerDoesNotStopTheOthers() throws Exception {
        PluginManager pluginManager = pluginManagerMock();

        File directory = write("noisy", """
                leng.on("player_join", function(ctx) error("第一个订阅者炸了") end)
                leng.on("player_join", function(ctx) ctx.player:send("第二个照常跑") end)
                """);
        registry.register(new LuaExtension(plugin, registry, directory));

        List<String> received = new ArrayList<>();
        ((LuaEventBridge) registeredListener(pluginManager))
                .onPlayerJoin(new PlayerJoinEvent(playerWithSink(received), ""));

        assertEquals(List.of("第二个照常跑"), received);
    }

    /** 玩家事件回调里改动的值要能落回事件本身（唯一能干预流程的事件）。 */
    @Test
    void playerCommandEventCanBeCancelledFromTheScript() throws Exception {
        PluginManager pluginManager = pluginManagerMock();

        File directory = write("guard", """
                leng.on("player_command", function(ctx)
                  if string.find(ctx.command, "boom") then
                    ctx:set_cancelled(true)
                  end
                end)
                """);
        registry.register(new LuaExtension(plugin, registry, directory));

        PlayerCommandPreprocessEvent event =
                new PlayerCommandPreprocessEvent(playerWithSink(new ArrayList<>()), "/lban boom");
        ((LuaEventBridge) registeredListener(pluginManager)).onPlayerCommand(event);

        assertTrue(event.isCancelled(), "脚本调用 set_cancelled 要真的作用在事件上");
    }

    /** 把 PluginManager 换成 mock；注册后由 {@link #registeredListener} 把它抓出来。 */
    private PluginManager pluginManagerMock() {
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        lenient().when(plugin.getServer()).thenReturn(server);
        lenient().when(server.getPluginManager()).thenReturn(pluginManager);
        return pluginManager;
    }

    private static Listener registeredListener(PluginManager pluginManager) {
        ArgumentCaptor<Listener> captor = ArgumentCaptor.forClass(Listener.class);
        verify(pluginManager).registerEvents(captor.capture(), any());
        return captor.getValue();
    }

    /** 一个能把 send 出去的消息收进 {@code received} 的假玩家。 */
    private static Player playerWithSink(List<String> received) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        // 玩家事件（如 PlayerCommandPreprocessEvent）的构造函数会去取 getServer().getOnlinePlayers()，
        // 假玩家也得有个假服务端，否则事件还没造出来就 NPE。
        Server server = mock(Server.class);
        lenient().doReturn(List.of()).when(server).getOnlinePlayers();
        lenient().when(player.getServer()).thenReturn(server);
        lenient().when(world.getName()).thenReturn("world");
        lenient().when(player.getName()).thenReturn("Uki");
        lenient().when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        lenient().when(player.getWorld()).thenReturn(world);
        lenient().when(player.getLocation()).thenReturn(new Location(world, 1, 2, 3));
        lenient().when(player.isOnline()).thenReturn(true);
        lenient().doAnswer(call -> {
            received.add(call.getArgument(0));
            return null;
        }).when(player).sendMessage(anyString());
        return player;
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
        writeConfig("tp", "enabled: false\n");
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
        writeConfig("tp", "enabled: false\n");
        registry.register(new LuaExtension(plugin, registry, directory));
        assertFalse(registry.isFeatureActive("tp"));

        LuaConfigWriter.writeEnabled(configFile("tp"), true);
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

    /** 配置目录不是扩展：只有子目录、没有同名 .lua 的，一个都不该被加载。 */
    @Test
    void configDirectoriesAloneAreNotExtensions() throws Exception {
        writeConfig("orphan", "enabled: true\n");

        LuaLoader loader = new LuaLoader(plugin, registry);
        loader.loadAll();

        assertTrue(loader.extensions().isEmpty(), "没有 <扩展名>.lua 就是什么都没装");
        assertTrue(loader.failures().isEmpty(), "这不是错误，不必报");
    }

    /** 老布局（{@code <扩展名>/extension.lua}）本版不认，但得在日志里指路。 */
    @Test
    void legacyLayoutIsIgnoredWithAPointerInTheLog() throws Exception {
        Path legacy = dataFolder.resolve("extensions").resolve("tp");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("extension.lua"), "leng.name('旧布局')");

        List<String> logs = captureLog(() -> new LuaLoader(plugin, registry).loadAll());

        assertTrue(registry.ids().isEmpty(), "旧布局不再被加载");
        assertTrue(logs.stream().anyMatch(line -> line.contains("tp.lua")),
                "但必须说清脚本该挪去哪，否则管理员只看到功能没了：" + logs);
    }

    /** 缺配置目录的扩展，加载时补一份 config.yml——配置该长在哪，不该让人猜。 */
    @Test
    void missingConfigIsGeneratedNextToTheScript() throws Exception {
        write("welcome", "leng.command{ name = 'hi', run = function(ctx) return true end }");

        new LuaLoader(plugin, registry).loadAll();

        File config = configFile("welcome");
        assertTrue(config.isFile(), "应该在 extensions/welcome/config.yml 生成一份");
        assertTrue(Files.readString(config.toPath()).contains("enabled: true"), "生成的开关默认是开的");
        assertTrue(registry.isRegistered("welcome"));
    }

    /** 已经写过的配置不许被生成逻辑覆盖：那是管理员改过的东西。 */
    @Test
    void generatedConfigNeverOverwritesAnExistingOne() throws Exception {
        write("tp", "leng.command{ name = 'tp', parent = 'lban', run = function(ctx) return true end }");
        writeConfig("tp", "enabled: false\nmessage: 我自己写的\n");

        new LuaLoader(plugin, registry).loadAll();

        assertEquals("enabled: false\nmessage: 我自己写的\n",
                Files.readString(configFile("tp").toPath()), "一个字都不能动");
        assertFalse(registry.isFeatureActive("tp"), "开关还是关着的");
    }

    /** 配置目录被管理员删了，开关还得有地方落——不能因为目录没了就静默失败。 */
    @Test
    void disablingRebuildsADeletedConfigDirectory() throws Exception {
        write("tp", "leng.command{ name = 'tp', parent = 'lban', run = function(ctx) return true end }");
        LuaLoader loader = new LuaLoader(plugin, registry);
        loader.loadAll();
        File config = configFile("tp");
        Files.delete(config.toPath());
        Files.delete(config.getParentFile().toPath());

        assertTrue(loader.setEnabled("tp", false));

        assertTrue(config.isFile(), "目录被删也要能重新写出来，不然开关没地方落");
        assertTrue(Files.readString(config.toPath()).contains("enabled: false"));
    }

    /** 没有脚本就谈不上开关：{@code /lban ext enable 鬼} 必须被挡下来。 */
    @Test
    void enablingAnExtensionWithoutAScriptIsRefused() throws Exception {
        LuaLoader loader = new LuaLoader(plugin, registry);

        assertFalse(loader.setEnabled("ghost", true), "没有 <扩展名>.lua 就是没这个扩展");
    }

    /** id 是命令输入，不能让它顺着 {@code ../} 把 config.yml 写到数据目录外面去。 */
    @Test
    void idsThatEscapeTheExtensionsDirectoryAreRefused() throws Exception {
        LuaLoader loader = new LuaLoader(plugin, registry);

        assertFalse(loader.setEnabled("../outside", true));
        assertFalse(loader.setEnabled("nested/path", true));
        assertFalse(loader.setEnabled("", true));
        assertFalse(Files.exists(dataFolder.resolve("outside")), "一个文件都不该被写到数据目录里");
        assertFalse(Files.exists(dataFolder.resolve("nested")), "更不该顺着子路径写出去");
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
        Path root = dataFolder.resolve("extensions");
        Files.createDirectories(root);
        for (String id : new String[]{"tp", "staffchat"}) {
            String resource = "/extensions/" + id + LuaExtension.ENTRY_SUFFIX;
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                assertTrue(in != null, "jar 里没有 " + resource);
                Files.copy(in, root.resolve(id + LuaExtension.ENTRY_SUFFIX));
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

package org.leng.integration;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 云端扩展市场：Lua 落位、jar 落位、sha256 校验与"不覆盖已有配置"。
 */
@ExtendWith(MockitoExtension.class)
class ExtensionCloudManagerTest {

    private static HttpServer server;
    private static int port;
    private static final Map<String, byte[]> FILES = new ConcurrentHashMap<>();

    @Mock Lengbanlist plugin;
    @Mock FileConfiguration config;

    private ExtensionCloudManager manager;
    private Path pluginsDir;
    private Path extensionsDir;

    @TempDir Path tmp;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = FILES.get(exchange.getRequestURI().getPath());
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void setUp() throws IOException {
        pluginsDir = tmp.resolve("plugins");
        Path dataFolder = pluginsDir.resolve("Lengbanlist");
        extensionsDir = dataFolder.resolve("extensions");
        Files.createDirectories(extensionsDir);
        lenient().when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        lenient().when(plugin.getPluginVersion()).thenReturn("2.1.6");
        lenient().when(plugin.getLogger()).thenReturn(Logger.getGlobal());
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(config.getBoolean("extensions-cloud.enabled", true)).thenReturn(true);
        lenient().doReturn(List.of("http://127.0.0.1:" + port + "/index.json"))
                .when(config).getList("download.mirrors.extensions");
        FILES.clear();
        manager = new ExtensionCloudManager(plugin);
    }

    @Test
    void luaExtensionLandsInExtensionsAndLeavesUserConfigAlone() throws IOException {
        byte[] script = "leng.name(\"T\")\n".getBytes(StandardCharsets.UTF_8);
        FILES.put("/tp.lua", script);
        FILES.put("/index.json", index(
                luaEntry("tp", "1.0.0", "tp.lua", sha256(script), "tp/config.yml")).getBytes(StandardCharsets.UTF_8));
        // 用户自己的配置已经存在：安装时不能覆盖
        Path configFile = extensionsDir.resolve("tp/config.yml");
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, "enabled: false\n", StandardCharsets.UTF_8);

        ExtensionCloudManager.InstallOutcome outcome = manager.install("tp");

        assertEquals(ExtensionCloudManager.InstallResult.INSTALLED, outcome.result());
        assertTrue(Files.exists(extensionsDir.resolve("tp.lua")), "Lua 脚本要落到 extensions/ 下");
        assertEquals("enabled: false\n", Files.readString(configFile), "已有配置不得被市场覆盖");
        assertTrue(Files.exists(extensionsDir.resolve(".cache/installed.json")), "安装记录要写下来");

        assertEquals(ExtensionCloudManager.InstallResult.ALREADY_INSTALLED, manager.install("tp").result());
    }

    @Test
    void jarExtensionLandsInPlugins() throws IOException {
        byte[] jar = minimalExtensionJar();
        FILES.put("/testjar.jar", jar);
        String url = "http://127.0.0.1:" + port + "/testjar.jar";
        FILES.put("/index.json", index(
                jarEntry("testjar", "1.0.0", url, sha256(jar))).getBytes(StandardCharsets.UTF_8));

        ExtensionCloudManager.InstallOutcome outcome = manager.install("testjar");

        assertEquals(ExtensionCloudManager.InstallResult.INSTALLED, outcome.result());
        assertTrue(Files.exists(pluginsDir.resolve("testjar.jar")), "jar 要落到 plugins/ 顶层");
    }

    @Test
    void checksumMismatchIsRejected() throws IOException {
        byte[] script = "leng.name(\"T\")\n".getBytes(StandardCharsets.UTF_8);
        FILES.put("/tp.lua", script);
        FILES.put("/index.json", index(
                luaEntry("tp", "1.0.0", "tp.lua", "deadbeef", "tp/config.yml")).getBytes(StandardCharsets.UTF_8));

        assertEquals(ExtensionCloudManager.InstallResult.FAILED, manager.install("tp").result());
        assertFalse(Files.exists(extensionsDir.resolve("tp.lua")), "校验失败不得落盘");
    }

    @Test
    void placeholderVersionIsNotInstallable() throws IOException {
        FILES.put("/index.json", index(
                jarEntry("soon", "0.0.0", "", "")).getBytes(StandardCharsets.UTF_8));

        assertEquals(ExtensionCloudManager.InstallResult.NOT_READY, manager.install("soon").result());
    }

    /** minCore 是"最低版本"，不是精确版本：核心 2.1.9 必须能满足 2.1.6。 */
    @Test
    void newerCoreSatisfiesMinCore() throws IOException {
        byte[] script = "leng.name(\"T\")\n".getBytes(StandardCharsets.UTF_8);
        FILES.put("/tp.lua", script);
        FILES.put("/index.json", index(
                luaEntry("tp", "1.0.0", "tp.lua", sha256(script), "tp/config.yml", "2.1.6"))
                .getBytes(StandardCharsets.UTF_8));
        when(plugin.getPluginVersion()).thenReturn("2.1.9");

        assertEquals(ExtensionCloudManager.InstallResult.INSTALLED, manager.install("tp").result());
    }

    @Test
    void olderCoreFailsMinCore() throws IOException {
        byte[] script = "leng.name(\"T\")\n".getBytes(StandardCharsets.UTF_8);
        FILES.put("/tp.lua", script);
        FILES.put("/index.json", index(
                luaEntry("tp", "1.0.0", "tp.lua", sha256(script), "tp/config.yml", "2.2.0"))
                .getBytes(StandardCharsets.UTF_8));
        when(plugin.getPluginVersion()).thenReturn("2.1.9");

        ExtensionCloudManager.InstallOutcome outcome = manager.install("tp");

        assertEquals(ExtensionCloudManager.InstallResult.INCOMPATIBLE, outcome.result());
        assertFalse(Files.exists(extensionsDir.resolve("tp.lua")), "版本不满足不得落盘");
    }

    /** requiredApi 比的是契约版本，不是插件版本：插件 3.0 不该挡住按 2.x 契约写的扩展。 */
    @Test
    void requiredApiUsesContractVersionNotPluginVersion() throws IOException {
        byte[] script = "leng.name(\"T\")\n".getBytes(StandardCharsets.UTF_8);
        FILES.put("/tp.lua", script);
        FILES.put("/index.json", index(
                luaEntry("tp", "1.0.0", "tp.lua", sha256(script), "tp/config.yml", "2.1.6", "[2.0,3.0)"))
                .getBytes(StandardCharsets.UTF_8));
        when(plugin.getPluginVersion()).thenReturn("3.0.0");

        assertEquals(ExtensionCloudManager.InstallResult.INSTALLED, manager.install("tp").result());
    }

    @Test
    void incompatibleRequiredApiIsRejected() throws IOException {
        byte[] script = "leng.name(\"T\")\n".getBytes(StandardCharsets.UTF_8);
        FILES.put("/tp.lua", script);
        FILES.put("/index.json", index(
                luaEntry("tp", "1.0.0", "tp.lua", sha256(script), "tp/config.yml", "2.1.6", "[3.0,4.0)"))
                .getBytes(StandardCharsets.UTF_8));

        assertEquals(ExtensionCloudManager.InstallResult.INCOMPATIBLE, manager.install("tp").result());
    }

    // ------------------------------------------------------------ helpers

    private static String index(String entry) {
        return "{\"schemaVersion\":1,\"updated\":\"2026-10-17\",\"extensions\":[" + entry + "]}";
    }

    private static String luaEntry(String id, String version, String file, String sha, String config) {
        return luaEntry(id, version, file, sha, config, "2.1.6");
    }

    private static String luaEntry(String id, String version, String file, String sha, String config,
                                   String minCore) {
        return luaEntry(id, version, file, sha, config, minCore, null);
    }

    private static String luaEntry(String id, String version, String file, String sha, String config,
                                   String minCore, String requiredApi) {
        String api = requiredApi == null ? "" : ",\"requiredApi\":\"" + requiredApi + "\"";
        return "{\"id\":\"" + id + "\",\"type\":\"lua\",\"name\":\"" + id + "\",\"author\":\"t\","
                + "\"version\":\"" + version + "\",\"minCore\":\"" + minCore + "\"" + api
                + ",\"file\":\"" + file + "\","
                + "\"config\":\"" + config + "\",\"sha256\":\"" + sha + "\"}";
    }

    private static String jarEntry(String id, String version, String url, String sha) {
        return "{\"id\":\"" + id + "\",\"type\":\"jar\",\"name\":\"" + id + "\",\"author\":\"t\","
                + "\"version\":\"" + version + "\",\"minCore\":\"2.1.6\",\"file\":\"" + id + ".jar\","
                + "\"url\":\"" + url + "\",\"sha256\":\"" + sha + "\"}";
    }

    private static byte[] minimalExtensionJar() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("plugin.yml"));
            jar.write(("name: Lengbanlist-Ext-Test\n"
                    + "main: com.example.Test\n"
                    + "depend: [Lengbanlist]\n").getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

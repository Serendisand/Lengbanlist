package org.leng.manager;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ModelCloudSourceFallbackTest {

    private static final String MODEL_BODY = "name: \"hutao\"\nhelp:\n  - \"§c test help\"\nmessages: {}\n";

    private static HttpServer server;
    private static int port;
    private static final AtomicInteger primaryHits = new AtomicInteger(0);

    private static String primaryBase;
    private static String mirrorBase;

    @Mock Lengbanlist plugin;
    @Mock FileConfiguration config;
    ModelCloudManager manager;

    @TempDir Path tmp;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        primaryBase = "http://127.0.0.1:" + port;
        mirrorBase = "http://127.0.0.2:" + port;

        String index = "{\n  \"version\": 1,\n  \"models\": [\n"
                + "    {\"id\": \"hutao\", \"name\": \"胡桃\", \"version\": \"1.2.0\", \"url\": \"" + mirrorBase + "/model\"},\n"
                + "    {\"id\": \"furina\", \"name\": \"芙宁娜\", \"version\": \"1.0.0\", \"url\": \"" + mirrorBase + "/model\"}\n"
                + "  ]\n}";

        register("/down", 500, "primary is down");
        register("/index.json", 200, index);
        register("/model", 200, MODEL_BODY);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    private static void register(String path, int code, String body) {
        server.createContext(path, new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                String host = ex.getRequestHeaders().getFirst("Host");
                if (host != null && host.startsWith("127.0.0.1:")) {
                    primaryHits.incrementAndGet();
                }
                byte[] resp = body.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(code, resp.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(resp);
                }
            }
        });
    }

    @BeforeEach
    void setUp() {
        primaryHits.set(0);
        lenient().when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getGlobal());
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getDataFolder()).thenReturn(tmp.toFile());
        lenient().when(config.getStringList("models-cloud.mirrors"))
                .thenReturn(List.of(primaryBase + "/down", mirrorBase + "/index.json"));
        lenient().when(config.getString(eq("models-cloud.repo"), any())).thenReturn("Serendisand/Lengbanlist-Models");
        lenient().when(config.getString(eq("models-cloud.branch"), any())).thenReturn("main");
        manager = new ModelCloudManager(plugin);
    }

    @Test
    void unreachablePrimaryIsSkippedForTheRestOfTheTask() {
        int installed = manager.syncAll();

        assertEquals(2, installed, "主源挂了也应该能通过镜像装完两个模型");
        assertEquals(1, primaryHits.get(), "主源只应在任务开头被试一次,后续下载不应再打它");
    }

    @Test
    void nextTaskProbesThePrimaryAgain() {
        manager.syncAll();
        assertEquals(1, primaryHits.get());

        manager.fetchIndex();

        assertEquals(2, primaryHits.get(), "新任务应重新尝试主源");
    }

    @Test
    void configuredMirrorOrderIsUntouched() {
        List<String> mirrors = manager.mirrors();

        assertEquals(primaryBase + "/down", mirrors.get(0), "配置里的主源顺序不应被改动");
        assertEquals(2, mirrors.size());
    }

    @Test
    void connectionFailuresAndServerErrorsCountAsUnreachable() {
        assertTrue(ModelCloudManager.isUnreachableFailure(new IOException("Connection refused")));
        assertTrue(ModelCloudManager.isUnreachableFailure(new IOException((String) null)));
        assertTrue(ModelCloudManager.isUnreachableFailure(new InterruptedException()));
        assertTrue(ModelCloudManager.isUnreachableFailure(new java.net.http.HttpConnectTimeoutException("HTTP connect timed out")));
        assertTrue(ModelCloudManager.isUnreachableFailure(new IOException("HTTP 503: /index.json")));
    }

    @Test
    void contentLevelErrorsDoNotBlacklistTheSource() {
        assertFalse(ModelCloudManager.isUnreachableFailure(new IOException("HTTP 404: /models/hutao/hutao.yml")));
        assertFalse(ModelCloudManager.isUnreachableFailure(new IllegalStateException("解析失败")));
    }
}

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelCloudManagerTest {

    private static final String MOCK_INDEX_JSON = "{\n" +
            "  \"version\": 1,\n" +
            "  \"updated\": \"2026-09-01\",\n" +
            "  \"models\": [\n" +
            "    {\"id\": \"hutao\", \"name\": \"胡桃\", \"version\": \"1.2.0\", \"author\": \"uki\",\n" +
            "     \"url\": \"%s\", \"sha256\": \"\"},\n" +
            "    {\"id\": \"furina\", \"name\": \"芙宁娜\", \"version\": \"1.0.0\", \"author\": \"uki\",\n" +
            "     \"url\": \"%s\", \"sha256\": \"\"}\n" +
            "  ],\n" +
            "  \"featured\": {\"month\": \"%s\", \"modelId\": \"hutao\", \"title\": \"本月精选\", \"description\": \"\"}\n" +
            "}";

    private static String mockIndexWithModelUrl(String month, String modelUrl) {
        return String.format(MOCK_INDEX_JSON, modelUrl, modelUrl, month);
    }

    private static HttpServer server;
    private static int port;
    private static final AtomicInteger intermittentHits = new AtomicInteger(0);

    @Mock Lengbanlist plugin;
    @Mock FileConfiguration config;
    ModelCloudManager manager;

    @TempDir Path tmp;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        register("/success", 200, mockIndexWithModelUrl(currentMonth, url("model")));
        register("/fail", 500, "server error");
        register("/invalid", 200, "not json at all");
        register("/intermittent", -1, "{\"version\":1,\"models\":[]}");
        register("/model", 200, "name: \"hutao\"\nhelp:\n  - \"§c test help\"\nmessages: {}\n");
        server.start();
    }

    static final String currentMonth = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    private static void register(String path, int codeOrNegative, String body) {
        String ctx = path.startsWith("/") ? path : "/" + path;
        server.createContext(ctx, new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                int code;
                if (codeOrNegative < 0) {

                    int hit = intermittentHits.incrementAndGet();
                    code = (hit % 2 == 1) ? 500 : 200;
                } else {
                    code = codeOrNegative;
                }
                byte[] resp = body.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(code, resp.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(resp);
                }
            }
        });
    }

    private static String url(String path) {
        return "http://127.0.0.1:" + port + "/" + path;
    }

    @BeforeEach
    void setUp() throws IOException {
        intermittentHits.set(0);
        lenient().when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getGlobal());
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getDataFolder()).thenReturn(tmp.toFile());
        lenient().when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of());
        lenient().when(config.getString(eq("models-cloud.repo"), any())).thenReturn("Serendisand/Lengbanlist-Models");
        lenient().when(config.getString(eq("models-cloud.branch"), any())).thenReturn("main");
        manager = new ModelCloudManager(plugin);
    }

    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
    private static String eq(String s) { return org.mockito.ArgumentMatchers.eq(s); }

    @Test
    void fetchIndex_success_parsesModelsAndFeatured() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("success")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndex();

        assertTrue(result.isPresent());
        ModelCloudManager.ModelIndex idx = result.get();
        assertEquals(1, idx.version());
        assertEquals(2, idx.models().size());
        assertEquals("hutao", idx.models().get(0).id());
        assertNotNull(idx.featured());
        assertEquals(currentMonth, idx.featured().month());
    }

    @Test
    void fetchIndex_allMirrorsFail_returnsEmpty() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("fail"), url("fail"), url("fail")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndex();

        assertFalse(result.isPresent());
    }

    @Test
    void fetchIndex_firstFailsSecondSucceeds_usesSecond() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("fail"), url("success")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndex();

        assertTrue(result.isPresent());
        assertEquals(2, result.get().models().size());
    }

    @Test
    void fetchIndex_invalidJson_returnsEmpty() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("invalid")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndex();

        assertFalse(result.isPresent());
    }

    @Test
    void getIndex_usesMemoryCache_whenFresh() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("success")));
        manager.fetchIndex(); 

        Optional<ModelCloudManager.ModelIndex> result = manager.getIndex();
        assertTrue(result.isPresent());
        assertEquals(2, result.get().models().size());
    }

    @Test
    void getIndex_usesDiskCache_whenMemoryExpired() throws Exception {

        Path cacheFile = tmp.resolve("models/.cache/index.json");
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, mockIndexWithModelUrl(currentMonth, url("model")));

        lenient().when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("fail")));

        Optional<ModelCloudManager.ModelIndex> result = manager.getIndex();
        assertTrue(result.isPresent());
        assertEquals(2, result.get().models().size());
    }

    @Test
    void getIndex_noCacheNoNetwork_returnsEmpty() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("fail")));
        Optional<ModelCloudManager.ModelIndex> result = manager.getIndex();
        assertFalse(result.isPresent());
    }

    @Test
    void writeIndexCache_writesFileAndReadBack() throws Exception {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("success")));
        manager.fetchIndex();

        Path cacheFile = tmp.resolve("models/.cache/index.json");
        assertTrue(Files.exists(cacheFile));
        String body = Files.readString(cacheFile, StandardCharsets.UTF_8);
        assertTrue(body.contains("hutao"));
    }

    @Test
    void fetchIndexAsync_returnsCompletableFutureWithResult() throws Exception {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("success")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndexAsync().get();

        assertTrue(result.isPresent());
    }

    @Test
    void fetchIndexAsync_allFailures_resolvesToEmpty() throws Exception {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("fail")));

        Optional<ModelCloudManager.ModelIndex> result = manager.fetchIndexAsync().get();

        assertFalse(result.isPresent());
    }

    @Test
    void isFeaturedForCurrentMonth_match_returnsTrue() {
        String currentMonth = ModelCloudManager.currentMonth();
        ModelCloudManager.FeaturedModel f = new ModelCloudManager.FeaturedModel(currentMonth, "hutao", "x", "y");
        assertTrue(manager.isFeaturedForCurrentMonth(f));
    }

    @Test
    void isFeaturedForCurrentMonth_noMatch_returnsFalse() {
        ModelCloudManager.FeaturedModel f = new ModelCloudManager.FeaturedModel("1999-01", "hutao", "x", "y");
        assertFalse(manager.isFeaturedForCurrentMonth(f));
    }

    @Test
    void isFeaturedForCurrentMonth_null_returnsFalse() {
        assertFalse(manager.isFeaturedForCurrentMonth(null));
    }

    @Test
    void isFeaturedForCurrentMonth_nullMonth_returnsFalse() {
        ModelCloudManager.FeaturedModel f = new ModelCloudManager.FeaturedModel(null, "hutao", "x", "y");
        assertFalse(manager.isFeaturedForCurrentMonth(f));
    }

    @Test
    void currentFeatured_returnsFeaturedWhenMonthMatches() {
        when(config.getStringList("models-cloud.mirrors")).thenReturn(List.of(url("success")));
        Optional<ModelCloudManager.FeaturedModel> result = manager.currentFeatured();
        assertTrue(result.isPresent());
    }

    @Test
    void mirrors_emptyConfig_buildsDefaultChain() {
        List<String> mirrors = manager.mirrors();
        assertEquals(3, mirrors.size());
        assertTrue(mirrors.get(0).contains("raw.githubusercontent.com"));
        assertTrue(mirrors.get(1).contains("gh-proxy.com"));
        assertTrue(mirrors.get(2).contains("mirror.ghproxy.com"));
    }

    @Test
    void mirrors_userConfig_overridesDefault() {
        List<String> custom = List.of(url("success"), url("fail"));
        when(config.getStringList("models-cloud.mirrors")).thenReturn(custom);
        List<String> mirrors = manager.mirrors();
        assertEquals(custom, mirrors);
    }

    private void ensureModelFile(String id) throws Exception {
        Files.createDirectories(tmp.resolve("models"));
        Files.writeString(tmp.resolve("models/" + id + ".yml"), "name: " + id + "\nmessages: {}\n");
    }

    @Test
    void pin_writesMetaInModelFile_andIsPinnedTrue() throws Exception {
        ensureModelFile("hutao");
        assertFalse(manager.isPinned("hutao"));
        assertTrue(manager.pin("hutao"));
        assertTrue(manager.isPinned("hutao"));

        String content = Files.readString(tmp.resolve("models/hutao.yml"));
        assertTrue(content.contains("pinned: true"));
    }

    @Test
    void unpin_flipsMetaInModelFile() throws Exception {
        ensureModelFile("hutao");
        manager.pin("hutao");
        assertTrue(manager.unpin("hutao"));
        assertFalse(manager.isPinned("hutao"));
        String content = Files.readString(tmp.resolve("models/hutao.yml"));
        assertTrue(content.contains("pinned: false"));
    }

    @Test
    void unpin_whenNotInstalled_returnsTrue() {

        assertTrue(manager.unpin("hutao"));
        assertFalse(manager.pin("hutao"));
    }

    @Test
    void pin_twiceIdempotent() throws Exception {
        ensureModelFile("hutao");
        assertTrue(manager.pin("hutao"));
        assertTrue(manager.pin("hutao"));
        assertTrue(manager.isPinned("hutao"));

        long cnt = Files.readAllLines(tmp.resolve("models/hutao.yml"))
                .stream().filter(l -> l.startsWith("pinned: ")).count();
        assertEquals(1, cnt);
    }

    @Test
    void installModel_pinned_skipsDownload() throws Exception {
        ensureModelFile("hutao");
        manager.pin("hutao");

        assertEquals(ModelCloudManager.InstallResult.PINNED_SKIPPED, manager.installModel("hutao"));
    }

    @Test
    void installModel_notInIndex_returnsNotFound() throws Exception {

        Path cacheFile = tmp.resolve("models/.cache/index.json");
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, "{\"version\":1,\"models\":[],\"featured\":null}");

        assertEquals(ModelCloudManager.InstallResult.NOT_FOUND, manager.installModel("nonexistent"));
    }

    @Test
    void installModel_success_writesFile() throws Exception {

        Path cacheFile = tmp.resolve("models/.cache/index.json");
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, mockIndexWithModelUrl(currentMonth, url("model")));

        when(config.getStringList("models-cloud.mirrors"))
                .thenReturn(List.of(url("b1"), url("b2")));

        assertEquals(ModelCloudManager.InstallResult.INSTALLED, manager.installModel("hutao"));
        assertTrue(manager.isInstalled("hutao"));
        Path installed = tmp.resolve("models/hutao.yml");
        assertTrue(Files.exists(installed));

        String content = Files.readString(installed);
        assertTrue(content.contains("version: 1.2.0"));
        assertFalse(content.contains("pinned: true"));
    }

    @Test
    void installModel_alreadyInstalledSameVersion_returnsAlreadyInstalled() throws Exception {
        Path cacheFile = tmp.resolve("models/.cache/index.json");
        Files.createDirectories(cacheFile.getParent());
        Files.writeString(cacheFile, mockIndexWithModelUrl(currentMonth, url("model")));

        Files.createDirectories(tmp.resolve("models"));
        Files.writeString(tmp.resolve("models/hutao.yml"),
                "name: hutao\nmessages: {}\nversion: 1.2.0\n");

        assertEquals(ModelCloudManager.InstallResult.ALREADY_INSTALLED, manager.installModel("hutao"));
    }
}

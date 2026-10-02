package org.leng.download;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一下载服务的完整性测试。
 *
 * <p>重点是三条不可让步的性质：
 * <ul>
 *   <li>sha256 不匹配时<b>绝不落盘</b>，且不破坏已有文件</li>
 *   <li>失败后不留下 {@code .part} 临时文件</li>
 *   <li>只允许 HTTPS（外加 localhost 的 http）</li>
 * </ul>
 */
class DownloadServiceTest {

    private static final String HELLO = "hello";
    /** 由 node crypto 独立算得，避免"拿实现验证实现"。 */
    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String MODEL_SHA256 =
            "6d3c24aa6f951bbd0159d7e46a3eadadc028a40ba7605929ba8f1919ff62cd0c";
    private static final String MODEL_BODY = "model: hutao\n";

    private static HttpServer server;
    private static String base;

    @TempDir Path tmp;
    private DownloadService service;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        base = "http://127.0.0.1:" + port;
        register("/hello", 200, HELLO);
        register("/model", 200, MODEL_BODY);
        register("/boom", 500, "server error");
        register("/missing", 404, "not found");
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void register(String path, int code, String body) {
        server.createContext(path, new HttpHandler() {
            @Override
            public void handle(HttpExchange ex) throws IOException {
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
        service = new DownloadService(DownloadSettings.defaults());
    }

    private Path target(String name) {
        return tmp.resolve(name);
    }

    private List<Path> partFiles() throws IOException {
        try (Stream<Path> files = Files.list(tmp)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".part")).toList();
        }
    }

    // ---------- 完整性 ----------

    @Test
    void downloadsAndVerifiesChecksum() throws Exception {
        Path out = target("hello.bin");

        DownloadService.DownloadResult result =
                service.downloadToFile(base + "/hello", out, HELLO_SHA256, "application/octet-stream");

        assertTrue(result.ok(), "校验通过时应成功: " + result.error());
        assertEquals(HELLO_SHA256, result.sha256());
        assertEquals(HELLO.length(), result.bytes());
        assertTrue(result.checksumVerified());
        assertEquals(HELLO, Files.readString(out, StandardCharsets.UTF_8));
        assertTrue(partFiles().isEmpty(), "成功后不应残留 .part 临时文件");
    }

    @Test
    void rejectsWhenChecksumMismatches() throws Exception {
        Path out = target("tampered.bin");

        DownloadService.DownloadResult result =
                service.downloadToFile(base + "/model", out, HELLO_SHA256, "application/octet-stream");

        assertFalse(result.ok(), "sha256 不匹配必须失败");
        assertEquals(MODEL_SHA256, result.sha256(), "应回传实际哈希以便排查");
        assertFalse(Files.exists(out), "校验失败时目标文件绝不能被创建");
        assertTrue(partFiles().isEmpty(), "校验失败后不应残留 .part 临时文件");
        assertTrue(result.error().contains("sha256"), "错误信息应说明是校验失败");
    }

    @Test
    void doesNotClobberExistingTargetOnChecksumFailure() throws Exception {
        Path out = target("existing.bin");
        Files.writeString(out, "previous good content", StandardCharsets.UTF_8);

        DownloadService.DownloadResult result =
                service.downloadToFile(base + "/model", out, HELLO_SHA256, "application/octet-stream");

        assertFalse(result.ok());
        assertEquals("previous good content", Files.readString(out, StandardCharsets.UTF_8),
                "校验失败不得破坏已经存在的文件");
    }

    @Test
    void overwritesExistingTargetOnSuccess() throws Exception {
        Path out = target("replace.bin");
        Files.writeString(out, "stale", StandardCharsets.UTF_8);

        DownloadService.DownloadResult result =
                service.downloadToFile(base + "/hello", out, HELLO_SHA256, "application/octet-stream");

        assertTrue(result.ok());
        assertEquals(HELLO, Files.readString(out, StandardCharsets.UTF_8));
    }

    @Test
    void skipsVerificationWhenNoChecksumGiven() throws Exception {
        Path out = target("unverified.bin");

        DownloadService.DownloadResult result =
                service.downloadToFile(base + "/hello", out, null, "application/octet-stream");

        assertTrue(result.ok(), "未提供校验值时应跳过校验");
        assertEquals(HELLO, Files.readString(out, StandardCharsets.UTF_8));
    }

    @Test
    void acceptsGitHubDigestPrefix() throws Exception {
        Path out = target("digest.bin");

        DownloadService.DownloadResult result = service.downloadToFile(
                base + "/hello", out, "sha256:" + HELLO_SHA256, "application/octet-stream");

        assertTrue(result.ok(), "GitHub release API 的 digest 字段带 sha256: 前缀，必须能识别");
    }

    @Test
    void checksumComparisonIsCaseInsensitiveAndTrimmed() throws Exception {
        Path out = target("case.bin");

        DownloadService.DownloadResult result = service.downloadToFile(
                base + "/hello", out, "  " + HELLO_SHA256.toUpperCase(java.util.Locale.ROOT) + "  ",
                "application/octet-stream");

        assertTrue(result.ok());
    }

    @Test
    void sha256HexMatchesIndependentReference() throws Exception {
        Path file = target("ref.txt");
        Files.writeString(file, HELLO, StandardCharsets.UTF_8);

        assertEquals(HELLO_SHA256, DownloadService.sha256Hex(file));
    }

    @Test
    void sha256HexOfEmptyFile() throws Exception {
        Path file = target("empty.txt");
        Files.write(file, new byte[0]);

        assertEquals(EMPTY_SHA256, DownloadService.sha256Hex(file));
    }

    // ---------- 传输层错误 ----------

    @Test
    void serverErrorPropagatesAndIsUnreachable() throws Exception {
        Path out = target("boom.bin");

        IOException error = assertThrows(IOException.class,
                () -> service.downloadToFile(base + "/boom", out, null, "application/octet-stream"));

        assertTrue(error.getMessage().contains("HTTP 500"), "实际: " + error.getMessage());
        assertTrue(MirrorChain.isUnreachableFailure(error), "5xx 应判定为源不可达");
        assertFalse(Files.exists(out));
        assertTrue(partFiles().isEmpty());
    }

    @Test
    void notFoundIsContentLevelNotSourceFailure() throws Exception {
        Path out = target("missing.bin");

        IOException error = assertThrows(IOException.class,
                () -> service.downloadToFile(base + "/missing", out, null, "application/octet-stream"));

        assertTrue(error.getMessage().contains("HTTP 404"), "实际: " + error.getMessage());
        assertFalse(MirrorChain.isUnreachableFailure(error),
                "404 是资源不存在，不能因此拉黑整个镜像");
        assertTrue(partFiles().isEmpty());
    }

    @Test
    void getReturnsBody() throws Exception {
        assertEquals(HELLO, service.get(base + "/hello", "text/plain"));
    }

    // ---------- 地址策略 ----------

    @Test
    void onlyHttpsAndLocalhostHttpAreAllowed() {
        assertTrue(DownloadService.isAllowedUrl("https://example.com/a.jar"));
        assertTrue(DownloadService.isAllowedUrl("http://127.0.0.1:8080/a.jar"));
        assertTrue(DownloadService.isAllowedUrl("http://localhost:8080/a.jar"));

        assertFalse(DownloadService.isAllowedUrl("http://example.com/a.jar"),
                "明文 HTTP 的公网地址必须拒绝，否则索引被投毒即可降级传输");
        assertFalse(DownloadService.isAllowedUrl("ftp://example.com/a.jar"));
        assertFalse(DownloadService.isAllowedUrl(""));
        assertFalse(DownloadService.isAllowedUrl(null));
    }
}

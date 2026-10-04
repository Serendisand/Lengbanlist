package org.leng.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * 统一下载服务：插件里所有对外的 HTTP 读取、POST 与文件下载都必须走这里。
 *
 * <p>它是 {@code org.leng.net} 的唯一出口——{@link HttpTransport} 是包内私有的传输层，
 * 外部拿不到，因此"绕过统一入口自己 new 一个客户端"在编译期就不可能发生。
 *
 * <p>它统一了四件此前散落各处的事：
 *
 * <ol>
 *   <li><b>连接参数</b>：超时、User-Agent、SSL 校验一律取自 {@link DownloadSettings}</li>
 *   <li><b>地址策略</b>：只允许 HTTPS（额外放行 localhost 的 http），见 {@link #isAllowedUrl}</li>
 *   <li><b>完整性</b>：{@link #downloadToFile} 在落盘前校验 sha256，不匹配一律拒绝，
 *       绝不让半截或被篡改的文件进入 plugins/</li>
 *   <li><b>原子性</b>：先写同目录下的 {@code .part} 临时文件，校验通过后再原子改名，
 *       避免服务器在写入过程中被杀而留下损坏的 jar</li>
 * </ol>
 *
 * <p>调用方各自的<b>策略</b>（下什么、校验到什么程度）仍由调用方决定，
 * 这里只统一<b>怎么发请求</b>。
 */
public final class DownloadService implements AutoCloseable {

    private static final String PART_SUFFIX = ".part";

    private final DownloadSettings settings;
    private volatile HttpTransport transport;

    public DownloadService(DownloadSettings settings) {
        this.settings = settings == null ? DownloadSettings.defaults() : settings;
    }

    public DownloadSettings settings() {
        return settings;
    }

    /** 传输层按需创建并复用；{@link #close()} 之后下一次调用会重新建一个。 */
    private HttpTransport transport() {
        HttpTransport local = transport;
        if (local == null) {
            synchronized (this) {
                if (transport == null) {
                    transport = new HttpTransport(
                            Duration.ofMillis(settings.connectTimeoutMs()),
                            Duration.ofMillis(settings.readTimeoutMs()),
                            !settings.sslVerify());
                }
                local = transport;
            }
        }
        return local;
    }

    @Override
    public void close() {
        HttpTransport local = transport;
        transport = null;
        if (local != null) {
            local.close();
        }
    }

    /** 拉取文本（JSON / YAML）。调用方自行解析。 */
    public String get(String url, String accept) throws IOException, InterruptedException {
        return transport().get(url, settings.userAgent(), accept);
    }

    /** 发一个 JSON POST，只要状态码。 */
    public int postJson(String url, String jsonBody) throws IOException, InterruptedException {
        return transport().postJson(url, jsonBody, settings.userAgent());
    }

    /** 发一个 JSON POST，并拿到状态码、响应头与响应体。 */
    public Response postJsonForResponse(String url, String jsonBody) throws IOException, InterruptedException {
        return new Response(transport().postJsonForResponse(url, jsonBody, settings.userAgent()));
    }

    /**
     * 流式下载，由调用方逐块消费。
     *
     * <p>给那些需要边下边做自己那套校验的调用方用（例如更新检查要在第一块上验 JAR 文件头）。
     * 只要"落到某个文件"就应当改用 {@link #downloadToFile}，那边自带 sha256 校验与原子改名。
     *
     * @param chunkConsumer 每收到一块就调用一次，抛出的异常会中断下载并原样传给调用方
     * @param byteCounter   收完后的总字节数回调，可为 {@code null}
     */
    public void download(String url, Consumer<byte[]> chunkConsumer, LongConsumer byteCounter)
            throws IOException, InterruptedException {
        transport().download(url, settings.userAgent(), chunkConsumer, byteCounter);
    }

    /**
     * 下载到文件，落盘前校验 sha256。
     *
     * @param expectedSha256 期望的 sha256；为空表示调用方没有校验值，此时跳过校验。
     *                       允许 {@code sha256:} 前缀（GitHub release API 的
     *                       {@code digest} 字段就是这个格式）。
     * @return 结果对象；校验失败时 {@code ok=false} 且目标文件不会被创建或覆盖
     */
    public DownloadResult downloadToFile(String url, Path target, String expectedSha256, String accept)
            throws IOException, InterruptedException {

        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null) {
            return new DownloadResult(false, target, null, 0L, "目标路径没有父目录: " + target);
        }
        Files.createDirectories(parent);
        Path tmp = parent.resolve(absolute.getFileName().toString() + PART_SUFFIX);

        try {
            Files.deleteIfExists(tmp);
            streamToFile(url, tmp, accept);

            long bytes = Files.size(tmp);
            String actual = sha256Hex(tmp);
            if (!matches(expectedSha256, actual)) {
                Files.deleteIfExists(tmp);
                return new DownloadResult(false, target, actual, bytes,
                        "sha256 校验失败：期望 " + expectedSha256 + "，实际 " + actual);
            }
            moveInto(tmp, target);
            return new DownloadResult(true, target, actual, bytes, "");
        } catch (IOException | InterruptedException | RuntimeException e) {
            safeDelete(tmp);
            throw e;
        }
    }

    private void streamToFile(String url, Path tmp, String accept) throws IOException, InterruptedException {
        try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            download(url, chunk -> {
                try {
                    out.write(chunk);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, null);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void moveInto(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 同一目录内不该发生；某些文件系统/网络盘不支持原子改名时退回普通移动
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不应覆盖真正的失败原因
        }
    }

    /** 校验 sha256；{@code expected} 为空视为"无校验值"，直接通过。 */
    static boolean matches(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        String normalized = expected.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("sha256:")) {
            normalized = normalized.substring("sha256:".length()).trim();
        }
        return normalized.equals(actual == null ? "" : actual.toLowerCase(Locale.ROOT));
    }

    /** 计算文件的 sha256 十六进制串。 */
    public static String sha256Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) > 0) {
                digest.update(buf, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * 下载地址白名单：只允许 HTTPS，额外放行 localhost 的 http（本地测试与内网自建）。
     * 这是防止"索引被投毒后把插件指向明文 HTTP 源"的第一道闸。
     */
    public static boolean isAllowedUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        return trimmed.startsWith("https://")
                || trimmed.startsWith("http://127.0.0.1")
                || trimmed.startsWith("http://localhost");
    }

    /** 一次 HTTP 响应。 */
    public static final class Response {

        private final HttpResponse<String> raw;

        Response(HttpResponse<String> raw) {
            this.raw = raw;
        }

        public int statusCode() {
            return raw.statusCode();
        }

        public String body() {
            return raw.body() == null ? "" : raw.body();
        }

        public String header(String name) {
            if (name == null) {
                return null;
            }
            return raw.headers().firstValue(name).orElse(null);
        }
    }

    /** 一次下载的结果。{@code ok=false} 时 {@code error} 说明原因，目标文件未被改动。 */
    public record DownloadResult(boolean ok, Path path, String sha256, long bytes, String error) {

        public boolean checksumVerified() {
            return ok && sha256 != null && !sha256.isEmpty();
        }
    }
}

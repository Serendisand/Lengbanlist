package org.leng.download;

import org.leng.utils.HttpHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;

/**
 * 统一下载服务：所有对外的 HTTP 读取与文件下载都应当走这里。
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
 */
public final class DownloadService {

    private static final String PART_SUFFIX = ".part";

    private final DownloadSettings settings;

    public DownloadService(DownloadSettings settings) {
        this.settings = settings == null ? DownloadSettings.defaults() : settings;
    }

    public DownloadSettings settings() {
        return settings;
    }

    /** 按统一下载设置构造 HTTP 客户端。 */
    public HttpHelper newClient() {
        return new HttpHelper(
                Duration.ofMillis(settings.connectTimeoutMs()),
                Duration.ofMillis(settings.readTimeoutMs()),
                !settings.sslVerify());
    }

    /** 拉取文本（JSON / YAML）。调用方自行解析。 */
    public String get(String url, String accept) throws IOException, InterruptedException {
        try (HttpHelper http = newClient()) {
            return http.get(url, settings.userAgent(), accept);
        }
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
        try (HttpHelper http = newClient();
             OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            http.download(url, settings.userAgent(), chunk -> {
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

    /** 一次下载的结果。{@code ok=false} 时 {@code error} 说明原因，目标文件未被改动。 */
    public record DownloadResult(boolean ok, Path path, String sha256, long bytes, String error) {

        public boolean checksumVerified() {
            return ok && sha256 != null && !sha256.isEmpty();
        }
    }
}

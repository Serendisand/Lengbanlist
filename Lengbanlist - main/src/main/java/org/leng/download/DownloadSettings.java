package org.leng.download;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 统一下载设置：超时、User-Agent、SSL 校验与镜像列表，全部从 {@code download:} 段读取。
 *
 * <p>读取顺序为「新键 → 旧键 → 内置默认」，因此既有的
 * {@code update-check.connect-timeout} / {@code read-timeout} / {@code ssl-verify}
 * 无需用户改动配置即可继续生效。
 *
 * <p>所有数值都会做下限兜底：配置缺失、为 0 或被写成负数时退回默认值。
 * 这不只是防手滑——单元测试里 mock 的 {@code FileConfiguration} 对未打桩的
 * {@code getInt} 会返回 0，若不兜底就会构造出 0 毫秒超时的客户端，让所有网络测试
 * 以"连接超时"的形式莫名其妙地失败。
 */
public record DownloadSettings(int connectTimeoutMs, int readTimeoutMs, String userAgent, boolean sslVerify) {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    public static final int MAX_TIMEOUT_MS = 120_000;
    public static final String DEFAULT_USER_AGENT = "Lengbanlist";

    public static DownloadSettings from(FileConfiguration config, String userAgentFallback) {
        int connect = clamp(
                intOr(config, "download.connect-timeout", "update-check.connect-timeout"),
                DEFAULT_CONNECT_TIMEOUT_MS);
        int read = clamp(
                intOr(config, "download.read-timeout", "update-check.read-timeout"),
                DEFAULT_READ_TIMEOUT_MS);
        String ua = stringOr(config, "download.user-agent", "update-check.user-agent");
        if (ua == null || ua.isBlank()) {
            ua = (userAgentFallback == null || userAgentFallback.isBlank())
                    ? DEFAULT_USER_AGENT : userAgentFallback;
        }
        boolean ssl = booleanOr(config, "download.ssl-verify", "update-check.ssl-verify", true);
        return new DownloadSettings(connect, read, ua, ssl);
    }

    public static DownloadSettings defaults() {
        return new DownloadSettings(DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS, DEFAULT_USER_AGENT, true);
    }

    /**
     * 读取统一镜像列表：先看 {@code download.overrides.<purpose>}，再看 {@code download.mirrors}。
     *
     * <p>两种写法都接受，便于把既有的两种配置形态迁移进来：
     * <ul>
     *   <li>字符串：{@code - "https://gh-proxy.com/https://raw.githubusercontent.com/..."}</li>
     *   <li>映射：{@code - {name: gh-proxy, type: github-proxy, url: "..."}}</li>
     * </ul>
     *
     * @param purpose 用途名（models / extensions / update），对应 overrides 下的键
     * @return 解析后的镜像列表；未配置时返回空列表，由调用方决定自己的默认链
     */
    public static List<MirrorSpec> mirrors(FileConfiguration config, String purpose) {
        if (config == null) {
            return List.of();
        }
        List<?> raw = null;
        if (purpose != null && !purpose.isBlank()) {
            raw = config.getList("download.overrides." + purpose);
        }
        if (raw == null || raw.isEmpty()) {
            raw = config.getList("download.mirrors");
        }
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<MirrorSpec> specs = new ArrayList<>(raw.size());
        for (Object entry : raw) {
            MirrorSpec spec = parseMirror(entry);
            if (spec != null && spec.usable()) {
                specs.add(spec);
            }
        }
        return specs;
    }

    private static MirrorSpec parseMirror(Object entry) {
        if (entry instanceof String text) {
            return MirrorSpec.custom(text);
        }
        if (entry instanceof Map<?, ?> map) {
            Object url = map.get("url");
            Object name = map.get("name");
            Object type = map.get("type");
            return new MirrorSpec(
                    name == null ? null : String.valueOf(name),
                    MirrorType.fromConfig(type == null ? null : String.valueOf(type)),
                    url == null ? "" : String.valueOf(url));
        }
        return null;
    }

    private static int intOr(FileConfiguration config, String primary, String legacy) {
        if (config == null) {
            return 0;
        }
        if (config.contains(primary)) {
            return config.getInt(primary, 0);
        }
        return config.getInt(legacy, 0);
    }

    private static String stringOr(FileConfiguration config, String primary, String legacy) {
        if (config == null) {
            return null;
        }
        String value = config.getString(primary, "");
        if (value != null && !value.isBlank()) {
            return value;
        }
        return config.getString(legacy, "");
    }

    private static boolean booleanOr(FileConfiguration config, String primary, String legacy, boolean fallback) {
        if (config == null) {
            return fallback;
        }
        if (config.contains(primary)) {
            return config.getBoolean(primary, fallback);
        }
        if (config.contains(legacy)) {
            return config.getBoolean(legacy, fallback);
        }
        return fallback;
    }

    private static int clamp(int value, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.min(value, MAX_TIMEOUT_MS);
    }

    /** 把"镜像配置不可用/已回退到内置链"这类信息统一打一次日志。 */
    public void logOnce(Logger logger, String message) {
        if (logger != null) {
            logger.info(message);
        }
    }
}

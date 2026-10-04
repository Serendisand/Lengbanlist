package org.leng.net;

import java.net.URI;

/**
 * 一个镜像源的配置。
 *
 * <p>{@code url} 是该镜像下的完整入口地址。不同用途的地址形态不同（模型索引是一个
 * index.json 地址，更新检查是 releases/latest 或 jsDelivr 包地址），所以下载层
 * 不解释它的语义，只把它当作"这个镜像要请求的地址"。
 *
 * @param name 展示名，日志用
 * @param type 镜像类型，日志与诊断用
 * @param url  完整入口地址；空表示该条无效，会被跳过
 */
public record MirrorSpec(String name, MirrorType type, String url) {

    public MirrorSpec {
        type = type == null ? MirrorType.CUSTOM : type;
        url = url == null ? "" : url.trim();
        name = (name == null || name.isBlank()) ? type.name().toLowerCase(java.util.Locale.ROOT) : name.trim();
    }

    public static MirrorSpec of(String name, String type, String url) {
        return new MirrorSpec(name, MirrorType.fromConfig(type), url);
    }

    public static MirrorSpec custom(String url) {
        return new MirrorSpec(url, MirrorType.CUSTOM, url);
    }

    /** 该条是否可用（地址非空）。 */
    public boolean usable() {
        return !url.isEmpty();
    }

    /** 取主机名，用于"同一主机的镜像在本任务内不再重试"。无法解析时返回 null。 */
    public String host() {
        return hostOf(url);
    }

    public static String hostOf(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return null;
        }
        try {
            String host = URI.create(rawUrl).getHost();
            return host == null ? null : host.toLowerCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }
}

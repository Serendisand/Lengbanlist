package org.leng.download;

import java.util.Locale;

/**
 * 镜像源类型。
 *
 * <p>不同类型对应不同的入口地址形态与不同的响应结构，因此"按类型改写 URL"这件事
 * 无法在下载层通用地完成：同一条逻辑资源在 github 上是 api.github.com，
 * 在 jsDelivr 上是 data.jsdelivr.com 的另一套 schema。下载层只负责
 * <b>顺序尝试、失败记忆、完整性校验与原子落盘</b>；每个用途各自解释自己镜像的
 * URL 与响应。
 */
public enum MirrorType {

    /** GitHub 直连。 */
    GITHUB,

    /** 经 gh-proxy 一类加速站中转。 */
    GITHUB_PROXY,

    /** jsDelivr CDN。 */
    JSDELIVR,

    /** Gitee 镜像。 */
    GITEE,

    /** 自定义（配置里给了完整地址，下载层不做任何改写）。 */
    CUSTOM;

    public static MirrorType fromConfig(String raw) {
        if (raw == null) {
            return CUSTOM;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "github" -> GITHUB;
            case "github-proxy", "github_proxy", "ghproxy", "gh-proxy" -> GITHUB_PROXY;
            case "jsdelivr" -> JSDELIVR;
            case "gitee" -> GITEE;
            default -> CUSTOM;
        };
    }
}

package org.leng.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.leng.Lengbanlist;
import org.leng.download.DownloadService;
import org.leng.download.DownloadSettings;
import org.leng.download.MirrorChain;
import org.leng.download.MirrorType;
import org.leng.download.MirrorSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GitHubUpdateChecker {
    public static final String RELEASES_URL = "https://github.com/Serendisand/Lengbanlist/releases";
    public static final String LATEST_RELEASE_URL = RELEASES_URL + "/latest";

    private static final String GITHUB_API_URL = "https://api.github.com/repos/Serendisand/Lengbanlist/releases/latest";
    private static final int MAX_RETRIES = 3;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    private static class Mirror {
        final String name;
        final String type;
        final String url;

        Mirror(String name, String type, String url) {
            this.name = name;
            this.type = type;
            this.url = url;
        }
    }

    private static class UpdateInfo {
        final String sourceName;
        final String version;
        final String downloadUrl;
        final String sha256;

        UpdateInfo(String sourceName, String version, String downloadUrl) {
            this(sourceName, version, downloadUrl, null);
        }

        UpdateInfo(String sourceName, String version, String downloadUrl, String sha256) {
            this.sourceName = sourceName;
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
        }
    }

    private static volatile UpdateInfo cachedInfo;
    private static volatile long cachedAt;

    public static String getLatestReleaseVersion() throws Exception {
        return fetchUpdateInfo().version;
    }

    public static String getLatestDownloadUrl() throws Exception {
        UpdateInfo info = fetchUpdateInfo();
        if (info.downloadUrl != null && !info.downloadUrl.isEmpty()) {
            return info.downloadUrl;
        }
        return getDownloadUrl(info.version);
    }

    public static String getLatestSha256() throws Exception {
        UpdateInfo info = fetchUpdateInfo();
        return info.sha256;
    }

    public static int compareVersions(String v1, String v2) {
        int[] a = parseVersion(v1);
        int[] b = parseVersion(v2);
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int[] parseVersion(String ver) {
        String[] s = ver.replaceAll("^v", "").split("\\.");
        List<Integer> parts = new ArrayList<>();
        for (String part : s) {
            String digits = part.replaceAll("\\D+", "");
            if (digits.isEmpty()) {
                continue;
            }
            parts.add(Integer.parseInt(digits));
        }
        if (parts.isEmpty()) {
            parts.add(0);
        }
        int[] arr = new int[parts.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = parts.get(i);
        }
        return arr;
    }

    public static boolean isUpdateAvailable(String localVersion) throws Exception {
        return compareVersions(localVersion, getLatestReleaseVersion()) < 0;
    }

    public static CompletableFuture<String> getLatestReleaseVersionAsync(Lengbanlist plugin) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getLatestReleaseVersion();
            } catch (Exception e) {
                plugin.getLogger().fine("异步获取最新版本失败: " + e.getMessage());
                return null;
            }
        });
    }

    public static void checkUpdate() {
        try {
            String localVersion = Lengbanlist.getInstance().getDescription().getVersion();
            String latestVersion = getLatestReleaseVersion();
            if (compareVersions(localVersion, latestVersion) < 0) {
                TextComponent mainMessage = new TextComponent("§a喵喵发现有新版本可用，当前版本：§e" + localVersion + "§a，最新版本：§e" + latestVersion + "§a 请前往: §b" + RELEASES_URL);
                TextComponent clickableComponent = new TextComponent("§f【§b点击前往喵~§f】");
                clickableComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RELEASES_URL));
                clickableComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击打开更新页面喵~").create()));
                Lengbanlist.getInstance().getLogger().info(mainMessage.toLegacyText() + " " + clickableComponent.toLegacyText());
            } else {
                Lengbanlist.getInstance().getLogger().fine("更新检查完成：已是最新版本");
            }
        } catch (Exception e) {
            if (isPluginRunning()) {
                ErrorLog.record(Lengbanlist.getInstance(), "检测更新时出错", e);
            }
        }
    }

    public static String getDownloadUrl(String version) {
        return RELEASES_URL + "/download/" + version + "/" + getGitHubFileName(version);
    }

    public static String getGitHubFileName(String version) {
        return "Lengbanlist-" + version + ".jar";
    }

    public static String getLocalFileName(String version) {
        return "Lengbanlist-" + version + ".jar";
    }

    public static String generateNewFileName(String currentFileName, String newVersion) {
        if (currentFileName.startsWith("Lengbanlist-") && currentFileName.endsWith(".jar")) {
            String baseName = currentFileName.substring(0, currentFileName.lastIndexOf("-"));
            return baseName + "-" + newVersion + ".jar";
        }
        return getLocalFileName(newVersion);
    }

    /** 统一从 download.* 读取，缺失时回退到既有的 update-check.* 键。 */
    private static DownloadSettings settings() {
        return DownloadSettings.from(Lengbanlist.getInstance().getConfig(), "Lengbanlist-UpdateChecker");
    }

    public static String getUserAgent() {
        return settings().userAgent();
    }

    public static boolean isSslVerifyEnabled() {
        return settings().sslVerify();
    }

    private static List<Mirror> loadMirrors() {
        List<Mirror> mirrors = new ArrayList<>();
        try {
            Object raw = Lengbanlist.getInstance().getConfig().get("update-check.mirrors");
            if (raw instanceof List) {
                for (Object item : (List<?>) raw) {
                    if (!(item instanceof Map)) {
                        continue;
                    }
                    Map<?, ?> map = (Map<?, ?>) item;
                    String name = str(map.get("name"), "");
                    String type = str(map.get("type"), "");
                    String url = str(map.get("url"), "");
                    if (type.isEmpty() || url.isEmpty()) {
                        continue;
                    }
                    if (name.isEmpty()) {
                        name = type;
                    }
                    mirrors.add(new Mirror(name, type, url));
                }
            }
        } catch (Exception ignored) {
        }
        // 统一配置（download.overrides.update / download.mirrors）优先于内置默认
        if (mirrors.isEmpty()) {
            for (MirrorSpec spec : DownloadSettings.mirrors(Lengbanlist.getInstance().getConfig(), "update")) {
                mirrors.add(new Mirror(spec.name(), spec.type().configName(), spec.url()));
            }
        }
        if (mirrors.isEmpty()) {
            mirrors.add(new Mirror("gh-proxy", "github-proxy", "https://gh-proxy.com/https://api.github.com/repos/Serendisand/Lengbanlist/releases/latest"));
            mirrors.add(new Mirror("jsDelivr", "jsdelivr", "https://data.jsdelivr.com/v1/packages/gh/Serendisand/Lengbanlist"));
            mirrors.add(new Mirror("GitHub直连", "github", GITHUB_API_URL));
        }
        return mirrors;
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static boolean isPluginRunning() {
        Lengbanlist plugin = Lengbanlist.getInstance();
        return plugin != null && plugin.isEnabled();
    }

    private static UpdateInfo fetchUpdateInfo() throws Exception {
        if (cachedInfo != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            return cachedInfo;
        }
        synchronized (GitHubUpdateChecker.class) {
            if (cachedInfo != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return cachedInfo;
            }
            List<Mirror> mirrors = loadMirrors();
            MirrorChain chain = new MirrorChain(toSpecs(mirrors));
            Exception lastException = null;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                for (Mirror mirror : mirrors) {
                    String host = MirrorSpec.hostOf(mirror.url);
                    if (host != null && chain.unreachableHosts().contains(host)) {
                        continue;
                    }
                    if (!isPluginRunning()) {
                        throw new Exception("插件已停用，取消更新检查");
                    }
                    try {
                        UpdateInfo info = fetchFromMirror(mirror);
                        cachedInfo = info;
                        cachedAt = System.currentTimeMillis();
                        Lengbanlist.getInstance().getLogger().info("更新检查成功：来源 " + info.sourceName + "，最新版本 " + info.version);
                        return info;
                    } catch (Exception e) {
                        lastException = e;
                        if (chain.noteFailure(mirror.url, e)) {
                            Lengbanlist.getInstance().getLogger().info("更新检查：镜像源 " + host
                                    + " 本次不可达，后续轮次将跳过它。");
                        }
                        Lengbanlist.getInstance().getLogger().fine("更新检查失败：" + mirror.name
                                + "（第" + attempt + "轮）— " + e.getMessage());
                    }
                }
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(1000);
                }
            }
            throw new Exception("所有更新源均不可用（共 " + mirrors.size() + " 个镜像 × " + MAX_RETRIES + " 轮）", lastException);
        }
    }

    private static List<MirrorSpec> toSpecs(List<Mirror> mirrors) {
        List<MirrorSpec> specs = new ArrayList<>(mirrors.size());
        for (Mirror mirror : mirrors) {
            specs.add(new MirrorSpec(mirror.name, MirrorType.fromConfig(mirror.type), mirror.url));
        }
        return specs;
    }

    private static UpdateInfo fetchFromMirror(Mirror mirror) throws Exception {
        String body = doFetch(mirror.url);
        JsonObject obj;
        try {
            obj = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            throw new Exception("响应解析失败: " + e.getMessage(), e);
        }
        if ("jsdelivr".equals(mirror.type)) {
            if (!obj.has("versions") || obj.get("versions").getAsJsonArray().size() == 0) {
                throw new Exception("jsDelivr 返回了空的版本列表");
            }
            String bestVersion = null;
            for (com.google.gson.JsonElement element : obj.get("versions").getAsJsonArray()) {
                String v = element.getAsJsonObject().get("version").getAsString();
                if (bestVersion == null || compareVersions(v, bestVersion) > 0) {
                    bestVersion = v;
                }
            }
            return new UpdateInfo(mirror.name, bestVersion, null);
        }
        if ("github".equals(mirror.type) || "github-proxy".equals(mirror.type) || "gitee".equals(mirror.type)) {
            if (!obj.has("tag_name")) {
                throw new Exception("响应中缺少 tag_name 字段");
            }
            String version = obj.get("tag_name").getAsString();
            String downloadUrl = null;
            String sha256 = null;
            if (obj.has("assets") && obj.get("assets").getAsJsonArray().size() > 0) {
                JsonObject asset = obj.get("assets").getAsJsonArray().get(0).getAsJsonObject();
                if (asset.has("browser_download_url")) {
                    downloadUrl = asset.get("browser_download_url").getAsString();
                }
                if (asset.has("digest") && !asset.get("digest").isJsonNull()) {
                    sha256 = asset.get("digest").getAsString();
                }
            }
            if ("github-proxy".equals(mirror.type)) {
                String proxyBase = getProxyBase(mirror.url);
                if (downloadUrl == null) {
                    downloadUrl = getDownloadUrl(version);
                }
                if (proxyBase != null && downloadUrl != null && downloadUrl.startsWith("https://github.com/")) {
                    downloadUrl = proxyBase + downloadUrl;
                }
            }
            return new UpdateInfo(mirror.name, version, downloadUrl, sha256);
        }
        throw new Exception("未知的镜像类型: " + mirror.type);
    }

    private static String getProxyBase(String mirrorUrl) {
        String lower = mirrorUrl.toLowerCase();
        int idx = lower.indexOf("https://api.github.com");
        if (idx <= 0) idx = lower.indexOf("http://api.github.com");
        if (idx <= 0) idx = lower.indexOf("https://github.com");
        if (idx <= 0) idx = lower.indexOf("http://github.com");
        return idx > 0 ? mirrorUrl.substring(0, idx) : null;
    }

    private static String doFetch(String url) throws Exception {
        DownloadSettings current = settings();
        if (!current.sslVerify()) {
            logSslWarningIfNeeded();
        }
        try {
            return new DownloadService(current).get(url, "application/json");
        } catch (java.io.IOException e) {
            throw new java.io.IOException("连接失败: " + url + "（" + e.getMessage() + "）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException("更新检查被中断");
        }
    }

    private static volatile boolean sslWarningLogged = false;

    private static void logSslWarningIfNeeded() {
        if (!sslWarningLogged) {
            sslWarningLogged = true;
            Lengbanlist.getInstance().getLogger().warning("!!! update-check.ssl-verify=false：更新检查将跳过 SSL 证书校验，存在中间人攻击风险，请仅在可信网络环境使用！");
        }
    }

}

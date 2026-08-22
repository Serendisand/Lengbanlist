package org.leng.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.leng.platform.LengbanlistPlatform;
import org.leng.platform.PlatformHolder;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GitHubUpdateChecker {
    public static final String RELEASES_URL = "https://github.com/Serendisand/Lengbanlist/releases";
    public static final String LATEST_RELEASE_URL = RELEASES_URL + "/latest";

    /** 当前运行的平台，用于在 release 资产列表里挑选对应的 jar。 */
    public enum Platform {
        BUKKIT("bukkit"),
        FABRIC("fabric");

        private final String assetPrefix;

        Platform(String assetPrefix) {
            this.assetPrefix = assetPrefix;
        }

        public String assetPrefix() {
            return assetPrefix;
        }
    }

    private static final String GITHUB_API_URL = "https://api.github.com/repos/Serendisand/Lengbanlist/releases/latest";
    private static final int MAX_RETRIES = 3;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final String CONFIG_PATH = "update-check.";

    private static volatile UpdateInfo cachedInfo;
    private static volatile long cachedAt;

    /** 镜像源：name 仅作日志标记；type 决定解析逻辑；url 是 API 端点或代理地址。 */
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
        final JsonObject releaseJson;

        UpdateInfo(String sourceName, String version, JsonObject releaseJson) {
            this.sourceName = sourceName;
            this.version = version;
            this.releaseJson = releaseJson;
        }
    }

    public static String getLatestReleaseVersion() throws Exception {
        return fetchUpdateInfo().version;
    }

    public static String getLatestDownloadUrl(Platform platform) throws Exception {
        UpdateInfo info = fetchUpdateInfo();
        String url = pickAssetDownloadUrl(info, platform);
        if (url != null) {
            return url;
        }
        return getDownloadUrl(info.version, platform);
    }

    public static String getLatestSha256(Platform platform) throws Exception {
        UpdateInfo info = fetchUpdateInfo();
        return pickAssetDigest(info, platform);
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

    public static CompletableFuture<String> getLatestReleaseVersionAsync(LengbanlistPlatform plugin) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getLatestReleaseVersion();
            } catch (Exception e) {
                plugin.getLogger().warning("异步获取最新版本失败: " + e.getMessage());
                return null;
            }
        });
    }

    public static void checkUpdate() {
        try {
            String localVersion = PlatformHolder.get().getPluginVersion();
            String latestVersion = getLatestReleaseVersion();
            if (compareVersions(localVersion, latestVersion) < 0) {
                PlatformHolder.get().getLogger().info("§a喵喵发现有新版本可用，当前版本：§e" + localVersion
                        + "§a，最新版本：§e" + latestVersion + "§a 请前往: §b" + RELEASES_URL
                        + " §f【§b点击前往喵~§f】");
            } else {
                PlatformHolder.get().getLogger().info("哇塞，喵呜现在是最新版本！QwQ");
            }
        } catch (Exception e) {
            PlatformHolder.get().getLogger().warning("检测更新时出错: " + e.getMessage());
        }
    }

    public static String getDownloadUrl(String version, Platform platform) {
        return RELEASES_URL + "/download/" + version + "/" + getGitHubFileName(version, platform);
    }

    /** Release 资产文件名：{@code Lengbanlist-{bukkit|fabric}-{version}.jar}。 */
    public static String getGitHubFileName(String version, Platform platform) {
        return "Lengbanlist-" + platform.assetPrefix() + "-" + version + ".jar";
    }

    /** 本地插件文件名（用于自动更新后写回 plugins/ 目录），与远端资产名保持一致便于升级。 */
    public static String getLocalFileName(String version, Platform platform) {
        return "Lengbanlist - " + platform.assetPrefix() + " - " + version + ".jar";
    }

    /**
     * 基于当前已安装文件名推断新版本应保存的本地文件名。
     * 当前文件名包含 {@code - <platform> - } 时直接替换版本号；否则按指定平台生成。
     */
    public static String generateNewFileName(String currentFileName, String newVersion, Platform platform) {
        if (currentFileName != null && currentFileName.endsWith(".jar")) {
            String prefix = "Lengbanlist - " + platform.assetPrefix() + " - ";
            if (currentFileName.startsWith(prefix)) {
                return prefix + newVersion + ".jar";
            }
            String legacyPrefix = "Lengbanlist - ";
            if (currentFileName.startsWith(legacyPrefix)) {
                return prefix + newVersion + ".jar";
            }
            int dash = currentFileName.lastIndexOf(" - ");
            if (dash > 0) {
                return currentFileName.substring(0, dash) + " - " + newVersion + ".jar";
            }
        }
        return getLocalFileName(newVersion, platform);
    }

    // -----------------------------------------------------------------------
    // 多镜像 + 配置项
    // -----------------------------------------------------------------------

    private static int getConnectTimeout() {
        return PlatformHolder.get().getConfigInt(CONFIG_PATH + "connect-timeout", 8000);
    }

    private static int getReadTimeout() {
        return PlatformHolder.get().getConfigInt(CONFIG_PATH + "read-timeout", 10000);
    }

    private static String getUserAgent() {
        String ua = PlatformHolder.get().getConfigString(CONFIG_PATH + "user-agent", "");
        if (ua == null || ua.trim().isEmpty()) {
            return "Lengbanlist-UpdateChecker";
        }
        return ua;
    }

    private static boolean isSslVerify() {
        return PlatformHolder.get().getConfigBoolean(CONFIG_PATH + "ssl-verify", true);
    }

    public static boolean isSslVerifyEnabled() {
        return isSslVerify();
    }

    public static SSLSocketFactory getInsecureSocketFactory() {
        return INSECURE_SOCKET_FACTORY;
    }

    /** 从 config.yml 读取 update-check.mirrors，缺省返回 3 个兜底源。 */
    private static List<Mirror> loadMirrors() {
        List<Mirror> mirrors = new ArrayList<>();
        try {
            Object raw = PlatformHolder.get().getConfigValue(CONFIG_PATH + "mirrors");
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
        if (mirrors.isEmpty()) {
            mirrors.add(new Mirror("gh-proxy", "github-proxy",
                    "https://gh-proxy.com/https://api.github.com/repos/Serendisand/Lengbanlist/releases/latest"));
            mirrors.add(new Mirror("jsDelivr", "jsdelivr",
                    "https://data.jsdelivr.com/v1/packages/gh/Serendisand/Lengbanlist"));
            mirrors.add(new Mirror("GitHub直连", "github", GITHUB_API_URL));
            mirrors.add(new Mirror("Gitee镜像", "gitee",
                    "https://gitee.com/api/v5/repos/Serendisand_mirror/Lengbanlist/releases/latest"));
        }
        return mirrors;
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static UpdateInfo fetchUpdateInfo() throws Exception {
        long now = System.currentTimeMillis();
        if (cachedInfo != null && now - cachedAt < CACHE_TTL_MS) {
            return cachedInfo;
        }
        synchronized (GitHubUpdateChecker.class) {
            now = System.currentTimeMillis();
            if (cachedInfo != null && now - cachedAt < CACHE_TTL_MS) {
                return cachedInfo;
            }
            List<Mirror> mirrors = loadMirrors();
            Exception lastException = null;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                for (Mirror mirror : mirrors) {
                    try {
                        UpdateInfo info = fetchFromMirror(mirror);
                        cachedInfo = info;
                        cachedAt = System.currentTimeMillis();
                        PlatformHolder.get().getLogger().info("更新检查成功：来源 " + info.sourceName
                                + "，最新版本 " + info.version);
                        return info;
                    } catch (Exception e) {
                        lastException = e;
                        PlatformHolder.get().getLogger().warning("更新检查失败：" + mirror.name
                                + " → " + mirror.url + "（第" + attempt + "轮），原因：" + e.getMessage());
                    }
                }
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(1000);
                }
            }
            throw new Exception("所有更新源均不可用（共 " + mirrors.size() + " 个镜像 × "
                    + MAX_RETRIES + " 轮）", lastException);
        }
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
            for (JsonElement element : obj.get("versions").getAsJsonArray()) {
                String v = element.getAsJsonObject().get("version").getAsString();
                if (bestVersion == null || compareVersions(v, bestVersion) > 0) {
                    bestVersion = v;
                }
            }
            // jsDelivr 无 tag_name / assets，构造一个最小可用 releaseJson
            JsonObject synthetic = new JsonObject();
            synthetic.addProperty("tag_name", bestVersion);
            return new UpdateInfo(mirror.name, bestVersion, synthetic);
        }
        if ("github".equals(mirror.type) || "github-proxy".equals(mirror.type)
                || "gitee".equals(mirror.type)) {
            if (!obj.has("tag_name")) {
                throw new Exception("响应中缺少 tag_name 字段");
            }
            return new UpdateInfo(mirror.name, obj.get("tag_name").getAsString(), obj);
        }
        throw new Exception("未知的镜像类型: " + mirror.type);
    }

    /**
     * 从已取得的 releaseJson 中挑选匹配当前平台的 asset。
     * github-proxy 类型若 asset URL 是原始 GitHub 地址，会按 mirror.url 的代理前缀改写。
     */
    private static String pickAssetDownloadUrl(UpdateInfo info, Platform platform) {
        JsonObject obj = info.releaseJson;
        if (obj == null || !obj.has("assets")) {
            return null;
        }
        String expectedName = getGitHubFileName(info.version, platform);
        String downloadUrl = null;
        for (int i = 0; i < obj.get("assets").getAsJsonArray().size(); i++) {
            JsonObject asset = obj.get("assets").getAsJsonArray().get(i).getAsJsonObject();
            if (expectedName.equals(safeGetString(asset, "name"))) {
                downloadUrl = safeGetString(asset, "browser_download_url");
                break;
            }
        }
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            return null;
        }
        // 不改写：调用方拿到原始 URL 即可，由 doFetch 阶段决定实际走哪个代理
        return downloadUrl;
    }

    private static String pickAssetDigest(UpdateInfo info, Platform platform) {
        JsonObject obj = info.releaseJson;
        if (obj == null || !obj.has("assets")) {
            return null;
        }
        String expectedName = getGitHubFileName(info.version, platform);
        for (int i = 0; i < obj.get("assets").getAsJsonArray().size(); i++) {
            JsonObject asset = obj.get("assets").getAsJsonArray().get(i).getAsJsonObject();
            if (expectedName.equals(safeGetString(asset, "name"))) {
                return safeGetString(asset, "digest");
            }
        }
        return null;
    }

    private static String safeGetString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsString();
    }

    private static String doFetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", getUserAgent());
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(getConnectTimeout());
        connection.setReadTimeout(getReadTimeout());
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection https = (HttpsURLConnection) connection;
            if (!isSslVerify()) {
                logSslWarningIfNeeded();
                if (INSECURE_SOCKET_FACTORY != null) {
                    https.setSSLSocketFactory(INSECURE_SOCKET_FACTORY);
                }
                https.setHostnameVerifier((hostname, session) -> true);
            }
        }
        int code;
        try {
            code = connection.getResponseCode();
        } catch (IOException e) {
            throw new IOException("连接失败: " + url + "（" + e.getMessage() + "）", e);
        }
        if (code >= 400) {
            throw new IOException("HTTP " + code + ": " + url);
        }
        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), "UTF-8")) {
            StringBuilder response = new StringBuilder();
            char[] buffer = new char[4096];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                response.append(buffer, 0, len);
            }
            return response.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static final SSLSocketFactory INSECURE_SOCKET_FACTORY = createInsecureSocketFactory();
    private static volatile boolean sslWarningLogged = false;

    private static void logSslWarningIfNeeded() {
        if (!sslWarningLogged) {
            sslWarningLogged = true;
            PlatformHolder.get().getLogger().warning(
                    "!!! update-check.ssl-verify=false：更新检查将跳过 SSL 证书校验，"
                            + "存在中间人攻击风险，请仅在可信网络环境使用！");
        }
    }

    private static SSLSocketFactory createInsecureSocketFactory() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);
            return context.getSocketFactory();
        } catch (Exception e) {
            return null;
        }
    }
}

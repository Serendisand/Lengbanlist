package org.leng.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.leng.platform.LengbanlistPlatform;
import org.leng.platform.PlatformHolder;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GitHubUpdateChecker {
    public static final String RELEASES_URL = "https://github.com/Ukiyograin/Lengbanlist/releases";
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

    private static final String GITHUB_API_URL = "https://api.github.com/repos/Ukiyograin/Lengbanlist/releases/latest";
    private static final List<String> STATIC_API_URLS = Arrays.asList(GITHUB_API_URL);
    private static final int TIMEOUT = 3000;
    private static final int MAX_RETRIES = 3;

    static {
    }

    public static String getLatestReleaseVersion() throws Exception {
        return fetchJsonFromApi().get("tag_name").getAsString();
    }

    public static String getLatestDownloadUrl(Platform platform) throws Exception {
        JsonObject json = fetchJsonFromApi();
        String assetUrl = findAssetUrl(json, platform);
        if (assetUrl != null) {
            return assetUrl;
        }
        return getDownloadUrl(json.get("tag_name").getAsString(), platform);
    }

    public static String getLatestSha256(Platform platform) throws Exception {
        JsonObject json = fetchJsonFromApi();
        String assetUrl = findAssetUrl(json, platform);
        if (assetUrl == null) {
            return null;
        }
        // digest 通常挂在 assets[].digest 上；这里通过遍历定位匹配平台的那个 asset。
        if (json.has("assets") && json.get("assets").getAsJsonArray().size() > 0) {
            String expectedName = getGitHubFileName(json.get("tag_name").getAsString(), platform);
            for (int i = 0; i < json.get("assets").getAsJsonArray().size(); i++) {
                JsonObject asset = json.get("assets").getAsJsonArray().get(i).getAsJsonObject();
                if (expectedName.equals(asset.get("name").getAsString()) && asset.has("digest")) {
                    return asset.get("digest").getAsString();
                }
            }
        }
        return null;
    }

    private static String findAssetUrl(JsonObject releaseJson, Platform platform) {
        if (!releaseJson.has("assets")) {
            return null;
        }
        String tag = releaseJson.get("tag_name").getAsString();
        String expectedName = getGitHubFileName(tag, platform);
        for (int i = 0; i < releaseJson.get("assets").getAsJsonArray().size(); i++) {
            JsonObject asset = releaseJson.get("assets").getAsJsonArray().get(i).getAsJsonObject();
            if (expectedName.equals(asset.get("name").getAsString())
                    && asset.has("browser_download_url")) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        return null;
    }

    private static JsonObject fetchJsonFromApi() throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            for (String apiUrl : STATIC_API_URLS) {
                try {
                    return doFetch(apiUrl);
                } catch (Exception e) {
                    lastException = e;
                    PlatformHolder.get().getLogger().warning("API 请求失败: " + apiUrl + "（第" + attempt + "次），正在重试...");
                }
            }
            if (attempt < MAX_RETRIES) {
                Thread.sleep(1000);
            }
        }
        throw new Exception("所有 API 请求均失败（已重试" + MAX_RETRIES + "次）", lastException);
    }

    private static JsonObject doFetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
            StringBuilder response = new StringBuilder();
            int data = reader.read();
            while (data != -1) {
                response.append((char) data);
                data = reader.read();
            }
            return JsonParser.parseString(response.toString()).getAsJsonObject();
        } finally {
            connection.disconnect();
        }
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
        int[] arr = new int[s.length];
        for (int i = 0; i < s.length; i++) {
            arr[i] = Integer.parseInt(s[i].replaceAll("\\D+", ""));
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
                PlatformHolder.get().getLogger().info("§a喵喵发现有新版本可用，当前版本：§e" + localVersion + "§a，最新版本：§e" + latestVersion + "§a 请前往: §b" + RELEASES_URL + " §f【§b点击前往喵~§f】");
            } else {
                PlatformHolder.get().getLogger().info("哇塞，喵呜现在是最新版本！QwQ");
            }
        } catch (Exception e) {
            PlatformHolder.get().getLogger().warning("检测更新时出错: " + e.getMessage());
            e.printStackTrace();
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
}

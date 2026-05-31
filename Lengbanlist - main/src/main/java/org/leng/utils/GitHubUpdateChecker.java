package org.leng.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.leng.Lengbanlist;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class GitHubUpdateChecker {
    public static final String RELEASES_URL = "https://github.com/Ukiyograin/Lengbanlist/releases";
    public static final String LATEST_RELEASE_URL = RELEASES_URL + "/latest";

    private static final String GITHUB_API_URL = "https://api.github.com/repos/Ukiyograin/Lengbanlist/releases/latest";
    private static final List<String> STATIC_API_URLS = Arrays.asList(GITHUB_API_URL);
    private static final int TIMEOUT = 3000;
    private static final int MAX_RETRIES = 3;

    static {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            if (Lengbanlist.getInstance() != null) {
                Lengbanlist.getInstance().getLogger().warning("SSL初始化失败: " + e.getMessage());
            }
        }
    }

    public static String getLatestReleaseVersion() throws Exception {
        return fetchJsonFromApi().get("tag_name").getAsString();
    }

    public static String getLatestDownloadUrl() throws Exception {
        JsonObject json = fetchJsonFromApi();
        if (json.has("assets") && json.get("assets").getAsJsonArray().size() > 0) {
            return json.get("assets").getAsJsonArray().get(0).getAsJsonObject().get("browser_download_url").getAsString();
        }
        return getDownloadUrl(json.get("tag_name").getAsString());
    }

    private static JsonObject fetchJsonFromApi() throws Exception {
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            for (String apiUrl : STATIC_API_URLS) {
                try {
                    return doFetch(apiUrl);
                } catch (Exception e) {
                    lastException = e;
                    Lengbanlist.getInstance().getLogger().warning("API 请求失败: " + apiUrl + "（第" + attempt + "次），正在重试...");
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

    public static CompletableFuture<String> getLatestReleaseVersionAsync(Lengbanlist plugin) {
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
            String localVersion = Lengbanlist.getInstance().getDescription().getVersion();
            String latestVersion = getLatestReleaseVersion();
            if (compareVersions(localVersion, latestVersion) < 0) {
                TextComponent mainMessage = new TextComponent("§a喵喵发现有新版本可用，当前版本：§e" + localVersion + "§a，最新版本：§e" + latestVersion + "§a 请前往: §b" + RELEASES_URL);
                TextComponent clickableComponent = new TextComponent("§f【§b点击前往喵~§f】");
                clickableComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RELEASES_URL));
                clickableComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击打开更新页面喵~").create()));
                Lengbanlist.getInstance().getLogger().info(mainMessage.toLegacyText() + " " + clickableComponent.toLegacyText());
            } else {
                Lengbanlist.getInstance().getLogger().info("哇塞，喵呜现在是最新版本！QwQ");
            }
        } catch (Exception e) {
            Lengbanlist.getInstance().getLogger().warning("检测更新时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getDownloadUrl(String version) {
        return RELEASES_URL + "/download/" + version + "/" + getGitHubFileName(version);
    }

    public static String getGitHubFileName(String version) {
        return "Lengbanlist-" + version + ".jar";
    }

    public static String getLocalFileName(String version) {
        return "Lengbanlist - " + version + ".jar";
    }

    public static String generateNewFileName(String currentFileName, String newVersion) {
        if (currentFileName.contains(" - ") && currentFileName.endsWith(".jar")) {
            String baseName = currentFileName.substring(0, currentFileName.lastIndexOf(" - "));
            return baseName + " - " + newVersion + ".jar";
        }
        return getLocalFileName(newVersion);
    }
}

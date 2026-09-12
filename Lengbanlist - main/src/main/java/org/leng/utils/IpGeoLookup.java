package org.leng.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.leng.Lengbanlist;

import java.io.IOException;
import java.util.logging.Level;

public final class IpGeoLookup {

    private static final String API_URL = "https://ip-api.com/json/%s?lang=zh-CN";
    private static final String USER_AGENT = "Lengbanlist-IPLoc/1.0";
    private static final int TIMEOUT_MS = 3000;

    private final Lengbanlist plugin;

    public IpGeoLookup(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public String lookup(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        String url = String.format(API_URL, ip);
        try (HttpHelper http = new HttpHelper(TIMEOUT_MS, TIMEOUT_MS)) {
            String response = http.get(url, USER_AGENT, "*/*");
            JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
            if (!"success".equals(safeGetString(obj, "status"))) {
                plugin.getLogger().warning("[IpGeoLookup] IP API 请求失败: " + safeGetString(obj, "message"));
                return null;
            }
            String country = safeGetString(obj, "country");
            String region = safeGetString(obj, "regionName");
            String city = safeGetString(obj, "city");
            return country + ", " + region + ", " + city;
        } catch (IOException | InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "[IpGeoLookup] 查询失败: " + ip, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static String safeGetString(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : "未知";
    }
}

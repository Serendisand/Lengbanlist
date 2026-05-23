package org.leng.manager;

import org.bukkit.entity.Player;
import org.leng.Lengbanlist;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class IpAssociationManager {
    private final Lengbanlist plugin;

    public IpAssociationManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void recordLogin(Player player) {
        String ip = player.getAddress().getAddress().getHostAddress();
        if (!isRealIp(ip)) return;
        plugin.getDatabaseManager().recordPlayerIp(player.getName(), ip, System.currentTimeMillis());
    }

    public List<String[]> getPlayerIps(String playerName) {
        return plugin.getDatabaseManager().getPlayerIpHistory(playerName);
    }

    public List<String> getPlayersByIp(String ip) {
        return plugin.getDatabaseManager().getPlayersByIpFromHistory(ip);
    }

    public Map<String, List<String>> getAssociatedPlayers(String playerName) {
        Map<String, List<String>> result = new HashMap<>();
        List<String[]> ipHistory = getPlayerIps(playerName);
        for (String[] record : ipHistory) {
            String ip = record[0];
            List<String> players = getPlayersByIp(ip);
            List<String> others = new ArrayList<>();
            for (String p : players) {
                if (!p.equalsIgnoreCase(playerName)) {
                    others.add(p);
                }
            }
            if (!others.isEmpty()) {
                result.put(ip, others);
            }
        }
        return result;
    }

    public Set<String> getAllAssociatedPlayerNames(String playerName) {
        Set<String> all = new HashSet<>();
        Map<String, List<String>> assoc = getAssociatedPlayers(playerName);
        for (List<String> players : assoc.values()) {
            all.addAll(players);
        }
        return all;
    }

    public boolean hasSuspiciousLogin(Player player) {
        String ip = player.getAddress().getAddress().getHostAddress();
        if (!isRealIp(ip)) return false;
        List<String> players = getPlayersByIp(ip);
        for (String p : players) {
            if (!p.equalsIgnoreCase(player.getName())) return true;
        }
        return false;
    }

    public List<String> getSuspiciousLoginDetails(Player player) {
        String ip = player.getAddress().getAddress().getHostAddress();
        List<String> details = new ArrayList<>();
        List<String> players = getPlayersByIp(ip);
        for (String p : players) {
            if (!p.equalsIgnoreCase(player.getName())) {
                details.add(p);
            }
        }
        return details;
    }

    public boolean isVpnIp(String ip) {
        try {
            String apiUrl = "http://ip-api.com/json/" + ip + "?fields=status,proxy,hosting";
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
            if ("success".equals(json.get("status").getAsString())) {
                boolean proxy = json.has("proxy") && json.get("proxy").getAsBoolean();
                boolean hosting = json.has("hosting") && json.get("hosting").getAsBoolean();
                return proxy || hosting;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("VPN检测请求失败: " + e.getMessage());
        }
        return false;
    }

    public static boolean isRealIp(String ip) {
        if (ip == null) return false;
        if (ip.startsWith("10.") || ip.startsWith("172.") || ip.startsWith("192.168.") || ip.startsWith("127.")) return false;
        if (ip.equalsIgnoreCase("::1")) return false;
        if (ip.startsWith("fd")) return false;
        return true;
    }
}

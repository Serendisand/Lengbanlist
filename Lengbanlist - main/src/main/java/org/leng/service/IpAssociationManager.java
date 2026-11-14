package org.leng.service;

import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.net.DownloadService;
import org.leng.net.DownloadSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class IpAssociationManager {
    private static final java.util.regex.Pattern IP_LITERAL =
            java.util.regex.Pattern.compile("[0-9A-Fa-f:.]{2,45}");
    private static final DownloadSettings VPN_SETTINGS =
            new DownloadSettings(3000, 3000, "Lengbanlist-VPNCheck/1.0", true);

    /** 同一 IP 的 VPN 查询结果缓存 10 分钟；查询失败只缓存 1 分钟，避免狂刷外部接口。 */
    private static final long VPN_CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final long VPN_FAILURE_TTL_MS = 60 * 1000L;
    private static final int VPN_CACHE_LIMIT = 2048;

    private record VpnResult(boolean vpn, boolean success, long expiresAt) {}

    private final Lengbanlist plugin;
    private final Map<String, VpnResult> vpnCache = new ConcurrentHashMap<>();
    private volatile DownloadService vpnClient;

    public IpAssociationManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    private DownloadService vpnClient() {
        DownloadService local = vpnClient;
        if (local == null) {
            synchronized (this) {
                if (vpnClient == null) {
                    vpnClient = new DownloadService(VPN_SETTINGS);
                }
                local = vpnClient;
            }
        }
        return local;
    }

    private static String safeGetHostAddress(Player player) {
        java.net.InetSocketAddress addr = player.getAddress();
        if (addr == null || addr.getAddress() == null) return null;
        return addr.getAddress().getHostAddress();
    }

    public List<String[]> getPlayerIps(String playerName) {
        return plugin.getDatabaseManager().getPlayerIpHistory(playerName);
    }

    public List<String> getPlayersByIp(String ip) {
        return plugin.getDatabaseManager().getPlayersByIpFromHistory(ip);
    }

    public static class AltAccount {
        public final String name;
        public final boolean banned;
        public final boolean currentIp;

        public AltAccount(String name, boolean banned, boolean currentIp) {
            this.name = name;
            this.banned = banned;
            this.currentIp = currentIp;
        }
    }

    public List<AltAccount> getAlts(String target) {
        List<AltAccount> result = new ArrayList<>();
        String ip = plugin.getDatabaseManager().getPlayerIp(target);
        if (ip == null || ip.isEmpty()) {
            return result;
        }
        Set<String> own = ownNames(target);
        Set<String> seen = new HashSet<>();
        for (String name : plugin.getDatabaseManager().getPlayersByIp(ip)) {
            if (isOwnAccount(own, name) || !seen.add(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(new AltAccount(name, plugin.getBanManager().isPlayerBanned(name), true));
        }
        for (String name : plugin.getDatabaseManager().getPlayersByIpFromHistory(ip)) {
            if (isOwnAccount(own, name) || !seen.add(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(new AltAccount(name, plugin.getBanManager().isPlayerBanned(name), false));
        }
        return result;
    }

    public Set<String> ownNames(String target) {
        Set<String> names = new HashSet<>(plugin.getDatabaseManager().getIdentityResolver().resolve(target).lowerNames());
        if (target != null && !target.trim().isEmpty()) {
            names.add(target.trim().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static boolean isOwnAccount(Set<String> own, String name) {
        return name == null || own.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    public Map<String, List<String>> getAssociatedPlayers(String playerName) {
        Map<String, List<String>> result = new HashMap<>();
        List<String[]> ipHistory = getPlayerIps(playerName);
        Set<String> own = ownNames(playerName);
        for (String[] record : ipHistory) {
            String ip = record[0];
            List<String> players = getPlayersByIp(ip);
            List<String> others = new ArrayList<>();
            for (String p : players) {
                if (!isOwnAccount(own, p)) {
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

    public List<String> getOtherPlayersOnIp(Player player) {
        String ip = player == null ? null : safeGetHostAddress(player);
        if (ip == null || !isRealIp(ip)) {
            return new ArrayList<>();
        }
        Set<String> own = player.getUniqueId() == null
                ? ownNames(player.getName())
                : ownNames(player.getUniqueId().toString());
        own.add(player.getName().toLowerCase(Locale.ROOT));
        List<String> others = new ArrayList<>();
        for (String p : getPlayersByIp(ip)) {
            if (!isOwnAccount(own, p)) {
                others.add(p);
            }
        }
        return others;
    }

    public boolean isVpnIp(String ip) {
        if (ip == null || !IP_LITERAL.matcher(ip).matches()) {
            return false;
        }
        long now = System.currentTimeMillis();
        VpnResult cached = vpnCache.get(ip);
        if (cached != null) {
            if (cached.expiresAt() > now) {
                return cached.vpn();
            }
            vpnCache.remove(ip, cached);
        }

        boolean vpn = false;
        boolean success = false;
        try {
            String apiUrl = "https://ip-api.com/json/" + ip + "?fields=status,proxy,hosting";
            String response = vpnClient().get(apiUrl, "*/*");
            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            if ("success".equals(json.get("status").getAsString())) {
                boolean proxy = json.has("proxy") && json.get("proxy").getAsBoolean();
                boolean hosting = json.has("hosting") && json.get("hosting").getAsBoolean();
                vpn = proxy || hosting;
                success = true;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("VPN检测请求失败: " + e.getMessage());
        }
        rememberVpnResult(ip, vpn, success, now);
        return vpn;
    }

    private void rememberVpnResult(String ip, boolean vpn, boolean success, long now) {
        if (vpnCache.size() >= VPN_CACHE_LIMIT) {
            vpnCache.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            if (vpnCache.size() >= VPN_CACHE_LIMIT) {
                vpnCache.clear();
            }
        }
        long ttl = success ? VPN_CACHE_TTL_MS : VPN_FAILURE_TTL_MS;
        vpnCache.put(ip, new VpnResult(vpn, success, now + ttl));
    }

    public static boolean isRealIp(String ip) {
        return org.leng.service.PlayerIpService.isRealIP(ip);
    }
}

package org.leng.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;
import org.leng.utils.TimeUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class WebServer {
    private final Lengbanlist plugin;
    private final Gson gson = new Gson();
    private HttpServer server;
    private AuthManager authManager;
    private boolean running;

    public WebServer(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public boolean start() {
        if (running) return true;
        try {
            String host = plugin.getConfig().getString("web.host", "0.0.0.0");
            int port = plugin.getConfig().getInt("web.port", 8080);
            String secret = plugin.getConfig().getString("web.jwt-secret", "change-this-to-a-random-secret-key");
            String username = plugin.getConfig().getString("web.admin-username", "admin");
            String password = plugin.getConfig().getString("web.admin-password", "admin123");

            authManager = new AuthManager(secret, username, password);
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));

            server.createContext("/api/login", this::handleLogin);
            server.createContext("/api/players", this::handlePlayers);
            server.createContext("/api/ban", this::handleBan);
            server.createContext("/api/unban", this::handleUnban);
            server.createContext("/api/stats", this::handleStats);
            server.createContext("/api/history", this::handleHistory);
            server.createContext("/api/bans", this::handleBanList);
            server.createContext("/api/ipbans", this::handleIpBanList);
            server.createContext("/api/mutes", this::handleMuteList);
            server.createContext("/api/reports", this::handleReports);
            server.createContext("/api/mute", this::handleMute);
            server.createContext("/api/unmute", this::handleUnmute);
            server.createContext("/api/warn", this::handleWarn);
            server.createContext("/api/report/action", this::handleReportAction);
            server.createContext("/api/reload", this::handleReload);
            server.createContext("/api/broadcast", this::handleBroadcast);
            server.createContext("/", this::handleRoot);

            server.start();
            running = true;
            String displayHost = host.equals("0.0.0.0") ? "本机IP" : host;
            plugin.getLogger().info("Web管理面板已启动: http://" + displayHost + ":" + port);
            if (host.equals("0.0.0.0")) {
                plugin.getLogger().info("绑定到 0.0.0.0，可从 http://本机IP:" + port + " 访问（如 http://localhost:" + port + "）");
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Web管理面板启动失败: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            running = false;
            plugin.getLogger().info("Web管理面板已关闭");
        }
    }

    public boolean isRunning() {
        return running;
    }

    // ======== Auth ========

    private static class AuthManager {
        private final String secret;
        private final String username;
        private final String passwordHash;
        private static final long TOKEN_EXP_MS = 86400000L;

        AuthManager(String secret, String username, String password) {
            this.secret = secret;
            this.username = username;
            this.passwordHash = sha256(password);
        }

        String login(String user, String pass) {
            if (!username.equals(user) || !sha256(pass).equals(passwordHash)) return null;
            return createToken(username);
        }

        boolean validateToken(String token) {
            return parseToken(token) != null;
        }

        String getUsernameFromToken(String token) {
            JsonObject payload = parseToken(token);
            return payload != null ? payload.get("sub").getAsString() : null;
        }

        private String createToken(String subject) {
            JsonObject header = new JsonObject();
            header.addProperty("alg", "HS256");
            header.addProperty("typ", "JWT");

            JsonObject payload = new JsonObject();
            long now = System.currentTimeMillis() / 1000;
            payload.addProperty("sub", subject);
            payload.addProperty("iat", now);
            payload.addProperty("exp", now + TOKEN_EXP_MS / 1000);

            String encodedHeader = b64url(header.toString());
            String encodedPayload = b64url(payload.toString());
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = hmacSha256(signingInput, secret);

            return signingInput + "." + signature;
        }

        private JsonObject parseToken(String token) {
            try {
                String[] parts = token.split("\\.");
                if (parts.length != 3) return null;
                if (!hmacSha256(parts[0] + "." + parts[1], secret).equals(parts[2])) return null;

                String json = new String(Base64.getUrlDecoder().decode(parts[1]));
                JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
                if (System.currentTimeMillis() / 1000 > payload.get("exp").getAsLong()) return null;
                return payload;
            } catch (Exception e) {
                return null;
            }
        }

        private static String hmacSha256(String data, String key) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                return b64url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static String sha256(String s) {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format("%02x", b));
                return hex.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private static String b64url(String data) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
        }

        private static String b64url(byte[] data) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        }
    }

    // ======== Rate Limiter ========

    private static class RateLimiter {
        private final ConcurrentHashMap<String, long[]> requests = new ConcurrentHashMap<>();
        private static final int MAX_REQUESTS = 60;
        private static final long WINDOW_MS = 60000L;

        boolean isRateLimited(String ip) {
            long now = System.currentTimeMillis();
            long[] window = requests.compute(ip, (key, val) -> {
                if (val == null || now - val[0] > WINDOW_MS) {
                    return new long[]{now, 1};
                }
                val[1]++;
                return val;
            });
            return window[1] > MAX_REQUESTS;
        }

        void cleanup() {
            long cutoff = System.currentTimeMillis() - WINDOW_MS;
            requests.entrySet().removeIf(e -> e.getValue()[0] < cutoff);
        }
    }

    private final RateLimiter rateLimiter = new RateLimiter();

    private boolean checkRateLimit(HttpExchange exchange) {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (rateLimiter.isRateLimited(ip)) {
            sendJson(exchange, 429, "{\"error\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }
        return true;
    }

    // ======== HTTP Helpers ========

    private String extractToken(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return null;
    }

    private boolean requireAuth(HttpExchange exchange) {
        if (!checkRateLimit(exchange)) return false;
        String token = extractToken(exchange);
        if (token == null || !authManager.validateToken(token)) {
            sendJson(exchange, 401, "{\"error\":\"未授权\"}");
            return false;
        }
        return true;
    }

    private void sendJson(HttpExchange exchange, int status, String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException e) {
            plugin.getLogger().warning("Web响应写入失败: " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private void handleOptions(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        try {
            exchange.sendResponseHeaders(204, -1);
        } catch (IOException e) {
            // ignore
        } finally {
            exchange.close();
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            try {
                params.put(URLDecoder.decode(pair[0], "UTF-8"),
                        pair.length > 1 ? URLDecoder.decode(pair[1], "UTF-8") : "");
            } catch (UnsupportedEncodingException e) {
                // ignore
            }
        }
        return params;
    }

    // ======== API Handlers ========

    private void handleLogin(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
            return;
        }
        if (!checkRateLimit(exchange)) return;
        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String token = authManager.login(json.get("username").getAsString(), json.get("password").getAsString());
            if (token == null) {
                sendJson(exchange, 401, "{\"error\":\"用户名或密码错误\"}");
                return;
            }
            sendJson(exchange, 200, "{\"token\":\"" + token + "\",\"username\":\"" + json.get("username").getAsString() + "\"}");
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"请求格式错误\"}");
        }
    }

    private void handlePlayers(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!requireAuth(exchange)) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String q = params.get("q");
        if (q == null || q.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"缺少查询参数 q\"}");
            return;
        }

        try {
            JsonObject result = new JsonObject();
            result.addProperty("query", q);

            if (q.contains(".")) {
                result.addProperty("type", "ip");
                List<String> players = plugin.getDatabaseManager().getPlayersByIpFromHistory(q);
                result.add("players", gson.toJsonTree(players));
            } else {
                result.addProperty("type", "player");
                result.addProperty("player", q);

                Set<String> associated = plugin.getIpAssociationManager().getAllAssociatedPlayerNames(q);
                result.add("associated_players", gson.toJsonTree(new ArrayList<>(associated)));

                List<String[]> ipHistory = plugin.getIpAssociationManager().getPlayerIps(q);
                JsonArray ips = new JsonArray();
                for (String[] record : ipHistory) {
                    JsonObject ipObj = new JsonObject();
                    ipObj.addProperty("ip", record[0]);
                    ipObj.addProperty("first_seen", TimeUtils.timestampToReadable(Long.parseLong(record[1])));
                    ipObj.addProperty("last_seen", TimeUtils.timestampToReadable(Long.parseLong(record[2])));
                    ips.add(ipObj);
                }
                result.add("ips", ips);
            }
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"查询失败\"}");
        }
    }

    private void handleHistory(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!requireAuth(exchange)) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String player = params.get("player");
        if (player == null || player.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"缺少参数 player\"}");
            return;
        }

        try {
            JsonObject result = new JsonObject();
            result.addProperty("player", player);

            JsonArray bans = new JsonArray();
            for (BanEntry entry : plugin.getDatabaseManager().getBansByPlayer(player)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("target", entry.getTarget());
                obj.addProperty("staff", entry.getStaff());
                obj.addProperty("reason", entry.getReason());
                obj.addProperty("end_time", TimeUtils.timestampToReadable(entry.getTime()));
                obj.addProperty("active", entry.isActive());
                obj.addProperty("auto", entry.isAuto());
                bans.add(obj);
            }
            result.add("bans", bans);

            JsonArray mutes = new JsonArray();
            for (MuteEntry entry : plugin.getDatabaseManager().getMutesByPlayer(player)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("staff", entry.getStaff());
                obj.addProperty("reason", entry.getReason());
                obj.addProperty("end_time", TimeUtils.timestampToReadable(entry.getTime()));
                mutes.add(obj);
            }
            result.add("mutes", mutes);

            JsonArray warnings = new JsonArray();
            for (WarnEntry entry : plugin.getWarnManager().getAllWarnings(player)) {
                JsonObject obj = new JsonObject();
                obj.addProperty("staff", entry.getStaff());
                obj.addProperty("reason", entry.getReason());
                obj.addProperty("warn_time", TimeUtils.timestampToReadable(entry.getTime()));
                obj.addProperty("revoked", entry.isRevoked());
                warnings.add(obj);
            }
            result.add("warnings", warnings);

            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"查询历史失败\"}");
        }
    }

    private void handleBan(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String duration = json.has("duration") ? json.get("duration").getAsString() : "7d";
            String reason = json.has("reason") ? json.get("reason").getAsString() : "管理员操作";

            String staff = authManager.getUsernameFromToken(extractToken(exchange));
            if (staff == null) staff = "WebAdmin";

            long durationMs = TimeUtils.parseTime(duration);
            if (durationMs <= 0) durationMs = TimeUtils.daysToMillis(7);
            long endTime = System.currentTimeMillis() + durationMs;

            if (target.contains(".")) {
                plugin.getBanManager().banIp(new BanIpEntry(target, staff, endTime, reason, false));
            } else {
                plugin.getBanManager().banPlayer(new BanEntry(target, staff, endTime, reason, false));
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被封禁，时长: " + TimeUtils.formatDuration(durationMs));
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"封禁失败: " + e.getMessage() + "\"}");
        }
    }

    private void handleUnban(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();

            if (target.contains(".")) {
                plugin.getBanManager().unbanIp(target);
            } else {
                plugin.getBanManager().unbanPlayer(target);
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被解封");
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"解封失败: " + e.getMessage() + "\"}");
        }
    }

    private void handleStats(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!requireAuth(exchange)) return;

        JsonObject stats = new JsonObject();
        List<BanEntry> bans = plugin.getBanManager().getBanList();
        List<BanIpEntry> ipBans = plugin.getBanManager().getBanIpList();
        stats.addProperty("total_bans", bans.size() + ipBans.size());
        stats.addProperty("active_bans", bans.size());
        stats.addProperty("ip_bans", ipBans.size());
        stats.addProperty("mutes", plugin.getMuteManager().getMuteList().size());
        stats.addProperty("warnings", plugin.getWarnManager().getWarnedPlayers().size());
        stats.addProperty("pending_reports", plugin.getReportManager().getPendingReportCount());

        sendJson(exchange, 200, stats.toString());
    }

    private void handleBanList(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!requireAuth(exchange)) return;

        JsonArray bans = new JsonArray();
        for (BanEntry entry : plugin.getBanManager().getBanList()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("target", entry.getTarget());
            obj.addProperty("staff", entry.getStaff());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("end_time", TimeUtils.timestampToReadable(entry.getTime()));
            obj.addProperty("remaining", TimeUtils.getRemainingTime(entry.getTime()));
            obj.addProperty("auto", entry.isAuto());
            bans.add(obj);
        }
        JsonObject result = new JsonObject();
        result.add("bans", bans);
        result.addProperty("total", bans.size());
        sendJson(exchange, 200, result.toString());
    }

    private void handleIpBanList(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!requireAuth(exchange)) return;

        JsonArray bans = new JsonArray();
        for (BanIpEntry entry : plugin.getBanManager().getBanIpList()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("ip", entry.getIp());
            obj.addProperty("staff", entry.getStaff());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("end_time", TimeUtils.timestampToReadable(entry.getTime()));
            obj.addProperty("remaining", TimeUtils.getRemainingTime(entry.getTime()));
            obj.addProperty("auto", entry.isAuto());
            bans.add(obj);
        }
        JsonObject result = new JsonObject();
        result.add("bans", bans);
        result.addProperty("total", bans.size());
        sendJson(exchange, 200, result.toString());
    }

    private void handleMuteList(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!requireAuth(exchange)) return;

        JsonArray mutes = new JsonArray();
        for (MuteEntry entry : plugin.getMuteManager().getMuteList()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("target", entry.getTarget());
            obj.addProperty("staff", entry.getStaff());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("time", TimeUtils.timestampToReadable(entry.getTime()));
            mutes.add(obj);
        }
        JsonObject result = new JsonObject();
        result.add("mutes", mutes);
        result.addProperty("total", mutes.size());
        sendJson(exchange, 200, result.toString());
    }

    private void handleReports(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!requireAuth(exchange)) return;

        JsonArray reports = new JsonArray();
        for (ReportEntry entry : plugin.getReportManager().getPendingReports()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", entry.getId());
            obj.addProperty("target", entry.getTarget());
            obj.addProperty("reporter", entry.getReporter());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("status", entry.getStatus());
            obj.addProperty("timestamp", TimeUtils.timestampToReadable(entry.getTimestamp()));
            reports.add(obj);
        }
        JsonObject result = new JsonObject();
        result.add("reports", reports);
        result.addProperty("total", reports.size());
        sendJson(exchange, 200, result.toString());
    }

    private void handleMute(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "管理员操作";
            String staff = authManager.getUsernameFromToken(extractToken(exchange));
            if (staff == null) staff = "WebAdmin";

            MuteEntry entry = new MuteEntry(target, staff, System.currentTimeMillis(), reason);
            plugin.getMuteManager().mutePlayer(entry);

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被禁言");
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"禁言失败: " + e.getMessage() + "\"}");
        }
    }

    private void handleUnmute(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            plugin.getMuteManager().unmutePlayer(target);

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被解除禁言");
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"解除禁言失败: " + e.getMessage() + "\"}");
        }
    }

    private void handleWarn(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "管理员操作";
            String staff = authManager.getUsernameFromToken(extractToken(exchange));
            if (staff == null) staff = "WebAdmin";

            plugin.getWarnManager().warnPlayer(target, staff, reason);

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被警告");
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"警告失败: " + e.getMessage() + "\"}");
        }
    }

    private void handleReportAction(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String id = json.get("id").getAsString();
            String action = json.get("action").getAsString();

            ReportEntry report = plugin.getReportManager().getReport(id);
            if (report == null) {
                sendJson(exchange, 404, "{\"error\":\"举报不存在\"}");
                return;
            }

            if ("close".equalsIgnoreCase(action)) {
                report.setStatus("已关闭");
                plugin.getReportManager().updateReport(report);
                JsonObject result = new JsonObject();
                result.addProperty("success", true);
                result.addProperty("message", "举报 " + id + " 已关闭");
                sendJson(exchange, 200, result.toString());
            } else {
                sendJson(exchange, 400, "{\"error\":\"未知操作: " + action + "\"}");
            }
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"操作失败: " + e.getMessage() + "\"}");
        }
    }

    private void handleReload(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            plugin.reloadConfig();
            plugin.reloadWebServer();
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "配置已重新加载");
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"重载失败: " + e.getMessage() + "\"}");
        }
    }

    private void handleBroadcast(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"仅支持 POST\"}");
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            String defaultMessage = plugin.getBroadcastFC().getString("default-message");
            int banCount = plugin.getBanManager().getBanList().size();
            int banIpCount = plugin.getBanManager().getBanIpList().size();
            int totalBans = banCount + banIpCount;

            defaultMessage = defaultMessage
                    .replace("%s", String.valueOf(banCount))
                    .replace("%i", String.valueOf(banIpCount))
                    .replace("%t", String.valueOf(totalBans));

            plugin.getServer().broadcastMessage(defaultMessage);

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "已广播封禁人数");
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"广播失败: " + e.getMessage() + "\"}");
        }
    }

    private void handleRoot(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        try {
            java.io.InputStream htmlStream = plugin.getResource("web/index.html");
            if (htmlStream != null) {
                byte[] htmlBytes = htmlStream.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, htmlBytes.length);
                exchange.getResponseBody().write(htmlBytes);
                exchange.close();
                return;
            }
        } catch (IOException e) {
        }
        sendJson(exchange, 200, "{\"name\":\"Lengbanlist Web API\",\"version\":\"" + plugin.getPluginVersion() + "\",\"login\":\"POST /api/login 获取token\",\"usage\":\"在请求头加 Authorization: Bearer <token> 调用其他接口\"}");
    }
}

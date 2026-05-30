package org.leng.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;
import org.leng.utils.TimeUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
            if (!validateWebCredentials(secret, password)) {
                return false;
            }

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

    private boolean validateWebCredentials(String secret, String password) {
        boolean defaultSecret = "change-this-to-a-random-secret-key".equals(secret);
        boolean defaultPassword = "lban123".equals(password) || "admin123".equals(password);
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32 || defaultSecret) {
            plugin.getLogger().severe("Web管理面板启动失败：web.jwt-secret 必须改为至少 32 字节的随机密钥。");
            return false;
        }
        if (password == null || password.trim().isEmpty() || defaultPassword) {
            plugin.getLogger().severe("Web管理面板启动失败：web.admin-password 不能使用默认密码。");
            return false;
        }
        return true;
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
            sendError(exchange, 429, "请求过于频繁，请稍后再试");
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

    private boolean requireFeature(HttpExchange exchange, String feature) {
        if (!plugin.isFeatureEnabled(feature)) {
            sendError(exchange, 403, "此功能已被管理员禁用");
            return false;
        }
        return true;
    }

    private boolean runSync(HttpExchange exchange, Runnable task) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        org.leng.utils.SchedulerUtils.runTask(plugin, () -> {
            try {
                task.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                sendError(exchange, 504, "操作超时");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendError(exchange, 500, "操作被中断");
            return false;
        }
        if (error.get() != null) {
            sendError(exchange, 500, "操作失败");
            return false;
        }
        return true;
    }

    private boolean requireAuth(HttpExchange exchange) {
        if (!checkRateLimit(exchange)) return false;
        String token = extractToken(exchange);
        if (token == null || !authManager.validateToken(token)) {
            sendError(exchange, 401, "未授权");
            return false;
        }
        return true;
    }

    private void sendError(HttpExchange exchange, int status, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        sendJson(exchange, status, error.toString());
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
        int maxBytes = 1024 * 1024;
        try (InputStream is = exchange.getRequestBody(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("请求体超过 1MB 限制");
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
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
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!checkRateLimit(exchange)) return;
        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String token = authManager.login(json.get("username").getAsString(), json.get("password").getAsString());
            if (token == null) {
                sendError(exchange, 401, "用户名或密码错误");
                return;
            }
            sendJson(exchange, 200, gson.toJson(new LoginResponse(token, json.get("username").getAsString())));
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "请求格式错误");
        }
    }

    private static class LoginResponse {
        private final String token;
        private final String username;

        LoginResponse(String token, String username) {
            this.token = token;
            this.username = username;
        }
    }

    private void handlePlayers(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!requireAuth(exchange)) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String q = params.get("q");
        if (q == null || q.isEmpty()) {
            sendError(exchange, 400, "缺少查询参数 q");
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
            sendError(exchange, 500, "查询失败");
        }
    }

    private void handleHistory(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!requireAuth(exchange)) return;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String player = params.get("player");
        if (player == null || player.isEmpty()) {
            sendError(exchange, 400, "缺少参数 player");
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
            sendError(exchange, 500, "查询历史失败");
        }
    }

    private void handleBan(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
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
            final String finalStaff = staff;

            long durationMs = TimeUtils.parseTime(duration);
            if (durationMs <= 0) durationMs = TimeUtils.daysToMillis(7);
            long endTime = TimeUtils.calculateEndTime(durationMs);

            String feature = target.contains(".") ? "ban-ip" : "ban";
            if (!requireFeature(exchange, feature)) return;

            boolean completed = runSync(exchange, () -> {
                if (target.contains(".")) {
                    plugin.getBanManager().banIp(new BanIpEntry(target, finalStaff, endTime, reason, false));
                } else {
                    plugin.getBanManager().banPlayer(new BanEntry(target, finalStaff, endTime, reason, false));
                }
            });
            if (!completed) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被封禁，时长: " + TimeUtils.formatDuration(durationMs));
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "封禁失败: " + e.getMessage());
        }
    }

    private void handleUnban(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "unban")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();

            boolean completed = runSync(exchange, () -> {
                if (target.contains(".")) {
                    plugin.getBanManager().unbanIp(target);
                } else {
                    plugin.getBanManager().unbanPlayer(target);
                }
            });
            if (!completed) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被解封");
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "解封失败: " + e.getMessage());
        }
    }

    private void handleStats(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!requireAuth(exchange)) return;

        JsonObject stats = new JsonObject();
        List<BanEntry> bans = plugin.getBanManager().getBanList();
        List<BanIpEntry> ipBans = plugin.getBanManager().getBanIpList();
        stats.addProperty("plugin_version", plugin.getPluginVersion());
        stats.addProperty("online_players", plugin.getServer().getOnlinePlayers().size());
        stats.addProperty("max_players", plugin.getServer().getMaxPlayers());
        stats.addProperty("database_status", plugin.getDatabaseManager().isHealthy() ? "正常" : "异常");
        stats.addProperty("database_type", getDatabaseType());
        stats.addProperty("total_bans", bans.size() + ipBans.size());
        stats.addProperty("active_bans", bans.size());
        stats.addProperty("ip_bans", ipBans.size());
        stats.addProperty("mutes", plugin.getMuteManager().getMuteList().size());
        stats.addProperty("warnings", plugin.getWarnManager().getWarnedPlayers().size());
        stats.addProperty("pending_reports", plugin.getReportManager().getPendingReportCount());

        JsonArray recentBans = new JsonArray();
        for (BanEntry entry : plugin.getDatabaseManager().getRecentBans(5)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("target", entry.getTarget());
            obj.addProperty("staff", entry.getStaff());
            obj.addProperty("reason", entry.getReason());
            obj.addProperty("end_time", TimeUtils.timestampToReadable(entry.getTime()));
            obj.addProperty("remaining", TimeUtils.getRemainingTime(entry.getTime()));
            obj.addProperty("active", entry.isActive() && entry.getTime() > System.currentTimeMillis());
            obj.addProperty("auto", entry.isAuto());
            recentBans.add(obj);
        }
        stats.add("recent_bans", recentBans);

        sendJson(exchange, 200, stats.toString());
    }

    private String getDatabaseType() {
        return plugin.getDatabaseManager().getDatabaseProductName();
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
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "mute")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String duration = json.has("duration") ? json.get("duration").getAsString() : "7d";
            String reason = json.has("reason") ? json.get("reason").getAsString() : "管理员操作";
            String staff = authManager.getUsernameFromToken(extractToken(exchange));
            if (staff == null) staff = "WebAdmin";
            final String finalStaff = staff;

            long durationMs = TimeUtils.parseTime(duration);
            if (durationMs <= 0) durationMs = TimeUtils.daysToMillis(7);
            long endTime = TimeUtils.calculateEndTime(durationMs);

            boolean completed = runSync(exchange, () -> plugin.getMuteManager().mutePlayer(new MuteEntry(target, finalStaff, endTime, reason)));
            if (!completed) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被禁言，时长: " + TimeUtils.formatDuration(durationMs));
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "禁言失败: " + e.getMessage());
        }
    }

    private void handleUnmute(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "mute")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            boolean completed = runSync(exchange, () -> plugin.getMuteManager().unmutePlayer(target));
            if (!completed) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被解除禁言");
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "解除禁言失败: " + e.getMessage());
        }
    }

    private void handleWarn(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "warn")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "管理员操作";
            String staff = authManager.getUsernameFromToken(extractToken(exchange));
            if (staff == null) staff = "WebAdmin";
            final String finalStaff = staff;

            boolean completed = runSync(exchange, () -> plugin.getWarnManager().warnPlayer(target, finalStaff, reason));
            if (!completed) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被警告");
            sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "警告失败: " + e.getMessage());
        }
    }

    private void handleReportAction(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "admin")) return;

        try {
            JsonObject json = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String id = json.get("id").getAsString();
            String action = json.get("action").getAsString();

            ReportEntry report = plugin.getReportManager().getReport(id);
            if (report == null) {
                sendError(exchange, 404, "举报不存在");
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
                sendError(exchange, 400, "未知操作: " + action);
            }
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 400, "操作失败: " + e.getMessage());
        }
    }

    private void handleReload(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "reload")) return;

        try {
            boolean completed = runSync(exchange, () -> {
            ModelManager.getInstance().reloadModel();

            File broadcastFile = new File(plugin.getDataFolder(), "broadcast.yml");
            if (broadcastFile.exists()) {
                try {
                    plugin.getBroadcastFC().load(broadcastFile);
                } catch (Exception e) {
                    plugin.getLogger().warning("重载broadcast.yml失败: " + e.getMessage());
                }
            }
            File chatConfigFile = new File(plugin.getDataFolder(), "chatconfig.yml");
            if (chatConfigFile.exists()) {
                try {
                    plugin.getChatConfig().load(chatConfigFile);
                } catch (Exception e) {
                    plugin.getLogger().warning("重载chatconfig.yml失败: " + e.getMessage());
                }
            }
            });
            if (!completed) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "配置已重新加载");
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendError(exchange, 500, "重载失败: " + e.getMessage());
            return;
        }
        try {
            plugin.reloadWebServer();
        } catch (Exception ignored) {
        }
    }

    private void handleBroadcast(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "broadcast")) return;

        try {
            String defaultMessage = plugin.getBroadcastFC().getString("default-message");
            int banCount = plugin.getBanManager().getBanList().size();
            int banIpCount = plugin.getBanManager().getBanIpList().size();
            int totalBans = banCount + banIpCount;

            defaultMessage = defaultMessage
                    .replace("%s", String.valueOf(banCount))
                    .replace("%i", String.valueOf(banIpCount))
                    .replace("%t", String.valueOf(totalBans));

            String message = defaultMessage;
            boolean completed = runSync(exchange, () -> plugin.getServer().broadcastMessage(message));
            if (!completed) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "已广播封禁人数");
            sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            sendError(exchange, 500, "广播失败: " + e.getMessage());
        }
    }

    private void handleRoot(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) { handleOptions(exchange); return; }
        try {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/")) path = "/index.html";

            String resourcePath = "web" + path;
            java.io.InputStream stream = plugin.getResource(resourcePath);
            if (stream != null) {
                byte[] bytes = readAllBytes(stream);
                exchange.getResponseHeaders().set("Content-Type", getMimeType(path));
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
                return;
            }

            if (path.equals("/index.html")) {
                JsonObject info = new JsonObject();
                info.addProperty("name", "Lengbanlist Web API");
                info.addProperty("version", plugin.getPluginVersion());
                info.addProperty("login", "POST /api/login 获取token");
                info.addProperty("usage", "在请求头加 Authorization: Bearer <token> 调用其他接口");
                sendJson(exchange, 200, info.toString());
                return;
            }
        } catch (IOException e) {
        }
        sendError(exchange, 404, "Not Found");
    }

    private byte[] readAllBytes(InputStream stream) throws IOException {
        try (InputStream input = stream; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private String getMimeType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css")) return "text/css; charset=UTF-8";
        if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (path.endsWith(".cur")) return "image/x-win-bitmap";
        if (path.endsWith(".ani")) return "application/x-navi-animation";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}

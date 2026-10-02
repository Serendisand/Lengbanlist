package org.leng.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.WarnEntry;
import org.leng.utils.TimeUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class PlayerController extends WebController {

    public PlayerController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/players", this::handlePlayers);
        server.createContext("/api/online", this::handleOnline);
        server.createContext("/api/kick", this::handleKick);
        server.createContext("/api/history", this::handleHistory);
    }

    private void handlePlayers(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;
        try {
            Map<String, String> params = parseQuery(exchange);
            String query = params.get("q");
            if (query == null || query.trim().isEmpty()) {
                WebResponse.sendError(exchange, 400, "缺少 q 参数");
                return;
            }
            query = query.trim();

            JsonObject result = new JsonObject();
            result.addProperty("query", query);
            if (query.contains(".") || query.contains(":")) {
                result.addProperty("type", "ip");
                JsonArray players = new JsonArray();
                for (String name : plugin.getDatabaseManager().getPlayersByIpFromHistory(query)) {
                    players.add(name);
                }
                result.add("players", players);
            } else {
                result.addProperty("type", "player");
                JsonArray associated = new JsonArray();
                for (String name : plugin.getIpAssociationManager().getAllAssociatedPlayerNames(query)) {
                    if (name != null && !name.equalsIgnoreCase(query)) {
                        associated.add(name);
                    }
                }
                result.add("associated_players", associated);

                JsonArray ips = new JsonArray();
                for (String[] row : plugin.getDatabaseManager().getPlayerIpHistory(query)) {
                    if (row == null || row.length < 3) {
                        continue;
                    }
                    JsonObject o = new JsonObject();
                    o.addProperty("ip", row[0]);
                    o.addProperty("first_seen", TimeUtils.timestampToReadable(parseLongSafe(row[1])));
                    o.addProperty("last_seen", TimeUtils.timestampToReadable(parseLongSafe(row[2])));
                    ips.add(o);
                }
                result.add("ips", ips);
            }
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 500, "查询失败");
        }
    }

    private long parseLongSafe(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void handleOnline(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        AtomicReference<JsonArray> playersRef = new AtomicReference<>(new JsonArray());
        boolean completed = runSync(exchange, () -> {
            JsonArray players = new JsonArray();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", player.getName());
                obj.addProperty("uuid", player.getUniqueId().toString());
                obj.addProperty("ping", player.getPing());
                players.add(obj);
            }
            playersRef.set(players);
        });
        if (!completed) return;

        JsonArray players = playersRef.get();
        JsonObject result = new JsonObject();
        result.add("players", players);
        result.addProperty("total", players.size());
        WebResponse.sendJson(exchange, 200, result.toString());
    }

    private void handleKick(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "kick")) return;

        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
            String target = json.get("target").getAsString();
            String reason = json.has("reason") ? json.get("reason").getAsString() : "管理员操作";
            final String finalReason = reason;
            AtomicReference<String> outcome = new AtomicReference<>("ok");
            String staff = authManager.resolveActor(extractToken(exchange));
            final String finalStaff = staff;
            boolean completed = runSync(exchange, () -> {
                Player player = plugin.getServer().getPlayerExact(target);
                if (player == null) {
                    outcome.set("404");
                    return;
                }
                if (!plugin.getPunishmentGate().canPunish(plugin.getPunishmentGate().webOperatorWeight(), target)) {
                    outcome.set("403");
                    return;
                }
                player.kickPlayer(finalReason);
                plugin.getAuditManager().log("踢出", finalStaff, target, reason);
            });
            if (!completed) return;
            if ("404".equals(outcome.get())) {
                WebResponse.sendError(exchange, 404, "玩家 " + target + " 不在线");
                return;
            }
            if ("403".equals(outcome.get())) {
                WebResponse.sendError(exchange, 403, "目标权重高于操作者,无法执行");
                return;
            }
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", target + " 已被踢出");
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "踢出失败: " + e.getMessage());
        }
    }

    private void handleHistory(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange)) return;

        try {
            Map<String, String> params = parseQuery(exchange);
            String target = params.get("player");
            if (target == null || target.isEmpty()) {
                target = params.get("target");
            }
            if (target == null || target.isEmpty()) {
                WebResponse.sendError(exchange, 400, "缺少 target 参数");
                return;
            }
            List<BanEntry> banHistory = plugin.getDatabaseManager().getBansByPlayer(target);
            List<WarnEntry> warnings = plugin.getWarnManager().getAllWarnings(target);
            Map<Long, Long> banStarts = plugin.getDatabaseManager().getStartTimesByPlayer("bans", target);
            Map<Long, Long> muteStarts = plugin.getDatabaseManager().getStartTimesByPlayer("mutes", target);

            JsonArray banArr = new JsonArray();
            for (BanEntry b : banHistory) {
                JsonObject o = new JsonObject();
                o.addProperty("type", "ban");
                o.addProperty("target", target);
                o.addProperty("staff", b.staff());
                o.addProperty("end_time", b.time());
                o.addProperty("reason", b.reason());
                o.addProperty("active", b.active());
                Long banStart = banStarts.get(b.time());
                o.addProperty("duration", TimeUtils.formatIssuedDuration(banStart == null ? 0L : banStart, b.time()));
                o.addProperty("remaining", TimeUtils.formatRemaining(b.time()));
                banArr.add(o);
            }
            JsonArray muteArr = new JsonArray();
            for (MuteEntry m : plugin.getDatabaseManager().getMutesByPlayer(target)) {
                JsonObject o = new JsonObject();
                o.addProperty("staff", m.staff());
                o.addProperty("reason", m.reason());
                o.addProperty("end_time", m.time());
                Long muteStart = muteStarts.get(m.time());
                o.addProperty("duration", TimeUtils.formatIssuedDuration(muteStart == null ? 0L : muteStart, m.time()));
                o.addProperty("remaining", TimeUtils.formatRemaining(m.time()));
                o.addProperty("active", m.time() > System.currentTimeMillis());
                muteArr.add(o);
            }
            JsonArray warnArr = new JsonArray();
            for (WarnEntry w : warnings) {
                JsonObject o = new JsonObject();
                o.addProperty("type", "warn");
                o.addProperty("id", w.id());
                o.addProperty("staff", w.staff());
                o.addProperty("time", w.time());
                o.addProperty("warn_time", w.time());
                o.addProperty("reason", w.reason());
                o.addProperty("revoked", w.revoked());
                warnArr.add(o);
            }
            JsonObject result = new JsonObject();
            result.addProperty("target", target);
            result.addProperty("player", target);
            result.add("bans", banArr);
            result.add("mutes", muteArr);
            result.add("warnings", warnArr);
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 500, "查询失败");
        }
    }

    private static Map<String, String> parseQuery(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                try {
                    params.put(java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                            java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
                } catch (java.io.UnsupportedEncodingException ignored) {}
            }
        }
        return params;
    }
}

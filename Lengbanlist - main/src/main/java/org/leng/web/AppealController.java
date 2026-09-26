package org.leng.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.manager.AppealManager;
import org.leng.object.AppealEntry;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AppealController extends WebController {

    public AppealController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/public/appeal/precheck", this::handlePrecheck);
        server.createContext("/api/public/appeal/submit", this::handleSubmit);
        server.createContext("/api/public/appeal/status", this::handleStatus);
        server.createContext("/api/appeals", this::handleList);
        server.createContext("/api/appeals/handle", this::handleHandle);
    }

    private boolean requirePublic(HttpExchange exchange, String method) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return false;
        }
        if (!method.equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 " + method);
            return false;
        }
        if (!checkRateLimit(exchange)) {
            return false;
        }
        AppealManager appeals = plugin.getAppealManager();
        if (appeals == null || !appeals.isPublicEnabled()) {
            WebResponse.sendError(exchange, 403, "该服务器未开放网页申诉");
            return false;
        }
        return true;
    }

    private String stringField(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : "";
    }

    private void handlePrecheck(HttpExchange exchange) {
        if (!requirePublic(exchange, "POST")) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
            AppealManager.Precheck result = plugin.getAppealManager().precheck(stringField(json, "player"));
            JsonObject out = new JsonObject();
            out.addProperty("ok", result.ok());
            out.addProperty("message", result.message());
            if (result.ok()) {
                out.addProperty("player", result.player());
                out.addProperty("reason", result.reason());
                out.addProperty("ban_ends_at", result.banEndsAt());
                out.addProperty("ticket", result.ticket());
            }
            WebResponse.sendJson(exchange, 200, out.toString());
        } catch (IOException | RuntimeException e) {
            WebResponse.sendError(exchange, 400, "请求格式不正确");
        }
    }

    private void handleSubmit(HttpExchange exchange) {
        if (!requirePublic(exchange, "POST")) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
            AppealManager.Submit result = plugin.getAppealManager().submit(
                    stringField(json, "ticket"), stringField(json, "contact"), stringField(json, "reason"));
            JsonObject out = new JsonObject();
            out.addProperty("ok", result.ok());
            out.addProperty("message", result.message());
            if (result.ok()) {
                out.addProperty("ticket", result.ticket());
            }
            WebResponse.sendJson(exchange, 200, out.toString());
        } catch (IOException | RuntimeException e) {
            WebResponse.sendError(exchange, 400, "请求格式不正确");
        }
    }

    private void handleStatus(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 GET");
            return;
        }
        if (!checkRateLimit(exchange)) {
            return;
        }
        AppealManager appeals = plugin.getAppealManager();
        if (appeals == null || !appeals.isPublicEnabled()) {
            WebResponse.sendError(exchange, 403, "该服务器未开放网页申诉");
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String ticket = "";
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("ticket=")) {
                    ticket = java.net.URLDecoder.decode(part.substring("ticket=".length()), java.nio.charset.StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        AppealEntry entry = appeals.findByTicket(ticket);
        JsonObject out = new JsonObject();
        if (entry == null) {
            out.addProperty("ok", false);
            out.addProperty("message", "没有查到该申诉，请确认查询码是否正确");
            WebResponse.sendJson(exchange, 200, out.toString());
            return;
        }
        out.addProperty("ok", true);
        out.addProperty("status", entry.status());
        out.addProperty("createdAt", entry.createdAt());
        out.addProperty("handledAt", entry.handledAt());
        out.addProperty("response", entry.response());
        WebResponse.sendJson(exchange, 200, out.toString());
    }

    private void handleList(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 GET");
            return;
        }
        if (!requireAuth(exchange)) {
            return;
        }
        if (!requireFeature(exchange, "appeal")) {
            return;
        }
        String status = "";
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("status=")) {
                    status = java.net.URLDecoder.decode(part.substring("status=".length()), java.nio.charset.StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        List<AppealEntry> appeals = plugin.getAppealManager().list(status, 200);
        JsonArray arr = new JsonArray();
        for (AppealEntry entry : appeals) {
            arr.add(toJson(entry));
        }
        JsonObject out = new JsonObject();
        out.add("appeals", arr);
        out.addProperty("total", arr.size());
        out.addProperty("pending", plugin.getAppealManager().countPending());
        WebResponse.sendJson(exchange, 200, out.toString());
    }

    private JsonObject toJson(AppealEntry entry) {
        JsonObject o = new JsonObject();
        o.addProperty("id", entry.id());
        o.addProperty("target", entry.target());
        o.addProperty("contact", entry.contact());
        o.addProperty("reason", entry.reason());
        o.addProperty("status", entry.status());
        o.addProperty("createdAt", entry.createdAt());
        o.addProperty("handledBy", entry.handledBy());
        o.addProperty("handledAt", entry.handledAt());
        o.addProperty("response", entry.response());
        return o;
    }

    private void handleHandle(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange)) {
            return;
        }
        if (!requireFeature(exchange, "appeal")) {
            return;
        }
        String body;
        try {
            body = WebResponse.readBody(exchange);
        } catch (IOException e) {
            WebResponse.sendError(exchange, 400, "读取请求失败");
            return;
        }
        JsonObject json;
        try {
            json = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            WebResponse.sendError(exchange, 400, "请求格式不正确");
            return;
        }
        String id = stringField(json, "id");
        String action = stringField(json, "action");
        String response = stringField(json, "response");
        if (id.isEmpty() || (!"approve".equals(action) && !"reject".equals(action))) {
            WebResponse.sendError(exchange, 400, "缺少申诉 ID 或操作类型");
            return;
        }
        boolean approve = "approve".equals(action);
        AtomicBoolean handled = new AtomicBoolean(false);
        boolean done = runSync(exchange, () -> handled.set(plugin.getAppealManager()
                .handle(id, approve, webOperator(), response)));
        if (!done) {
            return;
        }
        if (!handled.get()) {
            WebResponse.sendError(exchange, 409, approve ? "处理失败：该玩家当前没有生效中的封禁，或申诉已被处理" : "处理失败：申诉不存在或已被处理");
            return;
        }
        JsonObject out = new JsonObject();
        out.addProperty("success", true);
        out.addProperty("message", approve ? "已通过申诉并解除封禁" : "已驳回申诉");
        WebResponse.sendJson(exchange, 200, out.toString());
    }

    private String webOperator() {
        String username = plugin.getConfig().getString("web.admin-username", "admin");
        return username == null || username.trim().isEmpty() ? "Web面板" : username.trim();
    }
}

package org.leng.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.util.SchedulerUtils;

import java.io.IOException;

public class AdminController extends WebController {

    public AdminController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/reload", this::handleReload);
        server.createContext("/api/broadcast", this::handleBroadcast);
    }

    private void handleReload(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "reload")) return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }

        try {
            String body = WebResponse.readBody(exchange);
            JsonObject json = body.trim().isEmpty() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
            boolean restartWeb = !json.has("web") || json.get("web").getAsBoolean();

            if (!restartWeb) {
                WebResponse.sendError(exchange, 400, "当前仅支持 web 热重载");
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "Web 配置已热重载");
            WebResponse.sendJson(exchange, 200, result.toString());

            SchedulerUtils.runTask(plugin, () -> {
                if (!plugin.reloadWebServer()) {
                    plugin.getLogger().warning("Web 热重载失败：web.jwt-secret / web.admin-password 校验未通过，"
                            + "面板已下线，请修正配置后再次重载或重启服务器");
                }
            });
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "请求格式错误: " + e.getMessage());
        }
    }

    private void handleBroadcast(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "broadcast")) return;

        org.leng.api.BroadcastService broadcast = plugin.getBroadcastService();
        if (broadcast == null) {
            WebResponse.sendError(exchange, 503, "广播扩展未安装");
            return;
        }

        try {
            java.util.concurrent.atomic.AtomicBoolean sent = new java.util.concurrent.atomic.AtomicBoolean();
            boolean completed = runSync(exchange, () -> sent.set(broadcast.broadcastNow(null)));
            if (!completed) return;
            if (!sent.get()) {
                WebResponse.sendError(exchange, 500, "广播失败：请检查广播扩展的消息配置");
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "已广播封禁人数");
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 500, "广播失败: " + e.getMessage());
        }
    }
}

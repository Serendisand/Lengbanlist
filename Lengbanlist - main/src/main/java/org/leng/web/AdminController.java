package org.leng.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;

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
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "reload")) return;
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
            boolean restartWeb = !json.has("web") || json.get("web").getAsBoolean();

            if (!restartWeb) {
                WebResponse.sendError(exchange, 400, "当前仅支持 web 热重载");
                return;
            }

            boolean[] ok = new boolean[1];
            boolean completed = runSync(exchange, () -> ok[0] = plugin.reloadWebServer());
            if (!completed) return;
            if (!ok[0]) {
                WebResponse.sendError(exchange, 500, "Web 配置校验未通过（web.jwt-secret/web.admin-password 等），面板已下线，请修正配置后再次调用本接口或重启服务器");
                return;
            }
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "Web 配置已热重载");
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "请求格式错误: " + e.getMessage());
        }
    }

    private void handleBroadcast(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "broadcast")) return;

        try {
            String defaultMessage = plugin.getBroadcastFC().getString("default-message", "本服已封禁 %s 名玩家和 %i 个 IP，合计 %t 项处罚");
            int banCount = plugin.getBanManager().countActiveBans();
            int banIpCount = plugin.getBanManager().countActiveIpBans();
            int totalBans = banCount + banIpCount;

            String message = defaultMessage
                    .replace("%s", String.valueOf(banCount))
                    .replace("%i", String.valueOf(banIpCount))
                    .replace("%t", String.valueOf(totalBans));

            final String broadcast = message;
            boolean completed = runSync(exchange, () ->
                    plugin.getServer().broadcastMessage(plugin.prefix() + " " + broadcast));
            if (!completed) return;

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "已广播封禁人数");
            WebResponse.sendJson(exchange, 200, result.toString());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 500, "广播失败: " + e.getMessage());
        }
    }
}

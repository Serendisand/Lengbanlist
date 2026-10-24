package org.leng.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.leng.Lengbanlist;
import org.leng.object.ReportEntry;
import org.leng.service.ReportManager;

import java.io.IOException;
import java.util.List;

public class ReportController extends WebController {

    public ReportController(Lengbanlist plugin, AuthManager authManager) {
        super(plugin, authManager);
    }

    @Override
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/reports", this::handleReports);
        server.createContext("/api/report/action", this::handleReportAction);
    }

    private void handleReports(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "admin")) return;

        List<ReportEntry> reports = plugin.getReportManager().getPendingReports();
        JsonArray arr = new JsonArray();
        for (ReportEntry r : reports) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("target", r.target());
            o.addProperty("reporter", r.reporter());
            o.addProperty("reason", r.reason());
            o.addProperty("status", r.status());
            o.addProperty("timestamp", r.timestamp());
            arr.add(o);
        }
        JsonObject result = new JsonObject();
        result.add("reports", arr);
        result.addProperty("total", arr.size());
        WebResponse.sendJson(exchange, 200, result.toString());
    }

    private void handleReportAction(HttpExchange exchange) {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            WebResponse.handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            WebResponse.sendError(exchange, 405, "仅支持 POST");
            return;
        }
        if (!requireAuth(exchange) || !requireFeature(exchange, "admin")) return;

        try {
            JsonObject json = JsonParser.parseString(WebResponse.readBody(exchange)).getAsJsonObject();
            String id = json.get("id").getAsString();
            String action = json.get("action").getAsString();
            ReportEntry report = plugin.getReportManager().getReport(id);
            if (report == null) {
                WebResponse.sendError(exchange, 404, "举报不存在");
                return;
            }
            String actor = authManager.resolveActor(extractToken(exchange));
            ReportManager manager = plugin.getReportManager();
            ReportManager.ActionResult result;
            if ("accept".equalsIgnoreCase(action)) {
                result = manager.acceptReport(id, actor);
            } else if ("close".equalsIgnoreCase(action)) {
                result = manager.closeReport(id, actor);
            } else {
                WebResponse.sendError(exchange, 400, "未知 action: " + action);
                return;
            }
            if (result == ReportManager.ActionResult.NOT_FOUND) {
                WebResponse.sendError(exchange, 404, "举报不存在");
                return;
            }
            if (result == ReportManager.ActionResult.FINALIZED
                    || result == ReportManager.ActionResult.ALREADY_ACCEPTED) {
                WebResponse.sendError(exchange, 409, "举报已处理或已受理");
                return;
            }
            if (result == ReportManager.ActionResult.DB_ERROR) {
                WebResponse.sendError(exchange, 500, "数据库写入失败");
                return;
            }
            JsonObject resultBody = new JsonObject();
            resultBody.addProperty("success", true);
            resultBody.addProperty("message", "举报 " + id + " 处理完成");
            WebResponse.sendJson(exchange, 200, resultBody.toString());
        } catch (IOException e) {
            WebResponse.sendError(exchange, 413, e.getMessage());
        } catch (Exception e) {
            WebResponse.sendError(exchange, 400, "请求格式错误");
        }
    }
}

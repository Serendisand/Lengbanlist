package org.leng.manager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.leng.Lengbanlist;
import org.leng.object.AuditEntry;
import org.leng.utils.SchedulerUtils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public class AuditManager {
    private final Lengbanlist plugin;
    private final DatabaseManager db;

    public AuditManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void log(String action, String actor, String target, String reason) {
        log(action, actor, target, reason, true);
    }

    public void log(String action, String actor, String target, String reason, boolean success) {
        if (!plugin.isFeatureEnabled("audit")) {
            return;
        }
        if (actor == null || actor.trim().isEmpty()) {
            actor = "System";
        }
        db.addAuditLog(actor, action, target == null ? "" : target, reason == null ? "" : reason, success);
        notifyWebhook(action, actor, target, reason);
    }

    public List<AuditEntry> getLogs(String actorOrTarget, int limit) {
        return db.getAuditLogs(actorOrTarget == null ? "" : actorOrTarget.trim(), Math.max(1, Math.min(limit, 200)));
    }

    public List<AuditEntry> getLogsByActor(String actor, int limit) {
        return db.getAuditLogsByActor(actor == null ? "" : actor.trim(), Math.max(1, Math.min(limit, 200)));
    }

    private void notifyWebhook(String action, String actor, String target, String reason) {
        boolean enabled = plugin.getConfig().getBoolean("webhook.enabled", false);
        String webhookUrl = plugin.getConfig().getString("webhook.url", "");
        if (!enabled || webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }
        final String fUrl = webhookUrl.trim();
        final String fAction = action;
        final String fActor = actor;
        final String fTarget = target == null ? "" : target;
        final String fReason = reason == null ? "" : reason;
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                JSONArray fields = new JSONArray();
                fields.put(field("操作", fAction, true));
                fields.put(field("操作人", fActor, true));
                fields.put(field("目标", fTarget.isEmpty() ? "—" : fTarget, true));
                fields.put(field("原因", fReason.isEmpty() ? "—" : fReason, false));

                JSONObject embed = new JSONObject();
                embed.put("title", "Lengbanlist 审计日志");
                embed.put("color", 0xE74C3C);
                embed.put("fields", fields);
                embed.put("timestamp", Instant.now().toString());

                JSONObject payload = new JSONObject();
                payload.put("username", plugin.getConfig().getString("webhook.username", "Lengbanlist"));
                String avatarUrl = plugin.getConfig().getString("webhook.avatar-url", "");
                if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                    payload.put("avatar_url", avatarUrl.trim());
                }
                payload.put("embeds", new JSONArray().put(embed));

                HttpURLConnection conn = (HttpURLConnection) new URL(fUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    plugin.getLogger().warning("Webhook 推送失败，HTTP " + code);
                }
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Webhook 推送异常: " + e.getMessage());
            }
        });
    }

    private JSONObject field(String name, String value, boolean inline) {
        return new JSONObject().put("name", name).put("value", value).put("inline", inline);
    }
}

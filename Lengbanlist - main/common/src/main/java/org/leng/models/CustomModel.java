package org.leng.models;

import org.leng.platform.MessageSink;
import org.leng.platform.PlatformHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomModel implements Model {
    private final String name;
    private final Map<String, Object> config;

    public CustomModel(String name, Map<String, Object> config) {
        this.name = name;
        this.config = config == null ? Collections.<String, Object>emptyMap() : config;
    }

    @Override
    public String getName() {
        return name;
    }

    /** 构建占位符 map（Java 8 兼容，替代 Map.of） */
    private static Map<String, String> placeholders(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            if (kv[i + 1] != null) {
                map.put(kv[i], kv[i + 1]);
            }
        }
        return map;
    }

    private String getString(String path, String def) {
        Object value = config.get(path);
        if (value == null) {
            return def;
        }
        String s = String.valueOf(value);
        // 去除 YAML 引号包裹
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private List<String> getStringList(String path) {
        Object value = config.get(path);
        if (value instanceof List) {
            List<?> raw = (List<?>) value;
            List<String> result = new java.util.ArrayList<>();
            for (Object o : raw) {
                if (o != null) result.add(String.valueOf(o));
            }
            return result;
        }
        return Collections.emptyList();
    }

    private String msg(String key) {
        return msg(key, Collections.<String, String>emptyMap());
    }

    private String msg(String key, Map<String, String> placeholders) {
        String template = getString("messages." + key, null);
        if (template == null || template.isEmpty()) {
            return "§c[模型 " + name + " 缺少配置: " + key + "]";
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    @Override
    public void showHelp(MessageSink sender) {
        List<String> helpLines = getStringList("help");
        if (helpLines.isEmpty()) {
            sender.sendMessage("§6" + name + " 模型 - 没有自定义帮助信息");
            return;
        }
        String version = PlatformHolder.get() == null ? "" : PlatformHolder.get().getPluginVersion();
        for (String line : helpLines) {
            sender.sendMessage(line.replace("{version}", version == null ? "" : version));
        }
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return msg("toggle-broadcast", placeholders("enabled", enabled ? "开启" : "关闭"));
    }

    @Override
    public String reloadConfig() {
        return msg("reload-config");
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return msg("add-ban", placeholders("player", player, "days", Model.formatBanDays(days), "reason", reason));
    }

    @Override
    public String removeBan(String player) {
        return msg("remove-ban", placeholders("player", player));
    }

    @Override
    public String addMute(String player, String reason) {
        return msg("add-mute", placeholders("player", player, "reason", reason));
    }

    @Override
    public String removeMute(String player) {
        return msg("remove-mute", placeholders("player", player));
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return msg("add-ban-ip", placeholders("ip", ip, "days", Model.formatBanDays(days), "reason", reason));
    }

    @Override
    public String removeBanIp(String ip) {
        return msg("remove-ban-ip", placeholders("ip", ip));
    }

    @Override
    public String addWarn(String player, String reason) {
        return msg("add-warn", placeholders("player", player, "reason", reason));
    }

    @Override
    public String removeWarn(String player) {
        return msg("remove-warn", placeholders("player", player));
    }

    @Override
    public String getKickMessage(String reason) {
        return msg("get-kick-message", placeholders("reason", reason));
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return msg("on-kick-success", placeholders("player", playerName, "reason", reason));
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return msg("history-empty", placeholders("player", player));
        }
        StringBuilder sb = new StringBuilder();
        sb.append(msg("history-header", placeholders("player", player)));
        for (String entry : entries) {
            sb.append("\n").append(msg("history-entry-format", placeholders("entry", entry)));
        }
        return sb.toString();
    }

    @Override
    public String onMuteCommandBlocked() {
        return msg("mute-command-blocked");
    }

    @Override
    public String onWarnOffline(String player, String reason) {
        return msg("warn-offline", placeholders("player", player, "reason", reason));
    }

    @Override
    public String getPendingWarningsNotice(int count) {
        return msg("pending-warnings-notice", placeholders("count", String.valueOf(count)));
    }

    @Override
    public String getExpiryReminder(String type, String target, String remaining) {
        return msg("expiry-reminder", placeholders("type", type, "target", target, "remaining", remaining));
    }

    @Override
    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return msg("escalated-ban", placeholders("player", player, "count", String.valueOf(offenseCount), "duration", duration));
    }

    @Override
    public String getAltsResult(String player, int count) {
        return msg("alts-result", placeholders("player", player, "count", String.valueOf(count)));
    }

    @Override
    public String getNoAlts(String player) {
        return msg("no-alts", placeholders("player", player));
    }

    @Override
    public String onReportBan(String player, String duration) {
        return msg("on-report-ban", placeholders("player", player, "duration", duration));
    }

    @Override
    public String getExportResult(int count) {
        return msg("export-result", placeholders("count", String.valueOf(count)));
    }

    @Override
    public String getVerifyResult(boolean valid, int count) {
        String key = valid ? "verify-result-valid" : "verify-result-invalid";
        return msg(key, placeholders("count", String.valueOf(count)));
    }

    @Override
    public String getSyncStatus(String detail) {
        return msg("sync-status", placeholders("detail", detail));
    }

    @Override
    public String getImmunityDenied(String target) {
        return msg("immunity-denied", placeholders("target", target));
    }

    @Override
    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return msg("rollback-preview", placeholders("count", String.valueOf(matched), "actor", actor, "time", timeRange));
    }

    @Override
    public String getRollbackResult(int matched, int executed, int skipped) {
        return msg("rollback-result", placeholders("matched", String.valueOf(matched), "executed", String.valueOf(executed), "skipped", String.valueOf(skipped)));
    }

    @Override
    public String getRollbackNoRecords(String actor) {
        return msg("rollback-no-records", placeholders("actor", actor));
    }
}

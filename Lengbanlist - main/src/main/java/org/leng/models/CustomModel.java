package org.leng.models;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CustomModel implements Model {
    private final String name;
    private final FileConfiguration config;
    private final FileConfiguration base;

    public CustomModel(String name, FileConfiguration config) {
        this(name, config, null);
    }

    public CustomModel(String name, FileConfiguration config, FileConfiguration base) {
        this.name = name;
        this.config = config;
        this.base = base;
    }

    @Override
    public String getName() {
        return name;
    }

    private String raw(String path) {
        String value = config.getString(path);
        if ((value == null || value.isEmpty()) && base != null) {
            value = base.getString(path);
        }
        return value;
    }

    private static Map<String, String> placeholders(String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            if (kv[i + 1] != null) {
                map.put(kv[i], kv[i + 1]);
            }
        }
        return map;
    }

    private String msg(String key) {
        return msg(key, Collections.<String, String>emptyMap());
    }

    private String banDays(int days) {
        String forever = raw("days-forever");
        String suffix = raw("days-suffix");
        return days == Integer.MAX_VALUE
                ? (forever == null ? "永久" : forever)
                : days + (suffix == null ? " 天" : suffix);
    }

    private String msg(String key, Map<String, String> placeholders) {
        String template = raw("messages." + key);
        if (template == null || template.isEmpty()) {
            return "§c[模型 " + name + " 缺少配置: " + key + "]";
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String msgOr(String key, String fallback, Map<String, String> placeholders) {
        String template = raw("messages." + key);
        if (template == null || template.isEmpty()) {
            template = fallback;
        }
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    @Override
    public void showHelp(CommandSender sender) {
        List<String> helpLines = buildHelpLines();
        if (helpLines.isEmpty()) {
            Utils.sendMessage(sender, "§6" + name + " 模型 - 没有自定义帮助信息");
            return;
        }
        String version = Lengbanlist.getInstance().getPluginVersion();
        for (String line : helpLines) {
            Utils.sendMessage(sender, line.replace("{version}", version == null ? "" : version));
        }
    }

    private static final java.util.regex.Pattern COLOR_PATTERN =
            java.util.regex.Pattern.compile("[§&][0-9a-fk-orA-FK-ORxX]");

    private List<String> buildHelpLines() {
        List<String> lines = new ArrayList<>();
        List<String> own = config.getStringList("help");
        if (!own.isEmpty()) {
            lines.addAll(own);
        } else if (base != null) {
            lines.addAll(base.getStringList("help"));
        }
        if (lines.isEmpty()) {
            return lines;
        }
        ConfigurationSection overrides = config.getConfigurationSection("help-overrides");
        if (overrides == null) {
            return lines;
        }
        for (String key : overrides.getKeys(false)) {
            String replacement = overrides.getString(key);
            if (replacement == null || replacement.isEmpty()) {
                continue;
            }
            int index = indexOfHelpLine(lines, key);
            if (index < 0) {
                continue;
            }
            boolean bannerLine = key != null && key.trim().startsWith("#");
            lines.set(index, bannerLine ? replacement : mergeHelpLine(lines.get(index), replacement));
        }
        return lines;
    }

    private static final String HELP_DESCRIPTION_SEPARATOR = " §7- §3";
    private static final String HELP_DESCRIPTION_MARKER = ">";
    private static final String DEFAULT_HELP_BULLET = "§2✦ ";

    private String mergeHelpLine(String baseLine, String replacement) {
        if (!replacement.startsWith(HELP_DESCRIPTION_MARKER)) {
            return replacement;
        }
        String description = replacement.substring(HELP_DESCRIPTION_MARKER.length()).trim();
        String merged = applyHelpBullet(baseLine);
        int separator = merged.indexOf(HELP_DESCRIPTION_SEPARATOR);
        if (separator < 0) {
            return description;
        }
        return merged.substring(0, separator + HELP_DESCRIPTION_SEPARATOR.length()) + description;
    }

    private String applyHelpBullet(String baseLine) {
        String bullet = raw("help-bullet");
        if (bullet == null || bullet.isEmpty() || !baseLine.startsWith(DEFAULT_HELP_BULLET)) {
            return baseLine;
        }
        return bullet + " " + baseLine.substring(DEFAULT_HELP_BULLET.length());
    }

    private int indexOfHelpLine(List<String> lines, String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "#top":
                return lines.isEmpty() ? -1 : 0;
            case "#title":
                return indexOfHelpLineContaining(lines, "║");
            case "#split":
                return indexOfHelpLineContaining(lines, "╠");
            case "#bottom":
                return indexOfHelpLineContaining(lines, "╚");
            case "#version":
                return indexOfHelpLineContaining(lines, "{version}");
            default:
                return indexOfHelpCommand(lines, normalized);
        }
    }

    private int indexOfHelpLineContaining(List<String> lines, String marker) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(marker)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfHelpCommand(List<String> lines, String key) {
        String needle = key.startsWith("/") ? key.substring(1) : key;
        if (needle.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < lines.size(); i++) {
            String usage = commandUsageOf(lines.get(i));
            if (usage == null) {
                continue;
            }
            if (usage.equals("/" + needle) || usage.startsWith("/" + needle + " ")) {
                return i;
            }
        }
        return -1;
    }

    private String commandUsageOf(String line) {
        String plain = COLOR_PATTERN.matcher(line == null ? "" : line).replaceAll("");
        int separator = plain.indexOf(" - ");
        String commandPart = separator >= 0 ? plain.substring(0, separator) : plain;
        int slash = commandPart.indexOf('/');
        return slash < 0 ? null : commandPart.substring(slash).trim();
    }

    @Override
    public String toggleBroadcast(boolean enabled) {

        String on = raw("enabled-on");
        String off = raw("enabled-off");
        return msg("toggle-broadcast", placeholders("enabled",
                enabled ? (on == null ? "开启" : on) : (off == null ? "关闭" : off)));
    }

    @Override
    public String reloadConfig() {
        return msg("reload-config");
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return msg("add-ban", placeholders("player", player, "days", banDays(days), "reason", reason));
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
        return msg("add-ban-ip", placeholders("ip", ip, "days", banDays(days), "reason", reason));
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

        String footer = msg("history-footer", placeholders("player", player));
        if (footer != null && !footer.startsWith("§c[模型") && !footer.trim().isEmpty()) {
            sb.append("\n").append(footer);
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
    public String getFreezeNotify(String reason) {
        return msgOr("freeze-notify", "§c你已被管理员暂时冻结，理由：§f{reason}",
                placeholders("reason", reason));
    }

    @Override
    public String getFreezeTitle() {
        return msgOr("freeze-title", "§c你已被冻结",
                Collections.<String, String>emptyMap());
    }

    @Override
    public String getFreezeSubtitle(String reason) {
        return msgOr("freeze-subtitle", "§f理由：§e{reason}",
                placeholders("reason", reason));
    }

    @Override
    public String getFreezeReminder() {
        return msgOr("freeze-reminder", "§c你正处于冻结状态，请联系管理员处理。",
                Collections.<String, String>emptyMap());
    }

    @Override
    public String onFreeze(String player, String reason) {
        return msgOr("on-freeze", "§a已冻结玩家 §f{player}§a，理由：§f{reason}",
                placeholders("player", player, "reason", reason));
    }

    @Override
    public String onUnfreeze(String player) {
        return msgOr("on-unfreeze", "§a已解冻玩家 §f{player}",
                placeholders("player", player));
    }

    @Override
    public String onVanish(boolean vanished) {
        String on = raw("enabled-on");
        String off = raw("enabled-off");
        return msgOr("on-vanish", "§a隐身模式已{state}",
                placeholders("state", vanished
                        ? (on == null ? "开启" : on)
                        : (off == null ? "关闭" : off)));
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

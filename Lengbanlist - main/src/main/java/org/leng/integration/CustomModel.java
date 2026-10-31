package org.leng.integration;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.leng.Lengbanlist;
import org.leng.api.CommandSpec;
import org.leng.command.CommandRegistry;
import org.leng.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.leng.models.Model;

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
        helpLines = appendExtensionCommands(helpLines, sender);
        Lengbanlist plugin = Lengbanlist.getInstance();
        String version = plugin == null ? "" : plugin.getPluginVersion();
        for (String line : helpLines) {
            Utils.sendMessage(sender, line.replace("{version}", version == null ? "" : version));
        }
    }

    /** 帮助框的底边。自动补的命令行插在它之前，免得掉到框外面去。 */
    private static final String HELP_FOOTER_MARKER = "╚";

    /**
     * 把<b>用户装的扩展</b>提供的命令归入"扩展"分区。
     *
     * <p>模型文件里的 help 列表是给核心命令手工编排的，而扩展作者改不了别人的模型文件——
     * 不自动补的话，装完扩展谁也不知道多了哪些命令。
     *
     * <p>模型里已经<b>手工写过</b>的扩展命令行（典型是功能从核心搬进扩展后留下的旧行）
     * 不能简单当成"已记录"跳过，否则它会一直躺在原来的类目下（比如 {@code /lban tp}
     * 留在"杂项"）。这里把这类行从原位置摘走、原样挪进"扩展"分区，既修正了归类，
     * 又保留模型作者写的文案与本地化。过滤规则：功能没开的不列、发送者没权限的不显示、
     * 内置提供者手工编排的照旧不碰。
     */
    private List<String> appendExtensionCommands(List<String> lines, CommandSender sender) {
        Lengbanlist plugin = Lengbanlist.getInstance();
        if (plugin == null || plugin.getExtensionRegistry() == null || plugin.getCommandRegistry() == null
                || !plugin.getConfig().getBoolean("help.extension-commands", true)) {
            return lines;
        }
        // 手工编排过的内置功能键：这些行是刻意的，不重复也不挪动
        Set<String> builtinDocumented = new HashSet<>();
        // 扩展功能键 -> 模型里的旧行（可能不止一行，比如带别名的写法）
        Map<String, List<String>> staleLines = new HashMap<>();
        List<String> kept = new ArrayList<>(lines.size());
        for (String line : lines) {
            String feature = plugin.getCommandRegistry().featureForUsage(commandUsageOf(line));
            if (feature == null) {
                kept.add(line);
            } else if (plugin.getExtensionRegistry().isBuiltinProvided(feature)) {
                builtinDocumented.add(feature);
                kept.add(line);
            } else {
                staleLines.computeIfAbsent(feature, key -> new ArrayList<>()).add(line);
            }
        }

        List<String> moved = new ArrayList<>();
        List<String> generated = new ArrayList<>();
        Set<String> seen = new HashSet<>(builtinDocumented);
        for (CommandSpec spec : plugin.getExtensionRegistry().commandSpecs()) {
            if (plugin.getExtensionRegistry().isBuiltinProvided(spec.feature())
                    || !plugin.isFeatureActive(spec.feature())
                    || !seen.add(spec.feature())) {
                continue;
            }
            if (!spec.permission().isEmpty() && !sender.hasPermission(spec.permission())) {
                continue;
            }
            List<String> manual = staleLines.get(spec.feature());
            if (manual != null) {
                moved.addAll(manual);
            } else {
                generated.add(applyHelpBullet(DEFAULT_HELP_BULLET + "§b" + spec.usage()
                        + " §7- §3" + spec.description()));
            }
        }
        if (moved.isEmpty() && generated.isEmpty()) {
            return kept;
        }
        List<String> additions = new ArrayList<>();
        additions.add("§6§l◆ 扩展");
        additions.addAll(moved);
        additions.addAll(generated);
        return insertBeforeFooter(kept, additions);
    }

    /** 把新增行插在底部边框之前；模型没画边框就追加到末尾。 */
    static List<String> insertBeforeFooter(List<String> lines, List<String> additions) {
        List<String> merged = new ArrayList<>(lines);
        int footer = -1;
        for (int i = merged.size() - 1; i >= 0; i--) {
            if (merged.get(i).contains(HELP_FOOTER_MARKER)) {
                footer = i;
                break;
            }
        }
        if (footer < 0) {
            merged.addAll(additions);
        } else {
            merged.addAll(footer, additions);
        }
        return merged;
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
        if (!lines.isEmpty()) {
            ConfigurationSection overrides = config.getConfigurationSection("help-overrides");
            if (overrides != null) {
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
            }
        }
        return filterDisabledFeatures(lines);
    }

    private List<String> filterDisabledFeatures(List<String> lines) {
        Lengbanlist plugin = Lengbanlist.getInstance();
        if (plugin == null) {
            return lines;
        }
        List<String> filtered = new ArrayList<>(lines.size());
        CommandRegistry registry = plugin.getCommandRegistry();
        for (String line : lines) {
            String usage = commandUsageOf(line);
            // 注册表优先：功能搬进 Lua 扩展之后，只有它能说出这行属于哪个功能键
            String feature = registry == null
                    ? CommandRegistry.declareFeatureForUsage(usage)
                    : registry.featureForUsage(usage);
            if (feature != null && !plugin.isFeatureActive(feature)) {
                continue;
            }
            filtered.add(line);
        }
        return filtered;
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
    public String addBan(String player, String duration, String reason) {
        return msg("add-ban", placeholders("player", player, "days", duration, "reason", reason));
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
    public String addBanIp(String ip, String duration, String reason) {
        return msg("add-ban-ip", placeholders("ip", ip, "days", duration, "reason", reason));
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

    @Override
    public String getConsole(String key) {
        return raw("console." + key);
    }
}

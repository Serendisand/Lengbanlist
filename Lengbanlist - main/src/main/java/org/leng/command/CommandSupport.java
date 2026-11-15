package org.leng.command;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.util.Utils;

import java.util.Arrays;
import java.util.Locale;

/** 命令包内重复度最高的参数解析与提示文案。 */
final class CommandSupport {

    private CommandSupport() {
    }

    record Args(boolean silent, String[] values) {
    }

    static Args parseSilent(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
            return new Args(true, Arrays.copyOfRange(args, 1, args.length));
        }
        return new Args(false, args);
    }

    static String presetReason(Lengbanlist plugin, String input) {
        if (input == null || !plugin.getConfig().isConfigurationSection("preset-reasons")) {
            return input;
        }
        String value = plugin.getConfig().getString("preset-reasons." + input.toLowerCase(Locale.ROOT));
        return value != null ? value : input;
    }

    static void sendTimeFormat(CommandSender sender, String prefix, String action) {
        String p = prefix == null ? "" : prefix;
        Utils.sendMessage(sender, p + "§c时间格式错误喵，请使用以下格式:");
        Utils.sendMessage(sender, p + "§c - 10s: 秒 (10 秒)");
        Utils.sendMessage(sender, p + "§c - 5m: 分钟 (5 分钟)");
        Utils.sendMessage(sender, p + "§c - 2h: 小时 (2 小时)");
        Utils.sendMessage(sender, p + "§c - 7d: 天 (7 天)");
        Utils.sendMessage(sender, p + "§c - 1w: 周 (1 周，等于 7 天)");
        Utils.sendMessage(sender, p + "§c - 1M: 月 (1 月，按 30 天计算)");
        Utils.sendMessage(sender, p + "§c - 1y: 年 (1 年，按 365 天计算)");
        Utils.sendMessage(sender, p + "§c - forever: 永久" + action);
        Utils.sendMessage(sender, p + "§c - auto: 自动计算" + action + "时间");
    }
}

package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Klee implements Model {
    @Override
    public String getName() {
        return "Klee";
    }

    @Override
    public void showHelp(CommandSender sender) {
        String[] lines = {
                "§c╔══════════════════════════════════╗",
                "§c║ §6§oLengbanlist 帮助 - 可莉风格 §c║",
                "§c╠══════════════════════════════════╣",
                "§6§l◆ 处罚管理",
                "§e✦ §b/lban add <玩家名> <天数> <原因> §7- §3添加封禁，坏孩子要去禁闭室啦！",
                "§7  = §b/ban",
                "§e✦ §b/lban remove <玩家名> §7- §3移除封禁，这次就原谅你啦！",
                "§7  = §b/unban",
                "§e✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP，不许偷偷回来捣蛋！",
                "§e✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家，要安静一点哦！",
                "§7  = §b/mute",
                "§e✦ §b/lban unmute <玩家名> §7- §3解除禁言，可以说话啦！",
                "§7  = §b/unmute",
                "§e✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次就要被关禁闭啦！",
                "§7  = §b/warn",
                "§e✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告。",
                "§7  = §b/unwarn",
                "§e✦ §b/kick <玩家名> <原因> §7- §3踢出玩家，蹦蹦炸弹出击！",
                "§e✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间。",
                "§6§l◆ 查询信息",
                "§e✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态。",
                "§e✦ §b/lban history <玩家名> §7- §3查看捣蛋记录。",
                "§7  = §b/history",
                "§e✦ §b/report <玩家名> <原因> §7- §3举报坏孩子，可莉会告诉琴团长！",
                "§e✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址。",
                "§6§l◆ 杂项",
                "§e✦ §b/lban list §7- §3查看封禁名单。",
                "§e✦ §b/lban list-mute §7- §3查看禁言列表。",
                "§7  = §b/listmute",
                "§e✦ §b/lban a §7- §3广播封禁人数。",
                "§e✦ §b/lban toggle §7- §3开关自动广播。",
                "§e✦ §b/lban open §7- §3打开可视化操作界面。",
                "§e✦ §b/lban model <模型名称> §7- §3切换模型。",
                "§e✦ §b/lban reload §7- §3重新加载配置。",
                "§e✦ §b/lban info §7- §3查看插件信息。",
                "§c╚══════════════════════════════════╝",
                "§e♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 可莉 Klee"
        };

        for (String line : lines) {
            Utils.sendMessage(sender, line);
        }
    }

    @Override
    public String getKickMessage(String reason) {
        return "§c╔══════════════════════════╗\n" +
               "§c║   §6可莉的禁闭通知  §c║\n" +
               "§c╠══════════════════════════╣\n" +
               "§6💣 你被可莉送出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§6下次不可以再捣蛋啦，不然琴团长会生气的！\n" +
               "§c╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§c✧ 可莉说：§a" + playerName + " §e被送去禁闭啦！\n" +
               "§6原因: §f" + reason + "\n" +
               "§c蹦蹦炸弹，完成任务！";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§c可莉说：§a自动广播已经" + (enabled ? "打开啦！" : "关掉啦！") + " 大家都要听规则哦！";
    }

    @Override
    public String reloadConfig() {
        return "§c可莉说：§a配置重新加载好啦！可莉没有炸坏它哦！";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§c可莉说：§a" + player + " 被关禁闭 " + Model.formatBanDays(days) + "啦，原因是：" + reason + "。坏孩子要反省哦！";
    }

    @Override
    public String removeBan(String player) {
        return "§c可莉说：§a" + player + " 从禁闭室出来啦！以后要做好孩子哦！";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§c可莉说：§a" + player + " 被禁言啦，原因是：" + reason + "。现在要安静一点！";
    }

    @Override
    public String removeMute(String player) {
        return "§c可莉说：§a" + player + " 可以继续说话啦！不可以说坏话哦！";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§c可莉说：§aIP " + ip + " 被封禁 " + Model.formatBanDays(days) + "啦，原因是：" + reason + "。不许偷偷回来！";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§c可莉说：§aIP " + ip + " 的封禁解除啦！";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§c可莉说：§a玩家 " + player + " 被警告啦，原因是：" + reason + "。三次就要去禁闭室啦！";
    }

    @Override
    public String removeWarn(String player) {
        return "§c可莉说：§a玩家 " + player + " 的警告记录被擦掉啦！";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§c可莉说：§a" + player + " 没有捣蛋记录，是好孩子！奖励一朵小红花！";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§c可莉说：§a可莉找到 ").append(player).append(" 的捣蛋记录啦：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§c可莉说：§7这些事情要告诉琴团长！下次不可以再犯啦！");
        return sb.toString().trim();
    }
}

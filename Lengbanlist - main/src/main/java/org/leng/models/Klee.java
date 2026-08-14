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
                "§e✦ §b/lban add <可选>-s <必填>玩家名 <必填>天数 <必填>原因 §7- §3添加封禁，坏孩子要去禁闭室啦！",
                "§7  = §b/ban",
                "§e✦ §b/lban remove <必填>玩家名 §7- §3移除封禁，这次就原谅你啦！",
                "§7  = §b/unban",
                "§e✦ §b/ban-ip <可选>-s <必填>IP地址 <必填>天数 <必填>原因 §7- §3封禁 IP，不许偷偷回来捣蛋！",
                "§e✦ §b/lban mute <可选>-s <必填>玩家名 <必填>原因 §7- §3禁言玩家，要安静一点哦！",
                "§7  = §b/mute",
                "§e✦ §b/lban unmute <可选>-s <必填>玩家名 §7- §3解除禁言，可以说话啦！",
                "§7  = §b/unmute",
                "§e✦ §b/lban warn <必填>玩家名 <必填>原因 §7- §3警告玩家，三次就要被关禁闭啦！",
                "§7  = §b/warn",
                "§e✦ §b/lban unwarn <必填>玩家名 §7- §3移除玩家警告。",
                "§7  = §b/unwarn",
                "§e✦ §b/kick <必填>玩家名 <可选>原因 §7- §3踢出玩家，蹦蹦炸弹出击！",
                "§e✦ §b/setban <必填>玩家名/IP <必填>时间/forever/auto <必填>原因 §7- §3修改封禁时间。",
                "§6§l◆ 查询信息",
                "§e✦ §b/lban check <必填>玩家名/IP §7- §3检查封禁状态。",
                "§e✦ §b/lban history <必填>玩家名 §7- §3查看捣蛋记录。",
                "§7  = §b/history",
                "§e✦ §b/report <必填>玩家名 <必填>原因 §7- §3举报坏孩子，可莉会告诉琴团长！",
                "§e✦ §b/lban getip <可选>玩家名 §7- §3查询玩家 IP 地址。",
                "§e✦ §b/lban alts <必填>玩家名 §7- §3查询同IP小号，看谁在躲猫猫！",
                "§e✦ §b/lban audit <可选>操作人 §7- §3查看审计日志。",
                "§e✦ §b/lban audit export §7- §3导出审计日志，可莉要存档啦！",
                "§e✦ §b/lban audit verify §7- §3校验审计日志有没有被捣蛋鬼改过。",
                "§e✦ §b/lban sync §7- §3查看跨服同步状态。",
                "§e✦ §b/lban rollback <操作人> <开始时间> <结束时间> <可选>操作类型 §7- §3回滚操作，可莉来帮忙擦掉！",
                "§6§l◆ 杂项",
                "§e✦ §b/lban list §7- §3查看封禁名单。",
                "§e✦ §b/lban list-mute §7- §3查看禁言列表。",
                "§7  = §b/listmute",
                "§e✦ §b/lban a §7- §3广播封禁人数。",
                "§e✦ §b/lban toggle §7- §3开关自动广播。",
                "§e✦ §b/lban open §7- §3打开可视化操作界面。",
                "§e✦ §b/lban model <必填>模型名称 §7- §3切换模型。",
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

    @Override
    public String onMuteCommandBlocked() {
        return "§c可莉说：§6禁言的时候不能使用这个命令哦，要乖乖安静啦！";
    }

    @Override
    public String getImmunityDenied(String target) {
        return "§c可莉说：§6呜……" + target + " 的来头好大，可莉帮不了你啦！";
    }

    @Override
    public String onWarnOffline(String player, String reason) {
        return "§c可莉说：§a" + player + " 不在线呢，警告先记下啦，原因是：" + reason + "。等他回来要好好说说哦！";
    }

    @Override
    public String getPendingWarningsNotice(int count) {
        return "§c可莉说：§e你有 " + count + " 条待处理的警告哦，要乖乖的，别被关禁闭啦！";
    }

    @Override
    public String getExpiryReminder(String type, String target, String remaining) {
        return "§c可莉说：§e" + target + " 的" + type + "还有 " + remaining + " 就到期啦，要记得处理哦！";
    }

    @Override
    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§c可莉说：§6" + player + " 已经第 " + offenseCount + " 次捣蛋啦，蹦蹦炸弹自动升级，封禁 " + duration + " 哦！";
    }

    @Override
    public String getAltsResult(String player, int count) {
        return "§c可莉说：§e可莉找到了 " + player + " 的 " + count + " 个小号，都躲在一个 IP 后面哒！";
    }

    @Override
    public String getNoAlts(String player) {
        return "§c可莉说：§a没有找到 " + player + " 的小号哦，是独自一人的好孩子！";
    }

    @Override
    public String onReportBan(String player, String duration) {
        return "§c可莉说：§a举报确认啦，" + player + " 被封禁 " + duration + " 啦，坏孩子要好好反省哦！";
    }

    @Override
    public String getExportResult(int count) {
        return "§c可莉说：§a审计日志导出成功，一共 " + count + " 条，可莉数得很认真哦！";
    }

    @Override
    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§c可莉说：§a校验通过！" + count + " 条记录都好好的，没有坏孩子动过！";
        }
        return "§c可莉说：§6哎呀，有 " + count + " 条记录被篡改啦，可莉要告诉琴团长！";
    }

    @Override
    public String getSyncStatus(String detail) {
        return "§c可莉说：§a跨服同步状态：§f" + detail;
    }

    @Override
    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§c可莉说：§e嘿嘿，找到 " + actor + " 在 " + timeRange + " 留下的 " + matched + " 个可以拆掉的记录啦！";
    }

    @Override
    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§c可莉说：§a蹦蹦炸弹清场完毕！匹配 " + matched + " 条，执行 " + executed + " 条，跳过 " + skipped + " 条~";
    }

    @Override
    public String getRollbackNoRecords(String actor) {
        return "§c可莉说：§e" + actor + " 那段时间没有留下可以拆的记录，好无聊呀~";
    }
}

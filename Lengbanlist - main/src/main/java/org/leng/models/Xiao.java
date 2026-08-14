package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Xiao implements Model {
    @Override
    public String getName() {
        return "Xiao";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 魈风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <可选>-s <必填>玩家名 <必填>天数 <必填>原因 §7- §3添加封禁，不守规矩就封了！");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <必填>玩家名 §7- §3移除封禁，知错能改就放过他们吧！");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <可选>-s <必填>IP地址 <必填>天数 <必填>原因 §7- §3封禁 IP 地址，别再捣乱了！");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <可选>-s <必填>玩家名 <必填>原因 §7- §3禁言玩家，让他们安静一会儿！");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <可选>-s <必填>玩家名 §7- §3解除禁言，让他们继续说话吧！");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <必填>玩家名 <必填>原因 §7- §3警告玩家，三次警告自动封禁！");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <必填>玩家名 §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <必填>玩家名 <可选>原因 §7- §3踢出捣乱的玩家！");
    Utils.sendMessage(sender, "§2✦ §b/setban <必填>玩家名/IP <必填>时间/forever/auto <必填>原因 §7- §3修改封禁时间，让不守规矩的人好好反省！");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <必填>玩家名/IP §7- §3检查封禁状态，看看谁在捣乱！");
    Utils.sendMessage(sender, "§2✦ §b/lban history <必填>玩家名 §7- §3查看业障记录，让我瞧瞧……");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <必填>玩家名 <必填>原因 §7- §3发现捣乱的家伙？快举报给魈！");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <可选>玩家名 §7- §3查询玩家 IP 地址，看看谁在捣乱！");
    Utils.sendMessage(sender, "§2✦ §b/lban alts <必填>玩家名 §7- §3查询同IP小号，看看他们还有别的身份！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit <可选>操作人 §7- §3查看审计日志，看看谁在捣乱！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit export §7- §3导出审计日志，把证据好好收着！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit verify §7- §3校验审计完整性，看看有没有人动手脚！");
    Utils.sendMessage(sender, "§2✦ §b/lban sync §7- §3查看跨服同步状态，一切尽在掌握！");
    Utils.sendMessage(sender, "§2✦ §b/lban rollback <操作人> <开始时间> <结束时间> <可选>操作类型 §7- §3回滚操作，清除业障。");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看封禁名单，这些家伙真是麻烦！");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数，让大家都知道！");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，想听就听不想听就关！");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面");
    Utils.sendMessage(sender, "§2✦ §b/lban model <必填>模型名称 §7- §3切换模型，试试别的风格吧！");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，说不定能发现新东西！");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 魈 Xiao");
}

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d魈的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被魈踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 魈说：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b维护风起地的和平！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b魈说：§a自动广播已经 " + (enabled ? "开启！" : "关闭！") + " 想听就听，不想听就关！";
    }

    @Override
    public String reloadConfig() {
        return "§b魈说：§a配置重新加载完成！说不定能发现新东西！";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b魈说：§a" + player + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "！不守规矩，就别怪魈无情！";
    }

    @Override
    public String removeBan(String player) {
        return "§b魈说：§a" + player + " 已从封禁名单中移除。知错能改，就放过他们吧！";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b魈说：§a" + player + " 已被禁言，原因是：" + reason + "！让他们安静一会儿吧！";
    }

    @Override
    public String removeMute(String player) {
        return "§b魈说：§a" + player + " 的禁言已解除，可以继续说话了！";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b魈说：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "！别再捣乱了！";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b魈说：§aIP " + ip + " 的封禁已解除，放过他们吧！";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b魈说：§a玩家 " + player + " 已被警告，原因是：" + reason + "！警告三次将被自动封禁！";
    }

    @Override
    public String removeWarn(String player) {
        return "§b魈说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b魈说：§a" + player + " 身上没有业障的气息……此人无需守护。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b魈说：§a" + player + " 的业障记录……让我看看：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b魈说：§7这些业障我已记下。若再犯，我不会袖手旁观。");
        return sb.toString().trim();
    }

    @Override
    public String onMuteCommandBlocked() {
        return "§b魈说：§c禁言期间不能使用该命令。安静待着，别添乱。";
    }

    @Override
    public String getImmunityDenied(String target) {
        return "§b魈说：§c" + target + " 位阶在你之上，我不奉陪。";
    }

    @Override
    public String onWarnOffline(String player, String reason) {
        return "§b魈说：§a" + player + " 不在线，警告已记下，原因是：" + reason + "。等他回来再算账！";
    }

    @Override
    public String getPendingWarningsNotice(int count) {
        return "§b魈说：§e你身上有 " + count + " 条待处理的业障，最好放在心上。";
    }

    @Override
    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b魈说：§e" + target + " 的" + type + "还有 " + remaining + " 就要到期，别忘了处理。";
    }

    @Override
    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b魈说：§c" + player + " 第 " + offenseCount + " 次违规，已自动升级封禁 " + duration + "。屡教不改，就别怪魈无情！";
    }

    @Override
    public String getAltsResult(String player, int count) {
        return "§b魈说：§e查到 " + player + " 有 " + count + " 个同 IP 小号，都在魈的视线之内。";
    }

    @Override
    public String getNoAlts(String player) {
        return "§b魈说：§a" + player + " 没有同 IP 小号，孤身一人，无可疑之处。";
    }

    @Override
    public String onReportBan(String player, String duration) {
        return "§b魈说：§a举报属实，" + player + " 已被封禁 " + duration + "。正义不会缺席！";
    }

    @Override
    public String getExportResult(int count) {
        return "§b魈说：§a审计日志已导出，共 " + count + " 条记录。";
    }

    @Override
    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b魈说：§a校验通过，" + count + " 条记录完整无缺。";
        }
        return "§b魈说：§c校验失败！检测到 " + count + " 条记录被篡改，有人动了手脚！";
    }

    @Override
    public String getSyncStatus(String detail) {
        return "§b魈说：§a跨服同步状态：§f" + detail;
    }

    @Override
    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§b魈说：§e" + actor + " 在 " + timeRange + " 留了 " + matched + " 条可回滚的业障，要一并清除吗？";
    }

    @Override
    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§b魈说：§a业障已净。匹配 " + matched + " 条，执行 " + executed + " 条，跳过 " + skipped + " 条。";
    }

    @Override
    public String getRollbackNoRecords(String actor) {
        return "§b魈说：§e" + actor + " 在那段时间并无业障，无需净化。";
    }
}

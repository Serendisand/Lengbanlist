package org.leng.models;

import org.leng.platform.MessageSink;
import org.leng.platform.PlatformHolder;

import java.util.List;

public class Xiao implements Model {
    @Override
    public String getName() {
        return "Xiao";
    }

@Override
public void showHelp(MessageSink sender) {
    sender.sendMessage("§b╔══════════════════════════════════╗");
    sender.sendMessage("§b║ §2§oLengbanlist 帮助 - 魈风格 §b║");
    sender.sendMessage("§b╠══════════════════════════════════╣");
    sender.sendMessage("§6§l◆ 处罚管理");
    sender.sendMessage("§2✦ §b/lban add <玩家名> <天数> <原因> §7- §3添加封禁，不守规矩就封了！");
    sender.sendMessage("§7  = §b/ban");
    sender.sendMessage("§2✦ §b/lban remove <玩家名> §7- §3移除封禁，知错能改就放过他们吧！");
    sender.sendMessage("§7  = §b/unban");
    sender.sendMessage("§2✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP 地址，别再捣乱了！");
    sender.sendMessage("§2✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家，让他们安静一会儿！");
    sender.sendMessage("§7  = §b/mute");
    sender.sendMessage("§2✦ §b/lban unmute <玩家名> §7- §3解除禁言，让他们继续说话吧！");
    sender.sendMessage("§7  = §b/unmute");
    sender.sendMessage("§2✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次警告自动封禁！");
    sender.sendMessage("§7  = §b/warn");
    sender.sendMessage("§2✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告");
    sender.sendMessage("§7  = §b/unwarn");
    sender.sendMessage("§2✦ §b/kick <玩家名> <原因> §7- §3踢出捣乱的玩家！");
    sender.sendMessage("§2✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间，让不守规矩的人好好反省！");
    sender.sendMessage("§6§l◆ 查询信息");
    sender.sendMessage("§2✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态，看看谁在捣乱！");
    sender.sendMessage("§2✦ §b/lban history <玩家名> §7- §3查看业障记录，让我瞧瞧……");
    sender.sendMessage("§7  = §b/history");
    sender.sendMessage("§2✦ §b/report <玩家名> <原因> §7- §3发现捣乱的家伙？快举报给魈！");
    sender.sendMessage("§2✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址，看看谁在捣乱！");
    sender.sendMessage("§6§l◆ 杂项");
    sender.sendMessage("§2✦ §b/lban list §7- §3查看封禁名单，这些家伙真是麻烦！");
    sender.sendMessage("§2✦ §b/lban list-mute §7- §3查看禁言列表");
    sender.sendMessage("§7  = §b/listmute");
    sender.sendMessage("§2✦ §b/lban a §7- §3广播封禁人数，让大家都知道！");
    sender.sendMessage("§2✦ §b/lban toggle §7- §3开关自动广播，想听就听不想听就关！");
    sender.sendMessage("§2✦ §b/lban open §7- §3打开可视化操作界面");
    sender.sendMessage("§2✦ §b/lban model <模型名称> §7- §3切换模型，试试别的风格吧！");
    sender.sendMessage("§2✦ §b/lban reload §7- §3重新加载配置，说不定能发现新东西！");
    sender.sendMessage("§2✦ §b/lban info §7- §3查看插件信息");
    sender.sendMessage("§b╚══════════════════════════════════╝");
    sender.sendMessage("§2♡ 当前版本: " + PlatformHolder.get().getPluginVersion() + " §7| §b模型: 魈 Xiao");
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

    public String onMuteCommandBlocked() {
        return "§b魈说：§c禁言期间不能使用该命令。安静待着，别添乱。";
    }

    public String onWarnOffline(String player, String reason) {
        return "§b魈说：§a" + player + " 不在线，警告已记下，原因是：" + reason + "。等他回来再算账！";
    }

    public String getPendingWarningsNotice(int count) {
        return "§b魈说：§e你身上有 " + count + " 条待处理的业障，最好放在心上。";
    }

    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b魈说：§e" + target + " 的" + type + "还有 " + remaining + " 就要到期，别忘了处理。";
    }

    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b魈说：§c" + player + " 第 " + offenseCount + " 次违规，已自动升级封禁 " + duration + "。屡教不改，就别怪魈无情！";
    }

    public String getAltsResult(String player, int count) {
        return "§b魈说：§e查到 " + player + " 有 " + count + " 个同 IP 小号，都在魈的视线之内。";
    }

    public String getNoAlts(String player) {
        return "§b魈说：§a" + player + " 没有同 IP 小号，孤身一人，无可疑之处。";
    }

    public String onReportBan(String player, String duration) {
        return "§b魈说：§a举报属实，" + player + " 已被封禁 " + duration + "。正义不会缺席！";
    }

    public String getExportResult(int count) {
        return "§b魈说：§a审计日志已导出，共 " + count + " 条记录。";
    }

    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b魈说：§a校验通过，" + count + " 条记录完整无缺。";
        }
        return "§b魈说：§c校验失败！检测到 " + count + " 条记录被篡改，有人动了手脚！";
    }

    public String getSyncStatus(String detail) {
        return "§b魈说：§a跨服同步状态：§f" + detail;
    }

    public String getImmunityDenied(String target) {
        return "§b魈说：§c" + target + " 位阶在你之上，我不奉陪。";
    }

    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§b魈说：§e" + actor + " 在 " + timeRange + " 留了 " + matched + " 条可回滚的业障，要一并清除吗？";
    }

    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§b魈说：§a业障已净。匹配 " + matched + " 条，执行 " + executed + " 条，跳过 " + skipped + " 条。";
    }

    public String getRollbackNoRecords(String actor) {
        return "§b魈说：§e" + actor + " 在那段时间并无业障，无需净化。";
    }

}

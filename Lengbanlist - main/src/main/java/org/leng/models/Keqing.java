package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Keqing implements Model {
    @Override
    public String getName() {
        return "Keqing";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 刻晴风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <可选>-s <必填>玩家名 <必填>天数 <必填>原因 §7- §3添加封禁，不守规矩就封了！");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <必填>玩家名 §7- §3移除封禁，知错能改善莫大焉！");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <可选>-s <必填>IP地址 <必填>天数 <必填>原因 §7- §3封禁 IP 地址，刻晴绝不手软！");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <可选>-s <必填>玩家名 <必填>原因 §7- §3禁言玩家，让他们安静一会儿！");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <可选>-s <必填>玩家名 §7- §3解除禁言，让他们继续说话吧！");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <必填>玩家名 <必填>原因 §7- §3警告玩家，三次警告自动封禁！");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <必填>玩家名 §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <必填>玩家名 <可选>原因 §7- §3踢出不守规矩的玩家！");
    Utils.sendMessage(sender, "§2✦ §b/setban <必填>玩家名/IP <必填>时间/forever/auto <必填>原因 §7- §3修改封禁时间，效率第一！");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <必填>玩家名/IP §7- §3检查封禁状态，刻晴办事效率第一！");
    Utils.sendMessage(sender, "§2✦ §b/lban history <必填>玩家名 §7- §3调出处罚档案，一目了然！");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <必填>玩家名 <必填>原因 §7- §3发现违规行为？及时举报，刻晴会高效处理！");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <可选>玩家名 §7- §3查询玩家 IP 地址，看看谁在捣乱！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit <可选>操作人 §7- §3查看审计日志，刻晴办事效率第一！");
    Utils.sendMessage(sender, "§2✦ §b/lban alts <必填>玩家名 §7- §3查询同IP小号，一个都别想跑！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit export §7- §3导出审计日志，数据归档要快！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit verify §7- §3校验审计完整性，杜绝数据造假！");
    Utils.sendMessage(sender, "§2✦ §b/lban sync §7- §3查看跨服同步状态，效率第一！");
    Utils.sendMessage(sender, "§2✦ §b/lban rollback <操作人> <开始时间> <结束时间> <可选>操作类型 §7- §3回滚操作，及时纠错！");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看封禁名单，刻晴办事效率第一！");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数，让大家都知道！");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，想听就开不想听就关！");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面");
    Utils.sendMessage(sender, "§2✦ §b/lban model <必填>模型名称 §7- §3切换模型，试试不同的风格吧！");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，效率第一绝不拖沓！");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 刻晴 Keqing");
}

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d刻晴的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被刻晴踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 刻晴说：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b效率第一，刻晴办事！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b刻晴说：§a自动广播已经 " + (enabled ? "开启！" : "关闭！") + " 让大家都知道规则的重要性！";
    }

    @Override
    public String reloadConfig() {
        return "§b刻晴说：§a配置重新加载完成！效率第一，刻晴办事，绝不拖沓！";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b刻晴说：§a" + player + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "！不守规矩，就别怪刻晴无情！";
    }

    @Override
    public String removeBan(String player) {
        return "§b刻晴说：§a" + player + " 已从封禁名单中移除。知错能改，善莫大焉！";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b刻晴说：§a" + player + " 已被禁言，原因是：" + reason + "！让他们安静一会儿吧！";
    }

    @Override
    public String removeMute(String player) {
        return "§b刻晴说：§a" + player + " 的禁言已解除，可以继续说话了！";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b刻晴说：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "！刻晴绝不手软！";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b刻晴说：§aIP " + ip + " 的封禁已解除。知错能改，善莫大焉！";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b刻晴说：§a玩家 " + player + " 已被警告，原因是：" + reason + "！警告三次将被自动封禁！";
    }

    @Override
    public String removeWarn(String player) {
        return "§b刻晴说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b刻晴说：§a系统查询完毕，" + player + " 档案清白，效率第一！";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b刻晴说：§a" + player + " 的处罚档案已调出，刻晴办事，效率第一：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b刻晴说：§7查询完毕，希望此人不要再给玉衡星添乱！");
        return sb.toString().trim();
    }

    @Override
    public String onMuteCommandBlocked() {
        return "§b刻晴说：§c禁言期间别想用命令！安静反省，效率为重，别浪费时间！";
    }

    @Override
    public String onWarnOffline(String player, String reason) {
        return "§b刻晴说：§a" + player + " 不在线，警告已直接记录！原因是：" + reason + "。等他上线，刻晴第一时间跟进！";
    }

    @Override
    public String getPendingWarningsNotice(int count) {
        return "§b刻晴说：§e注意！你有 " + count + " 条待处理警告，立刻整改，别再拖延！";
    }

    @Override
    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b刻晴说：§e提醒：" + target + " 的" + type + "还剩 " + remaining + " 到期！提前安排，别误了进度！";
    }

    @Override
    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b刻晴说：§e" + player + " 第 " + offenseCount + " 次违规，系统自动升级封禁 " + duration + "！屡教不改，就该严办！";
    }

    @Override
    public String getAltsResult(String player, int count) {
        return "§b刻晴说：§a查到了！" + player + " 有 " + count + " 个同IP小号，一个都别想蒙混过关！";
    }

    @Override
    public String getNoAlts(String player) {
        return "§b刻晴说：§a" + player + " 没有同IP小号，干净利落！";
    }

    @Override
    public String onReportBan(String player, String duration) {
        return "§b刻晴说：§a举报确认，封禁 " + player + " " + duration + "！刻晴办事，绝不手软！";
    }

    @Override
    public String getExportResult(int count) {
        return "§b刻晴说：§a审计日志导出完成，共 " + count + " 条！归档完毕，随时可查！";
    }

    @Override
    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b刻晴说：§a校验通过！共 " + count + " 条审计记录完整无损，效率与安全兼备！";
        }
        return "§b刻晴说：§c校验失败！审计日志被动了手脚，共 " + count + " 条记录，必须立刻彻查！";
    }

    @Override
    public String getSyncStatus(String detail) {
        return "§b刻晴说：§e跨服同步状态：" + detail + "！数据一致才有效率，绝不能拖后腿！";
    }

    @Override
    public String getImmunityDenied(String target) {
        return "§b刻晴说：§c" + target + " 权限等级不低于你，这次行动不予批准。";
    }

    @Override
    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§b刻晴说：§e已核算完毕——操作人 " + actor + " 在 " + timeRange + " 有 " + matched + " 条操作待回滚。";
    }

    @Override
    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§b刻晴说：§a回滚完毕。匹配 " + matched + " 条，成功 " + executed + " 条，跳过 " + skipped + " 条。";
    }

    @Override
    public String getRollbackNoRecords(String actor) {
        return "§b刻晴说：§e" + actor + " 在该时间段没有留下可回滚的记录。";
    }
}

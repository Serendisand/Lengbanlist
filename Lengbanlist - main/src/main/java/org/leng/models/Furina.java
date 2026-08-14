package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Furina implements Model {
    @Override
    public String getName() {
        return "Furina";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 芙宁娜风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <可选>-s <必填>玩家名 <必填>天数 <必填>原因 §7- §3加入黑名单！");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <必填>玩家名 §7- §3从黑名单中移除");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <可选>-s <必填>IP地址 <必填>天数 <必填>原因 §7- §3封禁 IP 地址，别再划水啦！");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <可选>-s <必填>玩家名 <必填>原因 §7- §3禁言玩家，让他们安静一会儿！");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <可选>-s <必填>玩家名 §7- §3解除禁言，可以继续说话啦！");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <必填>玩家名 <必填>原因 §7- §3警告玩家，三次警告自动封禁！");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <必填>玩家名 §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <必填>玩家名 <可选>原因 §7- §3踢出划水的家伙！");
    Utils.sendMessage(sender, "§2✦ §b/setban <必填>玩家名/IP <必填>时间/forever/auto <必填>原因 §7- §3修改封禁时间");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <必填>玩家名/IP §7- §3检查封禁状态");
    Utils.sendMessage(sender, "§2✦ §b/lban history <必填>玩家名 §7- §3查阅审判记录，本水神亲自过目！");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <必填>玩家名 <必填>原因 §7- §3向本水神举报违规者！");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <可选>玩家名 §7- §3查询玩家 IP 地址");
    Utils.sendMessage(sender, "§2✦ §b/lban audit <可选>操作人 §7- §3查看审计日志");
    Utils.sendMessage(sender, "§2✦ §b/lban alts <必填>玩家名 §7- §3查询同IP小号，聚光灯下无所遁形！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit export §7- §3导出审计日志，存档以备水神审阅！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit verify §7- §3校验审计完整性，不容他人篡改剧本！");
    Utils.sendMessage(sender, "§2✦ §b/lban sync §7- §3查看跨服同步状态，水酱们时刻在线！");
    Utils.sendMessage(sender, "§2✦ §b/lban rollback <操作人> <开始时间> <结束时间> <可选>操作类型 §7- §3回滚操作，让剧情重演~");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看黑名单");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，水酱们要注意啦！");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面");
    Utils.sendMessage(sender, "§2✦ §b/lban model <必填>模型名称 §7- §3切换模型");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，水神的大脑又清晰啦！");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 芙宁娜 Furina");
}

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d芙宁娜的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被芙宁娜踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 芙宁娜说：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b维护秩序，不容破坏！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b芙宁娜说：§a自动广播已经 " + (enabled ? "开启啦！" : "关闭啦！") + " 水酱们要注意啦！";
    }

    @Override
    public String reloadConfig() {
        return "§b芙宁娜说：§a配置重新加载完成！水神的大脑又清晰啦！";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b芙宁娜说：§a" + player + " 已被加入黑名单！封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。划水可不是好习惯哦！";
    }

    @Override
    public String removeBan(String player) {
        return "§b芙宁娜说：§a" + player + " 已从黑名单中移除啦！知错能改，善莫大焉！";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b芙宁娜说：§a" + player + " 已被禁言，原因是：" + reason + "！让他们安静一会儿吧！";
    }

    @Override
    public String removeMute(String player) {
        return "§b芙宁娜说：§a" + player + " 的禁言已解除，可以继续说话啦！";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b芙宁娜说：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。别再划水啦！";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b芙宁娜说：§aIP " + ip + " 的封禁已解除，给他们一个机会！";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b芙宁娜说：§a玩家 " + player + " 已被警告，原因是：" + reason + "！警告三次将被自动封禁！";
    }

    @Override
    public String removeWarn(String player) {
        return "§b芙宁娜说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b芙宁娜说：§a本水神查阅了 " + player + " 的记录，此人品行端正，毫无污点！值得嘉奖~";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b芙宁娜说：§a咳咳，本水神在此宣判——以下是 ").append(player).append(" 的审判记录：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b芙宁娜说：§7审判结束！希望此人能改过自新，否则下次审判就不止于此了~");
        return sb.toString().trim();
    }

    @Override
    public String onMuteCommandBlocked() {
        return "§b芙宁娜说：§c禁言期间还想动用命令？本水神的舞台，不欢迎不守规矩的观众！";
    }

    @Override
    public String onWarnOffline(String player, String reason) {
        return "§b芙宁娜说：§a" + player + " 竟敢缺席，不过本水神的警告已然送达，原因是：" + reason + "。待他归来，再上演一场好戏吧！";
    }

    @Override
    public String getPendingWarningsNotice(int count) {
        return "§b芙宁娜说：§e幕布即将拉开！你有 " + count + " 条待处理警告等待登场，请好好表现哦~";
    }

    @Override
    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b芙宁娜说：§e演出提示：关于 " + target + " 的" + type + "，还有 " + remaining + " 便要落幕，请管理员做好准备！";
    }

    @Override
    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b芙宁娜说：§e好戏上演！" + player + " 第 " + offenseCount + " 次违规，自动升级封禁 " + duration + "！这般反复的表演，本水神已经看腻了！";
    }

    @Override
    public String getAltsResult(String player, int count) {
        return "§b芙宁娜说：§a本水神轻轻一挥手，便揪出了 " + player + " 的 " + count + " 个小号！聚光灯下，谁也藏不住~";
    }

    @Override
    public String getNoAlts(String player) {
        return "§b芙宁娜说：§a" + player + " 名下并无小号，干净得像一张白纸，值得喝彩~";
    }

    @Override
    public String onReportBan(String player, String duration) {
        return "§b芙宁娜说：§a举报确认！本水神宣判：" + player + " 封禁 " + duration + "！退场吧，这场戏不再需要你了！";
    }

    @Override
    public String getExportResult(int count) {
        return "§b芙宁娜说：§a审计日志导出完毕，共 " + count + " 条，本水神已尽数收入囊中，供后世品鉴~";
    }

    @Override
    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b芙宁娜说：§a审计哈希链完整无缺，共 " + count + " 条，本水神的账目滴水不漏！";
        }
        return "§b芙宁娜说：§c竟有宵小胆敢篡改！审计日志已现破绽，共 " + count + " 条记录，本水神绝不轻饶！";
    }

    @Override
    public String getSyncStatus(String detail) {
        return "§b芙宁娜说：§e跨服同步状态：" + detail + "。各服的水酱们，请配合本水神的演出~";
    }

    @Override
    public String getImmunityDenied(String target) {
        return "§b芙宁娜说：§c哎哟~这场戏的主角 " + target + " 权限可不低呢，本神明可不能随意干涉！";
    }

    @Override
    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§b芙宁娜说：§e哇哦~操作人 " + actor + " 在 " + timeRange + " 的戏份有 " + matched + " 幕可以重演，准备好剧本了吗？";
    }

    @Override
    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§b芙宁娜说：§a完美谢幕！回滚匹配 " + matched + " 幕，成功 " + executed + " 幕，跳过 " + skipped + " 幕~";
    }

    @Override
    public String getRollbackNoRecords(String actor) {
        return "§b芙宁娜说：§e" + actor + " 在那段时间没有登台记录，找无可找呢~";
    }
}

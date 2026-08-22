package org.leng.models;

import org.leng.platform.MessageSink;
import org.leng.platform.PlatformHolder;

import java.util.List;

public class Ayaka implements Model {
    @Override
    public String getName() {
        return "Ayaka";
    }

@Override
public void showHelp(MessageSink sender) {
    sender.sendMessage("§b╔══════════════════════════════════╗");
    sender.sendMessage("§b║ §2§oLengbanlist 帮助 - 绫华风格 §b║");
    sender.sendMessage("§b╠══════════════════════════════════╣");
    sender.sendMessage("§6§l◆ 处罚管理");
    sender.sendMessage("§2✦ §b/lban add [可选]-s <必填>玩家名 <必填>天数/auto <必填>原因 §7- §3添加封禁，维护秩序不容破坏。");
    sender.sendMessage("§7  = §b/ban");
    sender.sendMessage("§2✦ §b/lban remove <必填>玩家名 §7- §3移除封禁，宽恕是美德。");
    sender.sendMessage("§7  = §b/unban");
    sender.sendMessage("§2✦ §b/ban-ip [可选]-s <必填>IP地址 <必填>天数/auto <必填>原因 §7- §3封禁 IP 地址，维护秩序。");
    sender.sendMessage("§2✦ §b/lban mute [可选]-s <必填>玩家名 <必填>时间/auto <必填>原因 §7- §3禁言玩家，维护秩序。");
    sender.sendMessage("§7  = §b/mute");
    sender.sendMessage("§2✦ §b/lban unmute [可选]-s <必填>玩家名 §7- §3解除禁言，给予机会重新开始。");
    sender.sendMessage("§7  = §b/unmute");
    sender.sendMessage("§2✦ §b/lban warn <必填>玩家名 <必填>原因 §7- §3警告玩家，三次警告自动封禁。");
    sender.sendMessage("§7  = §b/warn");
    sender.sendMessage("§2✦ §b/lban unwarn <必填>玩家名 §7- §3移除玩家警告");
    sender.sendMessage("§7  = §b/unwarn");
    sender.sendMessage("§2✦ §b/kick <必填>玩家名 [可选]原因 §7- §3踢出不守规矩的玩家！");
    sender.sendMessage("§2✦ §b/setban <必填>玩家名/IP <必填>时间/forever/auto <必填>原因 §7- §3修改封禁时间，优雅而公正。");
    sender.sendMessage("§6§l◆ 查询信息");
    sender.sendMessage("§2✦ §b/lban check <必填>玩家名/IP §7- §3检查封禁状态，优雅而公正。");
    sender.sendMessage("§2✦ §b/lban history <必填>玩家名 §7- §3查看处罚记录，请过目。");
    sender.sendMessage("§7  = §b/history");
    sender.sendMessage("§2✦ §b/report <必填>玩家名 <必填>原因 §7- §3优雅地举报不守规矩的行为。");
    sender.sendMessage("§2✦ §b/lban getip [可选]玩家名 §7- §3查询玩家 IP 地址，不填则查自己。");
    sender.sendMessage("§2✦ §b/lban alts <必填>玩家名 §7- §3查看同 IP 的关联账号。");
    sender.sendMessage("§6§l◆ 审计与回滚");
    sender.sendMessage("§2✦ §b/lban audit [可选]操作人 §7- §3查阅审计记录，请过目。");
    sender.sendMessage("§2✦ §b/lban audit export [可选]条数 §7- §3导出审计日志，归档备查。");
    sender.sendMessage("§2✦ §b/lban audit verify §7- §3校验审计完整性。");
    sender.sendMessage("§2✦ §b/lban sync §7- §3查看跨服同步状态。");
    sender.sendMessage("§2✦ §b/lban rollback <必填>操作人 <必填>开始时间 <必填>结束时间 [可选]操作类型 §7- §3回滚指定操作。");
    sender.sendMessage("§6§l◆ 杂项");
    sender.sendMessage("§2✦ §b/lban list §7- §3查看封禁名单，优雅而公正。");
    sender.sendMessage("§2✦ §b/lban list-mute §7- §3查看禁言列表");
    sender.sendMessage("§7  = §b/listmute");
    sender.sendMessage("§2✦ §b/lban a §7- §3广播封禁人数，让大家知晓规则。");
    sender.sendMessage("§2✦ §b/lban toggle §7- §3开关自动广播，一切尽在掌控。");
    sender.sendMessage("§2✦ §b/lban open §7- §3打开可视化操作界面");
    sender.sendMessage("§2✦ §b/lban model <必填>模型名称 §7- §3切换模型，体验不同的风格。");
    sender.sendMessage("§2✦ §b/lban reload §7- §3重新加载配置，确保一切完美无缺。");
    sender.sendMessage("§2✦ §b/lban info §7- §3查看插件信息");
    sender.sendMessage("§b╚══════════════════════════════════╝");
    sender.sendMessage("§2♡ 当前版本: " + PlatformHolder.get().getPluginVersion() + " §7| §b模型: 绫华 Ayaka");
}

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d绫华的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被绫华踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 绫华说：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b维护秩序，不容破坏！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b绫华说：§a自动广播已经 " + (enabled ? "开启。" : "关闭。") + " 一切尽在掌控之中。";
    }

    @Override
    public String reloadConfig() {
        return "§b绫华说：§a配置重新加载完成。确保一切完美无缺。";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b绫华说：§a" + player + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。维护秩序，不容破坏。";
    }

    @Override
    public String removeBan(String player) {
        return "§b绫华说：§a" + player + " 已从封禁名单中移除。宽恕是美德。";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b绫华说：§a" + player + " 已被禁言，原因是：" + reason + "。维护秩序，不容破坏。";
    }

    @Override
    public String removeMute(String player) {
        return "§b绫华说：§a" + player + " 的禁言已解除。给予机会，重新开始。";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b绫华说：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。维护秩序，不容破坏。";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b绫华说：§aIP " + ip + " 的封禁已解除。给予机会，重新开始。";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b绫华说：§a玩家 " + player + " 已被警告，原因是：" + reason + "。警告三次将被自动封禁。";
    }

    @Override
    public String removeWarn(String player) {
        return "§b绫华说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b绫华说：§a" + player + " 殿下的记录如白雪般纯净，绫华深感欣慰。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b绫华说：§a" + player + " 殿下的处罚记录如下，请过目：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b绫华说：§7望阁下以此为戒，优雅地遵守规则，方显贵族风范。");
        return sb.toString().trim();
    }

    public String onMuteCommandBlocked() {
        return "§b绫华说：§c禁言期间无法使用该命令，还请您静心等候。";
    }

    public String onWarnOffline(String player, String reason) {
        return "§b绫华说：§a" + player + " 此刻不在线，警告已代为记下，原因是：" + reason + "。待他归来时请多加留意。";
    }

    public String getPendingWarningsNotice(int count) {
        return "§b绫华说：§e您有 " + count + " 条待处理的警告，还望多加注意。";
    }

    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b绫华说：§e" + target + " 的" + type + "将于 " + remaining + " 后到期，请阁下留意处理。";
    }

    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b绫华说：§c" + player + " 已是第 " + offenseCount + " 次违规，系统已自动升级封禁至 " + duration + "。规矩面前，还请自律。";
    }

    public String getAltsResult(String player, int count) {
        return "§b绫华说：§e经查，" + player + " 名下共有 " + count + " 个相同 IP 的账号，请酌情处理。";
    }

    public String getNoAlts(String player) {
        return "§b绫华说：§a" + player + " 名下未发现其他账号，一身清白。";
    }

    public String onReportBan(String player, String duration) {
        return "§b绫华说：§a举报已确认，" + player + " 已被封禁 " + duration + "。感谢您为维护秩序所做的努力。";
    }

    public String getExportResult(int count) {
        return "§b绫华说：§a审计日志已成功导出，共 " + count + " 条记录，请过目。";
    }

    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b绫华说：§a审计完整性校验通过，" + count + " 条记录完好无损。";
        }
        return "§b绫华说：§c校验未通过，" + count + " 条记录疑似被篡改，还请您及时核查。";
    }

    public String getSyncStatus(String detail) {
        return "§b绫华说：§a跨服同步状态：§f" + detail;
    }

    public String getImmunityDenied(String target) {
        return "§b绫华说：§c" + target + " 身份尊贵，恐非我等可轻易责罚，还请三思。";
    }

    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§b绫华说：§e已清点完毕——操作人 " + actor + " 在 " + timeRange + " 有 " + matched + " 条操作可待回滚。";
    }

    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§b绫华说：§a回滚之事已办妥。匹配 " + matched + " 条，执行 " + executed + " 条，跳过 " + skipped + " 条。";
    }

    public String getRollbackNoRecords(String actor) {
        return "§b绫华说：§e" + actor + " 在那段时间并无需要回滚的记录。";
    }

}

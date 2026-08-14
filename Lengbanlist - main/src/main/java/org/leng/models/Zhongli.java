package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Zhongli implements Model {
    @Override
    public String getName() {
        return "Zhongli";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 钟离风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <可选>-s <必填>玩家名 <必填>天数 <必填>原因 §7- §3添加封禁，秩序不容破坏。");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <必填>玩家名 §7- §3移除封禁，宽恕是美德。");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <可选>-s <必填>IP地址 <必填>天数 <必填>原因 §7- §3封禁 IP 地址，维护秩序。");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <可选>-s <必填>玩家名 <必填>原因 §7- §3禁言玩家，维护秩序。");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <可选>-s <必填>玩家名 §7- §3解除禁言，宽恕是美德。");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <必填>玩家名 <必填>原因 §7- §3警告玩家，三次警告自动封禁。");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <必填>玩家名 §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <必填>玩家名 <可选>原因 §7- §3踢出破坏秩序的玩家！");
    Utils.sendMessage(sender, "§2✦ §b/setban <必填>玩家名/IP <必填>时间/forever/auto <必填>原因 §7- §3修改封禁时间，维护秩序。");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <必填>玩家名/IP §7- §3检查封禁状态，一切尽在掌控。");
    Utils.sendMessage(sender, "§2✦ §b/lban history <必填>玩家名 §7- §3查阅契约档案，凡违契者皆记录在案。");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <必填>玩家名 <必填>原因 §7- §3发现破坏秩序的行为？及时举报。");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <可选>玩家名 §7- §3查询玩家 IP 地址");
    Utils.sendMessage(sender, "§2✦ §b/lban audit <可选>操作人 §7- §3查看审计日志，一切尽在掌控。");
    Utils.sendMessage(sender, "§2✦ §b/lban alts <必填>玩家名 §7- §3查询同IP小号，凡有所属皆可查证。");
    Utils.sendMessage(sender, "§2✦ §b/lban audit export §7- §3导出审计日志，录入契约存档。");
    Utils.sendMessage(sender, "§2✦ §b/lban audit verify §7- §3校验审计完整性，契约不容篡改。");
    Utils.sendMessage(sender, "§2✦ §b/lban sync §7- §3查看跨服同步状态，诸服契约相通。");
    Utils.sendMessage(sender, "§2✦ §b/lban rollback <操作人> <开始时间> <结束时间> <可选>操作类型 §7- §3回滚操作，纠偏契约之误。");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看封禁名单，一切尽在掌控。");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数，维护秩序。");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，一切尽在掌控。");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面");
    Utils.sendMessage(sender, "§2✦ §b/lban model <必填>模型名称 §7- §3切换模型，体验不同的风格。");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，确保一切完美无缺。");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 钟离 Zhongli");
}
    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d钟离的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被钟离踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 钟离说：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b维护秩序，不容破坏！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b钟离说：§a自动广播已经 " + (enabled ? "开启。" : "关闭。") + " 秩序需要维护。";
    }

    @Override
    public String reloadConfig() {
        return "§b钟离说：§a配置重新加载完成。一切尽在掌控之中。";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b钟离说：§a玩家 " + player + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。秩序不容破坏。";
    }

    @Override
    public String removeBan(String player) {
        return "§b钟离说：§a玩家 " + player + " 已从封禁名单中移除。宽恕是美德。";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b钟离说：§a玩家 " + player + " 已被禁言，原因是：" + reason + "。秩序不容破坏。";
    }

    @Override
    public String removeMute(String player) {
        return "§b钟离说：§a玩家 " + player + " 的禁言已解除。宽恕是美德。";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b钟离说：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。秩序不容破坏。";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b钟离说：§aIP " + ip + " 的封禁已解除。宽恕是美德。";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b钟离说：§a玩家 " + player + " 已被警告，原因是：" + reason + "。警告三次将被自动封禁。";
    }

    @Override
    public String removeWarn(String player) {
        return "§b钟离说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b钟离说：§a契约记载中，" + player + " 未曾违背任何规则。此人值得信任。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b钟离说：§a契约之下，一切皆有记录。" + player + " 的处罚档案如下：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b钟离说：§7凡契约者，当以此为鉴。善守规则，方能长久。");
        return sb.toString().trim();
    }

    @Override
    public String onMuteCommandBlocked() {
        return "§b钟离说：§c禁言期间，不宜动用此令。安分守己，方得长久。";
    }

    @Override
    public String onWarnOffline(String player, String reason) {
        return "§b钟离说：§a" + player + " 虽不在场，其过已录于契约之上，原因是：" + reason + "。待其归来，自当知晓。";
    }

    @Override
    public String getPendingWarningsNotice(int count) {
        return "§b钟离说：§e契约有载，你尚有 " + count + " 条警告待处理。前车之鉴，后事之师，望好自为之。";
    }

    @Override
    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b钟离说：§e特此提醒：关于 " + target + " 的" + type + "，仅余 " + remaining + "。契约将满，届时可酌情处置。";
    }

    @Override
    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b钟离说：§e" + player + " 已是第 " + offenseCount + " 次违反规则，依契约之约，自动升级封禁 " + duration + "。事不过三，此乃天道。";
    }

    @Override
    public String getAltsResult(String player, int count) {
        return "§b钟离说：§a经查，" + player + " 名下共有 " + count + " 个同源账号。纸终究包不住火，天网恢恢，疏而不漏。";
    }

    @Override
    public String getNoAlts(String player) {
        return "§b钟离说：§a" + player + " 名下并无同源账号，清者自清，坦坦荡荡。";
    }

    @Override
    public String onReportBan(String player, String duration) {
        return "§b钟离说：§a举报业已确认，" + player + " 依律封禁 " + duration + "。法不容情，望诸位引以为戒。";
    }

    @Override
    public String getExportResult(int count) {
        return "§b钟离说：§a审计日志导出完毕，共 " + count + " 条，尽数封存于契约之中，以供查证。";
    }

    @Override
    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b钟离说：§a审计哈希链校验无误，共 " + count + " 条，一毫不差。契约之重，在于诚信。";
        }
        return "§b钟离说：§c审计日志竟遭篡改，共 " + count + " 条记录已现破绽！契约被毁，此乃大忌，务必彻查。";
    }

    @Override
    public String getSyncStatus(String detail) {
        return "§b钟离说：§e跨服同步状态：" + detail + "。诸服如诸国，契约相通，方能长治久安。";
    }

    @Override
    public String getImmunityDenied(String target) {
        return "§b钟离说：§c" + target + " 之位阶不在你之下，贸然出手有违契约之道。";
    }

    @Override
    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§b钟离说：§e契约之印已显——操作人 " + actor + " 在 " + timeRange + " 留有 " + matched + " 条可回滚之记录。";
    }

    @Override
    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§b钟离说：§a契约已成。回滚匹配 " + matched + " 条，执行 " + executed + " 条，跳过 " + skipped + " 条。";
    }

    @Override
    public String getRollbackNoRecords(String actor) {
        return "§b钟离说：§e" + actor + " 在那段时间并无应回滚之契约记录。";
    }
}

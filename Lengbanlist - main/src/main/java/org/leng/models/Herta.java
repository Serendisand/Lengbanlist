package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Herta implements Model {
    @Override
    public String getName() {
        return "Herta";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 黑塔风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <可选>-s <必填>玩家名 <必填>天数 <必填>原因 §7- §3添加封禁，正义不容挑战！");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <必填>玩家名 §7- §3移除封禁，给予机会重新开始。");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <可选>-s <必填>IP地址 <必填>天数 <必填>原因 §7- §3封禁 IP 地址，维护正义。");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <可选>-s <必填>玩家名 <必填>原因 §7- §3禁言玩家，维护正义。");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <可选>-s <必填>玩家名 §7- §3解除禁言，给予机会。");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <必填>玩家名 <必填>原因 §7- §3警告玩家，三次警告自动封禁！");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <必填>玩家名 §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <必填>玩家名 <可选>原因 §7- §3踢出不听话的家伙！");
    Utils.sendMessage(sender, "§2✦ §b/setban <必填>玩家名/IP <必填>时间/forever/auto <必填>原因 §7- §3修改封禁时间，调皮捣蛋可是要额外收费的~");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <必填>玩家名/IP §7- §3检查封禁状态，黑塔的正义不容挑战！");
    Utils.sendMessage(sender, "§2✦ §b/lban history <必填>玩家名 §7- §3翻翻黑历史，让黑塔瞧瞧~");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <必填>玩家名 <必填>原因 §7- §3维护正义，举报违规者！");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <可选>玩家名 §7- §3查询玩家 IP 地址，找出违规者。");
    Utils.sendMessage(sender, "§2✦ §b/lban audit <可选>操作人 §7- §3查看审计日志，黑塔的正义不容挑战！");
    Utils.sendMessage(sender, "§2✦ §b/lban alts <必填>玩家名 §7- §3查询同IP小号，无非是些无聊的复制品！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit export §7- §3导出审计日志，账本清清楚楚！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit verify §7- §3校验审计完整性，谁动了手脚都瞒不过我！");
    Utils.sendMessage(sender, "§2✦ §b/lban sync §7- §3查看跨服同步状态，一切尽在掌控！");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看封禁名单，黑塔的正义不容挑战！");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数，让违规者无处可逃！");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，掌控一切！");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面");
    Utils.sendMessage(sender, "§2✦ §b/lban model <必填>模型名称 §7- §3切换模型，体验不同的风格。");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，确保一切正常运行。");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 黑塔 Herta");
}

    @Override
    public String getKickMessage(String reason) {
        return "§5╔══════════════════════════╗\n" +
               "§5║   §d黑塔的驱逐通知  §5║\n" +
               "§5╠══════════════════════════╣\n" +
               "§d☠️ 你被黑塔踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d想回来记得找黑塔哦~\n" +
               "§5╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 黑塔说：§a" + playerName + " §e已被踢出！\n" +
               "§5原因: §f" + reason + "\n" +
               "§b调皮捣蛋可是要额外收费的~ §5(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b黑塔说：§a自动广播已经 " + (enabled ? "开启！" : "关闭！") + " 正义需要维护！";
    }

    @Override
    public String reloadConfig() {
        return "§b黑塔说：§a配置重新加载完成！一切正常运行。";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b黑塔说：§a玩家 " + player + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。正义不容挑战！";
    }

    @Override
    public String removeBan(String player) {
        return "§b黑塔说：§a玩家 " + player + " 已从封禁名单中移除。给予机会，重新开始。";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b黑塔说：§a玩家 " + player + " 已被禁言，原因是：" + reason + "。正义不容挑战！";
    }

    @Override
    public String removeMute(String player) {
        return "§b黑塔说：§a玩家 " + player + " 的禁言已解除，可以继续说话了。";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b黑塔说：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。正义不容挑战！";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b黑塔说：§aIP " + ip + " 的封禁已解除。给予机会，重新开始。";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b黑塔说：§a玩家 " + player + " 已被警告，原因是：" + reason + "。警告三次将被自动封禁！";
    }

    @Override
    public String removeWarn(String player) {
        return "§b黑塔说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b黑塔说：§a" + player + " 的档案干干净净~看来是个遵纪守法的好孩子呢！";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b黑塔说：§a让黑塔翻翻 ").append(player).append(" 的黑历史……哇哦，有点东西嘛！\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b黑塔说：§7调皮捣蛋可是要额外收费的~下次不许再犯哦！");
        return sb.toString().trim();
    }

    @Override
    public String onMuteCommandBlocked() {
        return "§b黑塔说：§c禁言期间不能使用该命令，这种蠢问题也要来打扰天才？";
    }

    @Override
    public String onWarnOffline(String player, String reason) {
        return "§b黑塔说：§a玩家 " + player + " 不在线，但警告已记录在案，原因是：" + reason + "。想逃出天才的视野？还早着呢。";
    }

    @Override
    public String getPendingWarningsNotice(int count) {
        return "§b黑塔说：§e你有 " + count + " 条待处理警告。好好反省，我可没耐心重复第二遍。";
    }

    @Override
    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b黑塔说：§e到期提醒：" + target + " 的" + type + "还剩 " + remaining + "。是否提前解除，你自己决定。";
    }

    @Override
    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b黑塔说：§e" + player + " 第 " + offenseCount + " 次违规，已自动升级封禁 " + duration + "。一犯再犯，真当我的实验台是游乐场？";
    }

    @Override
    public String getAltsResult(String player, int count) {
        return "§b黑塔说：§a" + player + " 名下查出 " + count + " 个同IP小号。无非是些无聊的复制品，怎么可能逃过我的眼睛？";
    }

    @Override
    public String getNoAlts(String player) {
        return "§b黑塔说：§a" + player + " 没有查出同IP小号。还算干净，勉强值得一句表扬。";
    }

    @Override
    public String onReportBan(String player, String duration) {
        return "§b黑塔说：§a举报已确认，" + player + " 已被封禁 " + duration + "。这种小事，交给天才一秒就够了。";
    }

    @Override
    public String getExportResult(int count) {
        return "§b黑塔说：§a审计日志导出完成，共 " + count + " 条。数据归档得井井有条，这才是天才该有的样子。";
    }

    @Override
    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b黑塔说：§a审计哈希链校验完整，共 " + count + " 条。没有异常，一切都在我的注视之下。";
        }
        return "§b黑塔说：§c审计日志被篡改了！校验 " + count + " 条数据即发现异常。敢在天才的眼皮底下动手脚？";
    }

    @Override
    public String getSyncStatus(String detail) {
        return "§b黑塔说：§e跨服同步状态：" + detail + "。所有数据都在我的掌控之中，同步绝不能出错。";
    }

    @Override
    public String getImmunityDenied(String target) {
        return "§b黑塔说：§c哼，" + target + " 的权限等级可不比你低，想动他？先掂量掂量自己够不够格。";
    }
}

package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Zero implements Model {
    @Override
    public String getName() {
        return "Zero";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 零风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <可选>-s <必填>玩家名 <必填>天数 <必填>原因 §7- §3添加封禁，零的秩序不容破坏！");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <必填>玩家名 §7- §3移除封禁，给予机会。");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <可选>-s <必填>IP地址 <必填>天数 <必填>原因 §7- §3封禁 IP 地址，维护秩序。");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <可选>-s <必填>玩家名 <必填>原因 §7- §3禁言玩家，维护秩序。");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <可选>-s <必填>玩家名 §7- §3解除禁言，给予机会。");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <必填>玩家名 <必填>原因 §7- §3警告玩家，三次警告自动封禁！");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <必填>玩家名 §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <必填>玩家名 <可选>原因 §7- §3踢出不守规矩的玩家！");
    Utils.sendMessage(sender, "§2✦ §b/setban <必填>玩家名/IP <必填>时间/forever/auto <必填>原因 §7- §3修改封禁时间，维护秩序。");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <必填>玩家名/IP §7- §3检查封禁状态，维护秩序。");
    Utils.sendMessage(sender, "§2✦ §b/lban history <必填>玩家名 §7- §3查询秩序档案，一切记录在案。");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <必填>玩家名 <必填>原因 §7- §3维护秩序，举报违规者。");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <可选>玩家名 §7- §3查询玩家 IP 地址，找出违规者。");
    Utils.sendMessage(sender, "§2✦ §b/lban alts <必填>玩家名 §7- §3查询同IP小号，彻查违规者。");
    Utils.sendMessage(sender, "§2✦ §b/lban audit <可选>操作人 §7- §3查看审计日志，维护秩序。");
    Utils.sendMessage(sender, "§2✦ §b/lban audit export §7- §3导出审计日志，一切记录在案。");
    Utils.sendMessage(sender, "§2✦ §b/lban audit verify §7- §3校验审计完整性，确保秩序不被篡改。");
    Utils.sendMessage(sender, "§2✦ §b/lban sync §7- §3查看跨服同步状态，掌控一切。");
    Utils.sendMessage(sender, "§2✦ §b/lban rollback <操作人> <开始时间> <结束时间> <可选>操作类型 §7- §3回滚操作，修复权限秩序。");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看封禁名单，零的秩序不容破坏！");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数，让违规者无所遁形！");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，掌控一切！");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面");
    Utils.sendMessage(sender, "§2✦ §b/lban model <必填>模型名称 §7- §3切换模型，体验不同的风格。");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，确保一切正常运行。");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 零 Zero");
}

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d零的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被零踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 零说：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b维护秩序，不容破坏！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b零说：§a自动广播已经 " + (enabled ? "开启！" : "关闭！") + " 秩序需要维护！";
    }

    @Override
    public String reloadConfig() {
        return "§b零说：§a配置重新加载完成！一切正常运行。";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b零说：§a玩家 " + player + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。秩序不容破坏！";
    }

    @Override
    public String removeBan(String player) {
        return "§b零说：§a玩家 " + player + " 已从封禁名单中移除。给予机会，重新开始。";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b零说：§a玩家 " + player + " 已被禁言，原因是：" + reason + "。秩序不容破坏！";
    }

    @Override
    public String removeMute(String player) {
        return "§b零说：§a玩家 " + player + " 的禁言已解除，可以继续说话了。";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b零说：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。秩序不容破坏！";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b零说：§aIP " + ip + " 的封禁已解除，给予机会，重新开始。";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b零说：§a玩家 " + player + " 已被警告，原因是：" + reason + "！警告三次将被自动封禁！";
    }

    @Override
    public String removeWarn(String player) {
        return "§b零说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b零说：§a秩序档案查询完毕，" + player + " 无任何违规记录。维护秩序，人人有责。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b零说：§a秩序档案——" + player + " 的违规记录：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b零说：§7秩序不容破坏，以上记录将永久存档。");
        return sb.toString().trim();
    }

    @Override
    public String onMuteCommandBlocked() {
        return "§b零说：§c禁言期间不得使用该命令。静思己过，遵守秩序。";
    }

    @Override
    public String onWarnOffline(String player, String reason) {
        return "§b零说：§a离线玩家 " + player + " 已被警告，原因是：" + reason + "。上线后将收到通知，一切记录在案。";
    }

    @Override
    public String getPendingWarningsNotice(int count) {
        return "§b零说：§e你有 " + count + " 条待处理警告。正视问题，方能维护秩序。";
    }

    @Override
    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b零说：§a" + target + " 的" + type + "将在 " + remaining + " 后解除，秩序回归。";
    }

    @Override
    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b零说：§a玩家 " + player + " 第 " + offenseCount + " 次违规，处罚已自动升级为封禁 " + duration + "。秩序不容破坏！";
    }

    @Override
    public String getAltsResult(String player, int count) {
        return "§b零说：§a玩家 " + player + " 名下查到 " + count + " 个小号，一切记录在案。";
    }

    @Override
    public String getNoAlts(String player) {
        return "§b零说：§a玩家 " + player + " 名下无任何小号，档案干净。";
    }

    @Override
    public String onReportBan(String player, String duration) {
        return "§b零说：§a举报已确认，玩家 " + player + " 已被封禁 " + duration + "。秩序不容破坏！";
    }

    @Override
    public String getExportResult(int count) {
        return "§b零说：§a审计日志已导出，共 " + count + " 条记录，一切记录在案。";
    }

    @Override
    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b零说：§a审计校验通过，" + count + " 条记录完整无损。";
        }
        return "§b零说：§c审计校验失败！检测到 " + count + " 条记录被篡改，必须彻查！";
    }

    @Override
    public String getSyncStatus(String detail) {
        return "§b零说：§a跨服同步状态：§f" + detail;
    }

    @Override
    public String getImmunityDenied(String target) {
        return "§b零说：§c目标 " + target + " 权限等级不低，权限秩序不可逾越！";
    }

    @Override
    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§b零说：§e权限审查通过——操作人 " + actor + " 在 " + timeRange + " 有 " + matched + " 条操作待回滚。";
    }

    @Override
    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§b零说：§a权限秩序已修复。回滚匹配 " + matched + " 条，执行 " + executed + " 条，跳过 " + skipped + " 条。";
    }

    @Override
    public String getRollbackNoRecords(String actor) {
        return "§b零说：§e" + actor + " 在该时段无违规操作记录。";
    }
}

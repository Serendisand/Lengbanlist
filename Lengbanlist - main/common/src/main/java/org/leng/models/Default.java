package org.leng.models;

import org.leng.platform.MessageSink;
import org.leng.platform.PlatformHolder;

import java.util.List;

public class Default implements Model {
    @Override
    public String getName() {
        return "Default";
    }

@Override
public void showHelp(MessageSink sender) {
    sender.sendMessage("§b╔══════════════════════════════════╗");
    sender.sendMessage("§b║ §2§oLengbanlist 帮助 - 默认风格 §b║");
    sender.sendMessage("§b╠══════════════════════════════════╣");
    sender.sendMessage("§6§l◆ 处罚管理");
    sender.sendMessage("§2✦ §b/lban add <玩家名> <天数> <原因> §7- §3添加封禁");
    sender.sendMessage("§7  = §b/ban");
    sender.sendMessage("§2✦ §b/lban remove <玩家名> §7- §3移除封禁");
    sender.sendMessage("§7  = §b/unban");
    sender.sendMessage("§2✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP 地址");
    sender.sendMessage("§2✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家");
    sender.sendMessage("§7  = §b/mute");
    sender.sendMessage("§2✦ §b/lban unmute <玩家名> §7- §3解除禁言");
    sender.sendMessage("§7  = §b/unmute");
    sender.sendMessage("§2✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次警告将自动封禁");
    sender.sendMessage("§7  = §b/warn");
    sender.sendMessage("§2✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告");
    sender.sendMessage("§7  = §b/unwarn");
    sender.sendMessage("§2✦ §b/kick <玩家名> <原因> §7- §3踢出玩家");
    sender.sendMessage("§2✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间");
    sender.sendMessage("§6§l◆ 查询信息");
    sender.sendMessage("§2✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态");
    sender.sendMessage("§2✦ §b/lban history <玩家名> §7- §3查询处罚历史");
    sender.sendMessage("§7  = §b/history");
    sender.sendMessage("§2✦ §b/report <玩家名> <原因> §7- §3举报玩家");
    sender.sendMessage("§2✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址");
    sender.sendMessage("§6§l◆ 杂项");
    sender.sendMessage("§2✦ §b/lban list §7- §3查看封禁名单");
    sender.sendMessage("§2✦ §b/lban list-mute §7- §3查看禁言列表");
    sender.sendMessage("§7  = §b/listmute");
    sender.sendMessage("§2✦ §b/lban a §7- §3广播封禁人数");
    sender.sendMessage("§2✦ §b/lban toggle §7- §3开关自动广播");
    sender.sendMessage("§2✦ §b/lban open §7- §3打开可视化操作界面");
    sender.sendMessage("§2✦ §b/lban model <模型名称> §7- §3切换模型");
    sender.sendMessage("§2✦ §b/lban reload §7- §3重新加载配置");
    sender.sendMessage("§2✦ §b/lban info §7- §3查看插件信息");
    sender.sendMessage("§b╚══════════════════════════════════╝");
    sender.sendMessage("§2♡ 当前版本: " + PlatformHolder.get().getPluginVersion() + " §7| §b模型: 默认 Default");
}

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d默认模型的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被默认模型踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 默认模型：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b维护秩序，不容破坏！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b默认模型：§a自动广播已经 " + (enabled ? "开启" : "关闭");
    }

    @Override
    public String reloadConfig() {
        return "§b默认模型：§a配置重新加载完成";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b默认模型：§a玩家 " + player + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason;
    }

    @Override
    public String removeBan(String player) {
        return "§b默认模型：§a玩家 " + player + " 已从封禁名单中移除";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b默认模型：§a玩家 " + player + " 已被禁言，原因是：" + reason;
    }

    @Override
    public String removeMute(String player) {
        return "§b默认模型：§a玩家 " + player + " 的禁言已解除";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b默认模型：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason;
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b默认模型：§aIP " + ip + " 的封禁已解除";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b默认模型：§a玩家 " + player + " 已被警告，原因是：" + reason + "。警告三次将被自动封禁。";
    }

    @Override
    public String removeWarn(String player) {
        return "§b默认模型：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b默认模型：§a玩家 " + player + " 没有任何处罚记录，是个遵纪守法的好玩家！";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b默认模型：§a玩家 ").append(player).append(" 的处罚历史：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        return sb.toString().trim();
    }

    public String onMuteCommandBlocked() {
        return "§b默认模型：§c禁言期间不能使用该命令哦~ 等禁言结束后再试试吧！";
    }

    public String onWarnOffline(String player, String reason) {
        return "§b默认模型：§a离线玩家 " + player + " 已被警告，原因是：" + reason + "。上线后他会收到通知哦~";
    }

    public String getPendingWarningsNotice(int count) {
        return "§b默认模型：§e你有 " + count + " 条待处理警告，记得遵守规则哦~";
    }

    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b默认模型：§a" + target + " 的" + type + "将在 " + remaining + " 后到期！";
    }

    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b默认模型：§a玩家 " + player + " 因第 " + offenseCount + " 次违规，处罚已自动升级为封禁 " + duration + "！";
    }

    public String getAltsResult(String player, int count) {
        return "§b默认模型：§a玩家 " + player + " 名下查到 " + count + " 个小号：";
    }

    public String getNoAlts(String player) {
        return "§b默认模型：§a玩家 " + player + " 没有查到任何小号，是干净的好玩家！";
    }

    public String onReportBan(String player, String duration) {
        return "§b默认模型：§a举报已确认！玩家 " + player + " 已被封禁 " + duration + "，维护秩序，不容破坏！§b(◕‿◕✿)";
    }

    public String getExportResult(int count) {
        return "§b默认模型：§a审计日志导出成功，共 " + count + " 条记录。";
    }

    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b默认模型：§a审计校验通过！" + count + " 条记录完整无损。";
        }
        return "§b默认模型：§c审计校验失败！检测到 " + count + " 条记录可能被篡改！";
    }

    public String getSyncStatus(String detail) {
        return "§b默认模型：§a跨服同步状态：§f" + detail;
    }

    public String getImmunityDenied(String target) {
        return "§b默认模型：§c目标 " + target + " 的权限等级不低于你，无法执行该操作！";
    }

    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§b默认模型：§e操作人 " + actor + " 在 " + timeRange + " 共有 " + matched + " 条可回滚操作记录。";
    }

    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§b默认模型：§a回滚完成！匹配 " + matched + " 条，成功执行 " + executed + " 条，跳过 " + skipped + " 条。";
    }

    public String getRollbackNoRecords(String actor) {
        return "§b默认模型：§e操作人 " + actor + " 在指定范围内没有可回滚的操作记录。";
    }

}

package org.leng.models;

import org.leng.platform.MessageSink;
import org.leng.platform.PlatformHolder;

import java.util.List;

public class Nahida implements Model {
    @Override
    public String getName() {
        return "Nahida";
    }

    @Override
    public void showHelp(MessageSink sender) {
        sender.sendMessage("§b╔══════════════════════════════════╗");
        sender.sendMessage("§b║ §2§oLengbanlist 帮助 - 纳西妲风格 §b║");
        sender.sendMessage("§b╠══════════════════════════════════╣");
        sender.sendMessage("§6§l◆ 处罚管理");
        sender.sendMessage("§2✦ §b/lban add <玩家名> <天数> <原因> §7- §3添加封禁，世界树会记下这次选择。");
        sender.sendMessage("§7  = §b/ban");
        sender.sendMessage("§2✦ §b/lban remove <玩家名> §7- §3移除封禁，愿新的枝芽重新生长。");
        sender.sendMessage("§7  = §b/unban");
        sender.sendMessage("§2✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP 地址，异常的信息流需要修剪。");
        sender.sendMessage("§2✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家，让思绪先安静下来吧。");
        sender.sendMessage("§7  = §b/mute");
        sender.sendMessage("§2✦ §b/lban unmute <玩家名> §7- §3解除禁言，愿他们说出更温柔的话语。");
        sender.sendMessage("§7  = §b/unmute");
        sender.sendMessage("§2✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次警告将触发自动封禁。");
        sender.sendMessage("§7  = §b/warn");
        sender.sendMessage("§2✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告，让记录回归平静。");
        sender.sendMessage("§7  = §b/unwarn");
        sender.sendMessage("§2✦ §b/kick <玩家名> <原因> §7- §3踢出玩家，让他们暂时离开梦境。");
        sender.sendMessage("§2✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间，重新校准规则的天平。");
        sender.sendMessage("§6§l◆ 查询信息");
        sender.sendMessage("§2✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态，读取世界树中的记录。");
        sender.sendMessage("§2✦ §b/lban history <玩家名> §7- §3查询处罚历史，看看记忆里留下了什么。");
        sender.sendMessage("§7  = §b/history");
        sender.sendMessage("§2✦ §b/report <玩家名> <原因> §7- §3举报玩家，把异常的梦告诉纳西妲吧。");
        sender.sendMessage("§2✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址，追寻信息的源头。");
        sender.sendMessage("§6§l◆ 杂项");
        sender.sendMessage("§2✦ §b/lban list §7- §3查看封禁名单，世界树的枝叶中藏着答案。");
        sender.sendMessage("§2✦ §b/lban list-mute §7- §3查看禁言列表，安静也是一种思考。");
        sender.sendMessage("§7  = §b/listmute");
        sender.sendMessage("§2✦ §b/lban a §7- §3广播封禁人数，让大家共同守护规则。");
        sender.sendMessage("§2✦ §b/lban toggle §7- §3开关自动广播，梦境的声音可以自由选择。");
        sender.sendMessage("§2✦ §b/lban open §7- §3打开可视化操作界面。");
        sender.sendMessage("§2✦ §b/lban model <模型名称> §7- §3切换模型，去看看不同的梦吧。");
        sender.sendMessage("§2✦ §b/lban reload §7- §3重新加载配置，让知识重新流动。");
        sender.sendMessage("§2✦ §b/lban info §7- §3查看插件信息。");
        sender.sendMessage("§b╚══════════════════════════════════╝");
        sender.sendMessage("§2♡ 当前版本: " + PlatformHolder.get().getPluginVersion() + " §7| §b模型: 纳西妲 Nahida");
    }

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d纳西妲的梦境提醒  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☘ 你暂时离开了这个梦境。\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d等你整理好思绪，再回来吧~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 纳西妲说：§a" + playerName + " §e已经暂时离开梦境。\n" +
               "§b原因: §f" + reason + "\n" +
               "§b愿这次提醒能让新的智慧发芽。§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b纳西妲说：§a自动广播已经 " + (enabled ? "开启。" : "关闭。") + " 梦境的声音已经调整好了。";
    }

    @Override
    public String reloadConfig() {
        return "§b纳西妲说：§a配置重新加载完成。知识的脉络又变得清晰了。";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b纳西妲说：§a" + player + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。世界树已经记录下这片异常的叶子。";
    }

    @Override
    public String removeBan(String player) {
        return "§b纳西妲说：§a" + player + " 已从封禁名单中移除。愿新的枝芽能向着阳光生长。";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b纳西妲说：§a" + player + " 已被禁言，原因是：" + reason + "。先让心中的杂音安静一会儿吧。";
    }

    @Override
    public String removeMute(String player) {
        return "§b纳西妲说：§a" + player + " 的禁言已解除。请用温柔的话语继续交流吧。";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b纳西妲说：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。异常的信息流已经被暂时阻断。";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b纳西妲说：§aIP " + ip + " 的封禁已解除。愿它不再带来混乱的梦。";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b纳西妲说：§a玩家 " + player + " 已被警告，原因是：" + reason + "。三次警告后，世界树会自动执行封禁。";
    }

    @Override
    public String removeWarn(String player) {
        return "§b纳西妲说：§a玩家 " + player + " 的警告记录已移除。愿这份空白能被更好的选择填满。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b纳西妲说：§a我查阅了世界树的记忆，" + player + " 没有任何处罚记录，是一片干净而温柔的叶子。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§b纳西妲说：§a我从世界树中找到了 ").append(player).append(" 的记忆片段：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b纳西妲说：§7记忆不会责备任何人，但它会提醒我们做出更好的选择。");
        return sb.toString().trim();
    }

    public String onMuteCommandBlocked() {
        return "§b纳西妲说：§c禁言期间还不能使用这个命令哦，等思绪平静下来再说吧。";
    }

    public String onWarnOffline(String player, String reason) {
        return "§b纳西妲说：§a" + player + " 此刻不在梦境中，警告已先记在世界树上，原因是：" + reason + "。等他回来时再慢慢告诉他吧。";
    }

    public String getPendingWarningsNotice(int count) {
        return "§b纳西妲说：§e你还有 " + count + " 条待处理的警告哦，我们一起去面对它们，好吗？";
    }

    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b纳西妲说：§e" + target + " 的" + type + "还有 " + remaining + " 就要到期了，记得抽空处理一下哦。";
    }

    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b纳西妲说：§c" + player + " 已是第 " + offenseCount + " 次触犯规则，世界树已自动将封禁升级为 " + duration + "。希望这次，他能学会与规则温柔相处。";
    }

    public String getAltsResult(String player, int count) {
        return "§b纳西妲说：§e我在世界树里数了数，" + player + " 名下共有 " + count + " 个相同 IP 的账号，像是同一片叶子的投影。";
    }

    public String getNoAlts(String player) {
        return "§b纳西妲说：§a我在世界树中没有找到 " + player + " 的其他账号，他是一棵独立生长的小树苗。";
    }

    public String onReportBan(String player, String duration) {
        return "§b纳西妲说：§a举报已经确认，" + player + " 已被封禁 " + duration + "。谢谢你守护了这片梦境。";
    }

    public String getExportResult(int count) {
        return "§b纳西妲说：§a审计日志已成功导出，共 " + count + " 条记录，都整理得清清楚楚啦。";
    }

    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b纳西妲说：§a校验通过，" + count + " 条记录与世界树的记忆完全一致，一切安好。";
        }
        return "§b纳西妲说：§c校验发现了问题，" + count + " 条记录与世界树的记忆对不上，像是被什么悄悄改动过。";
    }

    public String getSyncStatus(String detail) {
        return "§b纳西妲说：§a跨服同步状态：§f" + detail;
    }

    public String getImmunityDenied(String target) {
        return "§b纳西妲说：§c" + target + " 与你的位阶相当或更高，此刻出手并非明智之选。";
    }

    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§b纳西妲说：§e梦境之中，我看到操作人 " + actor + " 在 " + timeRange + " 有 " + matched + " 条操作可以回滚哦~";
    }

    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§b纳西妲说：§a梦境已修正。回滚匹配 " + matched + " 条，执行 " + executed + " 条，跳过 " + skipped + " 条~";
    }

    public String getRollbackNoRecords(String actor) {
        return "§b纳西妲说：§e" + actor + " 在那段时间的梦境很干净，没有可回滚的记录呢~";
    }

}

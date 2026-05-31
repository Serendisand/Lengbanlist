package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class Nahida implements Model {
    @Override
    public String getName() {
        return "Nahida";
    }

    @Override
    public void showHelp(CommandSender sender) {
        Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
        Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 纳西妲风格 §b║");
        Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
        Utils.sendMessage(sender, "§6§l◆ 处罚管理");
        Utils.sendMessage(sender, "§2✦ §b/lban add <玩家名> <天数> <原因> §7- §3添加封禁，世界树会记下这次选择。");
        Utils.sendMessage(sender, "§7  = §b/ban");
        Utils.sendMessage(sender, "§2✦ §b/lban remove <玩家名> §7- §3移除封禁，愿新的枝芽重新生长。");
        Utils.sendMessage(sender, "§7  = §b/unban");
        Utils.sendMessage(sender, "§2✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP 地址，异常的信息流需要修剪。");
        Utils.sendMessage(sender, "§2✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家，让思绪先安静下来吧。");
        Utils.sendMessage(sender, "§7  = §b/mute");
        Utils.sendMessage(sender, "§2✦ §b/lban unmute <玩家名> §7- §3解除禁言，愿他们说出更温柔的话语。");
        Utils.sendMessage(sender, "§7  = §b/unmute");
        Utils.sendMessage(sender, "§2✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次警告将触发自动封禁。");
        Utils.sendMessage(sender, "§7  = §b/warn");
        Utils.sendMessage(sender, "§2✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告，让记录回归平静。");
        Utils.sendMessage(sender, "§7  = §b/unwarn");
        Utils.sendMessage(sender, "§2✦ §b/kick <玩家名> <原因> §7- §3踢出玩家，让他们暂时离开梦境。");
        Utils.sendMessage(sender, "§2✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间，重新校准规则的天平。");
        Utils.sendMessage(sender, "§6§l◆ 查询信息");
        Utils.sendMessage(sender, "§2✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态，读取世界树中的记录。");
        Utils.sendMessage(sender, "§2✦ §b/lban history <玩家名> §7- §3查询处罚历史，看看记忆里留下了什么。");
        Utils.sendMessage(sender, "§7  = §b/history");
        Utils.sendMessage(sender, "§2✦ §b/report <玩家名> <原因> §7- §3举报玩家，把异常的梦告诉纳西妲吧。");
        Utils.sendMessage(sender, "§2✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址，追寻信息的源头。");
        Utils.sendMessage(sender, "§6§l◆ 杂项");
        Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看封禁名单，世界树的枝叶中藏着答案。");
        Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表，安静也是一种思考。");
        Utils.sendMessage(sender, "§7  = §b/listmute");
        Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数，让大家共同守护规则。");
        Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，梦境的声音可以自由选择。");
        Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面。");
        Utils.sendMessage(sender, "§2✦ §b/lban model <模型名称> §7- §3切换模型，去看看不同的梦吧。");
        Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，让知识重新流动。");
        Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息。");
        Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
        Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 纳西妲 Nahida");
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
}

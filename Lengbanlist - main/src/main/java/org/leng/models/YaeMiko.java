package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class YaeMiko implements Model {
    @Override
    public String getName() {
        return "YaeMiko";
    }

    @Override
    public void showHelp(CommandSender sender) {
        String[] lines = {
                "§d╔══════════════════════════════════╗",
                "§d║ §5§oLengbanlist 帮助 - 八重神子风格 §d║",
                "§d╠══════════════════════════════════╣",
                "§6§l◆ 处罚管理",
                "§5✦ §b/lban add <玩家名> <天数> <原因> §7- §3添加封禁，哎呀，又有有趣的家伙了呢。",
                "§7  = §b/ban",
                "§5✦ §b/lban remove <玩家名> §7- §3移除封禁，给他一点改过机会吧。",
                "§7  = §b/unban",
                "§5✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP，别以为换个壳就认不出来哦。",
                "§5✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家，让耳根清净一会儿。",
                "§7  = §b/mute",
                "§5✦ §b/lban unmute <玩家名> §7- §3解除禁言，希望他说点有趣的。",
                "§7  = §b/unmute",
                "§5✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次之后可就不好玩了。",
                "§7  = §b/warn",
                "§5✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告。",
                "§7  = §b/unwarn",
                "§5✦ §b/kick <玩家名> <原因> §7- §3踢出玩家，小小惩戒而已。",
                "§5✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间。",
                "§6§l◆ 查询信息",
                "§5✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态。",
                "§5✦ §b/lban history <玩家名> §7- §3翻翻旧账，看看有什么好故事。",
                "§7  = §b/history",
                "§5✦ §b/report <玩家名> <原因> §7- §3举报玩家，把趣事告诉神子吧。",
                "§5✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址。",
                "§6§l◆ 杂项",
                "§5✦ §b/lban list §7- §3查看封禁名单。",
                "§5✦ §b/lban list-mute §7- §3查看禁言列表。",
                "§7  = §b/listmute",
                "§5✦ §b/lban a §7- §3广播封禁人数。",
                "§5✦ §b/lban toggle §7- §3开关自动广播。",
                "§5✦ §b/lban open §7- §3打开可视化操作界面。",
                "§5✦ §b/lban model <模型名称> §7- §3切换模型。",
                "§5✦ §b/lban reload §7- §3重新加载配置。",
                "§5✦ §b/lban info §7- §3查看插件信息。",
                "§d╚══════════════════════════════════╝",
                "§d♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 八重神子 YaeMiko"
        };

        for (String line : lines) {
            Utils.sendMessage(sender, line);
        }
    }

    @Override
    public String getKickMessage(String reason) {
        return "§d╔══════════════════════════╗\n" +
               "§d║   §5八重神子的逐客令  §d║\n" +
               "§d╠══════════════════════════╣\n" +
               "§5🦊 哎呀，你被请出服务器了呢。\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§5下次可要乖一点，不然故事就更精彩了哦~\n" +
               "§d╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§d✧ 八重神子说：§a" + playerName + " §e已经被请出去了哦。\n" +
               "§5原因: §f" + reason + "\n" +
               "§d呵呵，这样的展开倒也不坏。";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§d八重神子说：§a自动广播已经" + (enabled ? "开启啦。" : "关闭啦。") + " 观众们的反应也很重要呢。";
    }

    @Override
    public String reloadConfig() {
        return "§d八重神子说：§a配置重新加载完成。嗯，故事又能继续写下去了。";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§d八重神子说：§a" + player + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。真是让人忍不住想写进轻小说呢。";
    }

    @Override
    public String removeBan(String player) {
        return "§d八重神子说：§a" + player + " 已从封禁名单中移除。接下来可别让我失望哦。";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§d八重神子说：§a" + player + " 已被禁言，原因是：" + reason + "。先安静一下，听我讲个故事吧。";
    }

    @Override
    public String removeMute(String player) {
        return "§d八重神子说：§a" + player + " 的禁言已解除。希望接下来的台词能有趣些。";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§d八重神子说：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "。小狐狸的眼睛可是很尖的哦。";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§d八重神子说：§aIP " + ip + " 的封禁已解除。";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§d八重神子说：§a玩家 " + player + " 已被警告，原因是：" + reason + "。再来两次，结局可就定下了哦。";
    }

    @Override
    public String removeWarn(String player) {
        return "§d八重神子说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§d八重神子说：§a" + player + " 的故事干干净净，暂时还没有能让我取材的内容呢。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("§d八重神子说：§a让我看看 ").append(player).append(" 留下了哪些有趣的篇章：\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§d八重神子说：§7呵呵，这些记录若写成小说，倒也挺有看头。");
        return sb.toString().trim();
    }
}

package org.leng.models;

import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.utils.Utils;

import java.util.List;

public class HuTao implements Model {
    @Override
    public String getName() {
        return "HuTao";
    }

@Override
public void showHelp(CommandSender sender) {
    Utils.sendMessage(sender, "§b╔══════════════════════════════════╗");
    Utils.sendMessage(sender, "§b║ §2§oLengbanlist 帮助 - 胡桃风格 §b║");
    Utils.sendMessage(sender, "§b╠══════════════════════════════════╣");
    Utils.sendMessage(sender, "§6§l◆ 处罚管理");
    Utils.sendMessage(sender, "§2✦ §b/lban add <可选>-s <必填>玩家名 <必填>天数 <必填>原因 §7- §3添加封禁，不守规矩就封了！");
    Utils.sendMessage(sender, "§7  = §b/ban");
    Utils.sendMessage(sender, "§2✦ §b/lban remove <必填>玩家名 §7- §3移除封禁，知错能改就放过他们吧！");
    Utils.sendMessage(sender, "§7  = §b/unban");
    Utils.sendMessage(sender, "§2✦ §b/ban-ip <可选>-s <必填>IP地址 <必填>天数 <必填>原因 §7- §3封禁 IP 地址，别再捣乱了！");
    Utils.sendMessage(sender, "§2✦ §b/lban mute <可选>-s <必填>玩家名 <必填>原因 §7- §3禁言玩家，让他们安静一会儿！");
    Utils.sendMessage(sender, "§7  = §b/mute");
    Utils.sendMessage(sender, "§2✦ §b/lban unmute <可选>-s <必填>玩家名 §7- §3解除禁言，让他们继续说话吧！");
    Utils.sendMessage(sender, "§7  = §b/unmute");
    Utils.sendMessage(sender, "§2✦ §b/lban warn <必填>玩家名 <必填>原因 §7- §3警告玩家，三次警告自动封禁！");
    Utils.sendMessage(sender, "§7  = §b/warn");
    Utils.sendMessage(sender, "§2✦ §b/lban unwarn <必填>玩家名 §7- §3移除玩家警告");
    Utils.sendMessage(sender, "§7  = §b/unwarn");
    Utils.sendMessage(sender, "§2✦ §b/kick <必填>玩家名 <可选>原因 §7- §3踢出捣乱的玩家！");
    Utils.sendMessage(sender, "§2✦ §b/setban <必填>玩家名/IP <必填>时间/forever/auto <必填>原因 §7- §3修改封禁时间，不守规矩就别怪胡桃无情！");
    Utils.sendMessage(sender, "§6§l◆ 查询信息");
    Utils.sendMessage(sender, "§2✦ §b/lban check <必填>玩家名/IP §7- §3检查封禁状态，看看谁在捣乱！");
    Utils.sendMessage(sender, "§2✦ §b/lban history <必填>玩家名 §7- §3翻翻案底，让胡桃瞧瞧！");
    Utils.sendMessage(sender, "§7  = §b/history");
    Utils.sendMessage(sender, "§2✦ §b/report <必填>玩家名 <必填>原因 §7- §3发现捣乱的家伙？快举报给胡桃！");
    Utils.sendMessage(sender, "§2✦ §b/lban getip <可选>玩家名 §7- §3查询玩家 IP 地址，看看谁在捣乱！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit <可选>操作人 §7- §3查看审计日志，看看谁在捣乱！");
    Utils.sendMessage(sender, "§2✦ §b/lban alts <必填>玩家名 §7- §3查询同IP小号，看谁在偷偷搞小动作！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit export §7- §3导出审计日志，胡桃的账本备份一份！");
    Utils.sendMessage(sender, "§2✦ §b/lban audit verify §7- §3校验审计完整性，看看有没有人动手脚！");
    Utils.sendMessage(sender, "§2✦ §b/lban sync §7- §3查看跨服同步状态，大家都同步了没有！");
    Utils.sendMessage(sender, "§6§l◆ 杂项");
    Utils.sendMessage(sender, "§2✦ §b/lban list §7- §3查看封禁名单，这些家伙真是麻烦！");
    Utils.sendMessage(sender, "§2✦ §b/lban list-mute §7- §3查看禁言列表，看看谁被胡桃禁言了！");
    Utils.sendMessage(sender, "§7  = §b/listmute");
    Utils.sendMessage(sender, "§2✦ §b/lban a §7- §3广播封禁人数，让大家都知道！");
    Utils.sendMessage(sender, "§2✦ §b/lban toggle §7- §3开关自动广播，想听就听不想听就关！");
    Utils.sendMessage(sender, "§2✦ §b/lban open §7- §3打开可视化操作界面！");
    Utils.sendMessage(sender, "§2✦ §b/lban model <必填>模型名称 §7- §3切换模型，试试别的风格吧！");
    Utils.sendMessage(sender, "§2✦ §b/lban reload §7- §3重新加载配置，说不定能发现新东西！");
    Utils.sendMessage(sender, "§2✦ §b/lban info §7- §3查看插件信息");
    Utils.sendMessage(sender, "§b╚══════════════════════════════════╝");
    Utils.sendMessage(sender, "§2♡ 当前版本: " + Lengbanlist.getInstance().getPluginVersion() + " §7| §b模型: 胡桃 HuTao");
}

    @Override
    public String getKickMessage(String reason) {
        return "§b╔══════════════════════════╗\n" +
               "§b║   §d胡桃的驱逐通知  §b║\n" +
               "§b╠══════════════════════════╣\n" +
               "§d☠️ 你被胡桃踢出服务器啦！\n\n" +
               "§7原因: §f" + reason + "\n\n" +
               "§d下次请遵守规则哦~\n" +
               "§b╚══════════════════════════╝";
    }

    @Override
    public String onKickSuccess(String playerName, String reason) {
        return "§b✧ 胡桃说：§a" + playerName + " §e已被踢出！\n" +
               "§b原因: §f" + reason + "\n" +
               "§b维护往生堂的和平！§b(◕‿◕✿)";
    }

    @Override
    public String toggleBroadcast(boolean enabled) {
        return "§b胡桃说：§a自动广播已经 " + (enabled ? "开启！" : "关闭！") + " 想听就听，不想听就关！";
    }

    @Override
    public String reloadConfig() {
        return "§b胡桃说：§a配置重新加载完成！说不定能发现新东西！";
    }

    @Override
    public String addBan(String player, int days, String reason) {
        return "§b胡桃说：§a" + player + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "！不守规矩，就别怪胡桃无情！";
    }

    @Override
    public String removeBan(String player) {
        return "§b胡桃说：§a" + player + " 已从封禁名单中移除。知错能改，就放过他们吧！";
    }

    @Override
    public String addMute(String player, String reason) {
        return "§b胡桃说：§a" + player + " 已被禁言，原因是：" + reason + "！让他们安静一会儿吧！";
    }

    @Override
    public String removeMute(String player) {
        return "§b胡桃说：§a" + player + " 的禁言已解除，可以继续说话了！";
    }

    @Override
    public String addBanIp(String ip, int days, String reason) {
        return "§b胡桃说：§aIP " + ip + " 已被封禁 " + Model.formatBanDays(days) + "，原因是：" + reason + "！别再捣乱了！";
    }

    @Override
    public String removeBanIp(String ip) {
        return "§b胡桃说：§aIP " + ip + " 的封禁已解除，放过他们吧！";
    }

    @Override
    public String addWarn(String player, String reason) {
        return "§b胡桃说：§a玩家 " + player + " 已被警告，原因是：" + reason + "！警告三次将被自动封禁！";
    }

    @Override
    public String removeWarn(String player) {
        return "§b胡桃说：§a玩家 " + player + " 的警告记录已移除。";
    }

    @Override
    public String getHistory(String player, List<String> entries) {
        if (entries.isEmpty()) {
            return "§b胡桃说：§a" + player + " 是个乖孩子，没有任何案底哦！往生堂给他点赞~";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("§b胡桃说：§a让胡桃翻翻 ").append(player).append(" 的案底……哎呀，还挺有故事的嘛！\n");
        for (String entry : entries) {
            sb.append(entry).append("\n");
        }
        sb.append("§b胡桃说：§7看完了吧？记得遵纪守法哦，不然往生堂随时欢迎~");
        return sb.toString().trim();
    }

    @Override
    public String onMuteCommandBlocked() {
        return "§b胡桃说：§c你还在禁言中哒！别想用命令，先安静待着吧~";
    }

    @Override
    public String onWarnOffline(String player, String reason) {
        return "§b胡桃说：§a" + player + " 不在线哒，不过警告已经记上啦，原因是：" + reason + "！等他上线再好好说道说道~";
    }

    @Override
    public String getPendingWarningsNotice(int count) {
        return "§b胡桃说：§e你有 " + count + " 条待处理警告哒！乖乖改正，不然往生堂要来找你玩咯~";
    }

    @Override
    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b胡桃说：§e到期提醒哒！" + target + " 的" + type + "还剩 " + remaining + "，要不要提前放人呢？";
    }

    @Override
    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b胡桃说：§e" + player + " 这是第 " + offenseCount + " 次违规哒，自动升级封禁 " + duration + "！三番五次，往生堂可要好好款待了~";
    }

    @Override
    public String getAltsResult(String player, int count) {
        return "§b胡桃说：§a查到了！" + player + " 有 " + count + " 个小号同住一个IP哒，一个个都逃不过胡桃的眼睛~";
    }

    @Override
    public String getNoAlts(String player) {
        return "§b胡桃说：§a" + player + " 没查出小号哒，是个老实人，往生堂给你点赞~";
    }

    @Override
    public String onReportBan(String player, String duration) {
        return "§b胡桃说：§a举报确认哒！" + player + " 已被封禁 " + duration + "！正义的铁拳从不迟到~";
    }

    @Override
    public String getExportResult(int count) {
        return "§b胡桃说：§a审计日志导出完成哒，一共 " + count + " 条！往生堂的账本记得明明白白~";
    }

    @Override
    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b胡桃说：§a审计哈希链校验完整哒，一共 " + count + " 条，一个都没动过~";
        }
        return "§b胡桃说：§c糟了糟了！审计日志被动手脚哒！校验 " + count + " 条就发现了破绽，这可不行！";
    }

    @Override
    public String getSyncStatus(String detail) {
        return "§b胡桃说：§e跨服同步状态：" + detail + "！各服务器都要乖乖听胡桃的指挥哒~";
    }

    @Override
    public String getImmunityDenied(String target) {
        return "§b胡桃说：§c这位客人 " + target + " 权限不小，客卿我也动不了手呢~";
    }
}

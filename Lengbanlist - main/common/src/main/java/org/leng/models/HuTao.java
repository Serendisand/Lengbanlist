package org.leng.models;

import org.leng.platform.MessageSink;
import org.leng.platform.PlatformHolder;

import java.util.List;

public class HuTao implements Model {
    @Override
    public String getName() {
        return "HuTao";
    }

@Override
public void showHelp(MessageSink sender) {
    sender.sendMessage("§b╔══════════════════════════════════╗");
    sender.sendMessage("§b║ §2§oLengbanlist 帮助 - 胡桃风格 §b║");
    sender.sendMessage("§b╠══════════════════════════════════╣");
    sender.sendMessage("§6§l◆ 处罚管理");
    sender.sendMessage("§2✦ §b/lban add <玩家名> <天数> <原因> §7- §3添加封禁，不守规矩就封了！");
    sender.sendMessage("§7  = §b/ban");
    sender.sendMessage("§2✦ §b/lban remove <玩家名> §7- §3移除封禁，知错能改就放过他们吧！");
    sender.sendMessage("§7  = §b/unban");
    sender.sendMessage("§2✦ §b/ban-ip <IP地址> <天数> <原因> §7- §3封禁 IP 地址，别再捣乱了！");
    sender.sendMessage("§2✦ §b/lban mute <玩家名> <原因> §7- §3禁言玩家，让他们安静一会儿！");
    sender.sendMessage("§7  = §b/mute");
    sender.sendMessage("§2✦ §b/lban unmute <玩家名> §7- §3解除禁言，让他们继续说话吧！");
    sender.sendMessage("§7  = §b/unmute");
    sender.sendMessage("§2✦ §b/lban warn <玩家名> <原因> §7- §3警告玩家，三次警告自动封禁！");
    sender.sendMessage("§7  = §b/warn");
    sender.sendMessage("§2✦ §b/lban unwarn <玩家名> §7- §3移除玩家警告");
    sender.sendMessage("§7  = §b/unwarn");
    sender.sendMessage("§2✦ §b/kick <玩家名> <原因> §7- §3踢出捣乱的玩家！");
    sender.sendMessage("§2✦ §b/setban <玩家名/IP> <时间/forever/auto> <原因> §7- §3修改封禁时间，不守规矩就别怪胡桃无情！");
    sender.sendMessage("§6§l◆ 查询信息");
    sender.sendMessage("§2✦ §b/lban check <玩家名/IP> §7- §3检查封禁状态，看看谁在捣乱！");
    sender.sendMessage("§2✦ §b/lban history <玩家名> §7- §3翻翻案底，让胡桃瞧瞧！");
    sender.sendMessage("§7  = §b/history");
    sender.sendMessage("§2✦ §b/report <玩家名> <原因> §7- §3发现捣乱的家伙？快举报给胡桃！");
    sender.sendMessage("§2✦ §b/lban getip <玩家名> §7- §3查询玩家 IP 地址，看看谁在捣乱！");
    sender.sendMessage("§6§l◆ 杂项");
    sender.sendMessage("§2✦ §b/lban list §7- §3查看封禁名单，这些家伙真是麻烦！");
    sender.sendMessage("§2✦ §b/lban list-mute §7- §3查看禁言列表，看看谁被胡桃禁言了！");
    sender.sendMessage("§7  = §b/listmute");
    sender.sendMessage("§2✦ §b/lban a §7- §3广播封禁人数，让大家都知道！");
    sender.sendMessage("§2✦ §b/lban toggle §7- §3开关自动广播，想听就听不想听就关！");
    sender.sendMessage("§2✦ §b/lban open §7- §3打开可视化操作界面！");
    sender.sendMessage("§2✦ §b/lban model <模型名称> §7- §3切换模型，试试别的风格吧！");
    sender.sendMessage("§2✦ §b/lban reload §7- §3重新加载配置，说不定能发现新东西！");
    sender.sendMessage("§2✦ §b/lban info §7- §3查看插件信息");
    sender.sendMessage("§b╚══════════════════════════════════╝");
    sender.sendMessage("§2♡ 当前版本: " + PlatformHolder.get().getPluginVersion() + " §7| §b模型: 胡桃 HuTao");
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

    public String onMuteCommandBlocked() {
        return "§b胡桃说：§c你还在禁言中哒！别想用命令，先安静待着吧~";
    }

    public String onWarnOffline(String player, String reason) {
        return "§b胡桃说：§a" + player + " 不在线哒，不过警告已经记上啦，原因是：" + reason + "！等他上线再好好说道说道~";
    }

    public String getPendingWarningsNotice(int count) {
        return "§b胡桃说：§e你有 " + count + " 条待处理警告哒！乖乖改正，不然往生堂要来找你玩咯~";
    }

    public String getExpiryReminder(String type, String target, String remaining) {
        return "§b胡桃说：§e到期提醒哒！" + target + " 的" + type + "还剩 " + remaining + "，要不要提前放人呢？";
    }

    public String onEscalatedBan(String player, int offenseCount, String duration) {
        return "§b胡桃说：§e" + player + " 这是第 " + offenseCount + " 次违规哒，自动升级封禁 " + duration + "！三番五次，往生堂可要好好款待了~";
    }

    public String getAltsResult(String player, int count) {
        return "§b胡桃说：§a查到了！" + player + " 有 " + count + " 个小号同住一个IP哒，一个个都逃不过胡桃的眼睛~";
    }

    public String getNoAlts(String player) {
        return "§b胡桃说：§a" + player + " 没查出小号哒，是个老实人，往生堂给你点赞~";
    }

    public String onReportBan(String player, String duration) {
        return "§b胡桃说：§a举报确认哒！" + player + " 已被封禁 " + duration + "！正义的铁拳从不迟到~";
    }

    public String getExportResult(int count) {
        return "§b胡桃说：§a审计日志导出完成哒，一共 " + count + " 条！往生堂的账本记得明明白白~";
    }

    public String getVerifyResult(boolean valid, int count) {
        if (valid) {
            return "§b胡桃说：§a审计哈希链校验完整哒，一共 " + count + " 条，一个都没动过~";
        }
        return "§b胡桃说：§c糟了糟了！审计日志被动手脚哒！校验 " + count + " 条就发现了破绽，这可不行！";
    }

    public String getSyncStatus(String detail) {
        return "§b胡桃说：§e跨服同步状态：" + detail + "！各服务器都要乖乖听胡桃的指挥哒~";
    }

    public String getImmunityDenied(String target) {
        return "§b胡桃说：§c这位客人 " + target + " 权限不小，客卿我也动不了手呢~";
    }

    public String getRollbackPreview(int matched, String actor, String timeRange) {
        return "§b胡桃说：§e操作人 " + actor + " 在 " + timeRange + " 共留下 " + matched + " 条可以回滚的记录，要动手吗？";
    }

    public String getRollbackResult(int matched, int executed, int skipped) {
        return "§b胡桃说：§a回滚好啦！匹配 " + matched + " 条，办成 " + executed + " 条，跳过 " + skipped + " 条~";
    }

    public String getRollbackNoRecords(String actor) {
        return "§b胡桃说：§e" + actor + " 那段时间什么坏事都没干，找不到可回滚的记录哦~";
    }

}

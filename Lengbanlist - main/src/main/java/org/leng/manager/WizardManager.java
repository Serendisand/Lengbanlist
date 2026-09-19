package org.leng.manager;

import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.leng.Lengbanlist;
import org.leng.utils.IpMatcher;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;

public class WizardManager {

    private static final String META_ACTION = "lengbanlist-action";
    private static final String META_STEP = "lengbanlist-step";
    private static final String META_TARGET = "lengbanlist-target";
    private static final String META_TIME = "lengbanlist-time";
    private static final String META_SILENT = "lengbanlist-silent";

    private static final String STEP_TARGET = "target";
    private static final String STEP_TIME = "time";
    private static final String STEP_REASON = "reason";

    private static final String CANCEL_HINT = "§e（输入 §fcancel §e取消）：";
    private static final String TIME_HINT = "§e请在聊天栏输入§f时间§e（如：10m, 1d, forever, auto）：";

    private final Lengbanlist plugin;

    public WizardManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public void start(Player player, String action) {
        switch (action) {
            case "ban":
                if (!Utils.canUse(plugin, player, "lengbanlist.ban", "ban")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                setMeta(player, META_STEP, STEP_TARGET);
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f玩家名或IP" + CANCEL_HINT);
                break;
            case "ipban":
                if (!Utils.canUse(plugin, player, "lengbanlist.banip", "ban-ip")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                setMeta(player, META_STEP, STEP_TARGET);
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f封禁的IP地址" + CANCEL_HINT);
                break;
            case "unban":
                if (!Utils.canUse(plugin, player, "lengbanlist.unban", "unban")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f解封的玩家名或IP" + CANCEL_HINT);
                break;
            case "mute":
                if (!Utils.canUse(plugin, player, "lengbanlist.mute", "mute")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                setMeta(player, META_STEP, STEP_TARGET);
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f禁言的玩家名" + CANCEL_HINT);
                break;
            case "unmute":
                if (!Utils.canUse(plugin, player, "lengbanlist.mute", "mute")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f解除禁言的玩家名" + CANCEL_HINT);
                break;
            case "warn":
                if (!Utils.canUse(plugin, player, "lengbanlist.warn", "warn")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                setMeta(player, META_STEP, STEP_TARGET);
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f警告的玩家名" + CANCEL_HINT);
                break;
            case "freeze":
                if (!Utils.canUse(plugin, player, "lengbanlist.freeze", "freeze")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                setMeta(player, META_STEP, STEP_TARGET);
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f冻结的玩家名" + CANCEL_HINT);
                break;
            case "unfreeze":
                if (!Utils.canUse(plugin, player, "lengbanlist.freeze", "freeze")) {
                    plugin.sendFeatureDisabled(player);
                    return;
                }
                Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入要§f解除冻结的玩家名或 all" + CANCEL_HINT);
                break;
            default:
                return;
        }
        setMeta(player, META_ACTION, action);
    }

    public boolean isRunning(Player player) {
        return player.hasMetadata(META_ACTION);
    }

    public void handle(Player player, String input) {
        if (!isRunning(player)) {
            return;
        }
        if (input.equalsIgnoreCase("cancel") || input.equals("取消")) {
            clear(player);
            Utils.sendMessage(player, plugin.prefix() + "§7已取消本次操作。");
            return;
        }

        String action = player.getMetadata(META_ACTION).get(0).asString();
        switch (action) {
            case "ban":
                if (check(player, "lengbanlist.ban", "ban")) {
                    handlePunish(player, input, "lban add", "封禁");
                }
                break;
            case "ipban":
                if (check(player, "lengbanlist.banip", "ban-ip") && validIpv4(player, input)) {
                    handlePunish(player, input, "lban add", "封禁");
                }
                break;
            case "mute":
                if (check(player, "lengbanlist.mute", "mute")) {
                    handlePunish(player, input, "lban mute", "禁言");
                }
                break;
            case "warn":
                if (check(player, "lengbanlist.warn", "warn")) {
                    handleSingleStep(player, input, "lban warn");
                }
                break;
            case "freeze":
                if (check(player, "lengbanlist.freeze", "freeze")) {
                    handleSingleStep(player, input, "lban freeze");
                }
                break;
            case "unban":
                if (check(player, "lengbanlist.unban", "unban")) {
                    clear(player);
                    player.performCommand("lban remove " + input);
                }
                break;
            case "unmute":
                if (check(player, "lengbanlist.mute", "mute")) {
                    clear(player);
                    player.performCommand("lban unmute " + input);
                }
                break;
            case "unfreeze":
                if (check(player, "lengbanlist.freeze", "freeze")) {
                    clear(player);
                    player.performCommand("lban unfreeze " + input);
                }
                break;
            default:
                clear(player);
                break;
        }
    }

    private void handlePunish(Player player, String input, String command, String label) {
        String step = stepOf(player);
        if (STEP_TARGET.equals(step)) {
            if (input.equalsIgnoreCase("-s")) {
                setMeta(player, META_SILENT, true);
                Utils.sendMessage(player, plugin.prefix() + "§e已开启静默模式，请输入要" + label + "的目标" + CANCEL_HINT);
                return;
            }
            setMeta(player, META_TARGET, input);
            setMeta(player, META_STEP, STEP_TIME);
            Utils.sendMessage(player, plugin.prefix() + TIME_HINT);
            return;
        }
        if (STEP_TIME.equals(step)) {
            if (!TimeUtils.isValidTime(input)) {
                Utils.sendMessage(player, plugin.prefix() + "§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
                return;
            }
            setMeta(player, META_TIME, input);
            setMeta(player, META_STEP, STEP_REASON);
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f原因§e：");
            return;
        }
        if (STEP_REASON.equals(step)) {
            String target = meta(player, META_TARGET, "");
            String time = meta(player, META_TIME, "");
            boolean silent = player.hasMetadata(META_SILENT);
            clear(player);
            player.performCommand(command + " " + (silent ? "-s " : "") + target + " " + time + " " + input);
        }
    }

    private void handleSingleStep(Player player, String input, String command) {
        String step = stepOf(player);
        if (STEP_TARGET.equals(step)) {
            setMeta(player, META_TARGET, input);
            setMeta(player, META_STEP, STEP_REASON);
            Utils.sendMessage(player, plugin.prefix() + "§e请在聊天栏输入§f原因§e：");
            return;
        }
        if (STEP_REASON.equals(step)) {
            String target = meta(player, META_TARGET, "");
            clear(player);
            player.performCommand(command + " " + target + " " + input);
        }
    }

    private boolean check(Player player, String permission, String feature) {
        if (Utils.canUse(plugin, player, permission, feature)) {
            return true;
        }
        Utils.sendMessage(player, plugin.prefix() + "§c不是你的工作喵！");
        clear(player);
        return false;
    }

    private boolean validIpv4(Player player, String input) {
        if (IpMatcher.isIpv4(input)) {
            return true;
        }
        Utils.sendMessage(player, plugin.prefix() + "§cIP格式无效喵，请输入合法的 IPv4 地址。");
        return false;
    }

    private String stepOf(Player player) {
        return meta(player, META_STEP, "");
    }

    private String meta(Player player, String key, String fallback) {
        return player.hasMetadata(key) && !player.getMetadata(key).isEmpty()
                ? player.getMetadata(key).get(0).asString()
                : fallback;
    }

    private void setMeta(Player player, String key, Object value) {
        player.setMetadata(key, new FixedMetadataValue(plugin, value));
    }

    public void clear(Player player) {
        player.removeMetadata(META_ACTION, plugin);
        player.removeMetadata(META_STEP, plugin);
        player.removeMetadata(META_TARGET, plugin);
        player.removeMetadata(META_TIME, plugin);
        player.removeMetadata(META_SILENT, plugin);
    }
}

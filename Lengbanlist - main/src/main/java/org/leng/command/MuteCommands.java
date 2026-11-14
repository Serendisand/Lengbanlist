package org.leng.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.leng.Lengbanlist;
import org.leng.integration.ModelManager;
import org.leng.listener.ChatListener;
import org.leng.models.Model;
import org.leng.object.MuteEntry;
import org.leng.util.IpMatcher;
import org.leng.util.Utils;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.leng.util.TimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class MuteCommands {

    private MuteCommands() {}

    public static final class Mute implements CommandExecutor {
        private final Lengbanlist plugin;

        public Mute(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!plugin.isFeatureEnabled("mute")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }
            if (!sender.hasPermission("lengbanlist.mute")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return true;
            }
            CommandSupport.Args parsed = CommandSupport.parseSilent(args);
            boolean silent = parsed.silent();
            args = parsed.values();

            if (args.length < 3) {
                sendUsage(sender, label);
                return true;
            }
            String target = args[0];
            String timeArg = args[1];
            String rawReason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            String reason = CommandSupport.presetReason(plugin, rawReason);
            if (IpMatcher.normalizeIpOrCidr(target) == null && !plugin.getPunishmentGate().canPunish(sender, target)) {
                Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(target));
                return true;
            }
            String normalized = IpMatcher.normalizeIpOrCidr(target);
            if (normalized != null) target = normalized;
            long duration;
            if (timeArg.equalsIgnoreCase("auto")) {
                duration = plugin.getDurationPolicy().autoMute(target).durationMillis();
            } else {
                duration = TimeUtils.parseDurationToMillis(timeArg);
                if (duration <= 0) {
                    CommandSupport.sendTimeFormat(sender, plugin.prefix(), "禁言");
                    return true;
                }
            }
            MuteEntry entry = new MuteEntry(target, Utils.getSenderName(sender), TimeUtils.calculateEndTime(duration), reason);
            Long newDuration = plugin.getMuteManager().mutePlayer(entry);
            if (newDuration == null) {
                Utils.sendMessage(sender, plugin.prefix() + "§e该目标已有相同时长的禁言记录，未重复禁言。");
                return true;
            }
            String muteMessage = currentModel().addMute(target, reason);
            if (silent) {
                Utils.sendMessage(sender, muteMessage);
            } else {
                Utils.broadcast(muteMessage);
            }
            return true;
        }

        private Model currentModel() {
            return ModelManager.getInstance().getCurrentModel();
        }

        private void sendUsage(CommandSender sender, String label) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /" + label + " <玩家> <时间/auto> <原因>");
            Utils.sendMessage(sender, plugin.prefix() + "§c时间单位喵: s(秒), m(分), h(时), d(天), w(周), M(月), y(年)");
            Utils.sendMessage(sender, plugin.prefix() + "§c使用 auto 会根据警告次数自动计算禁言时间喵");
            Utils.sendMessage(sender, plugin.prefix() + "§7在第一位加上 -s 可静默执行（不向全服广播）喵");
        }
    }

    public static final class Unmute implements CommandExecutor {
        private final Lengbanlist plugin;

        public Unmute(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!plugin.isFeatureEnabled("mute")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }
            if (!sender.hasPermission("lengbanlist.mute")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return true;
            }
            CommandSupport.Args parsed = CommandSupport.parseSilent(args);
            boolean silent = parsed.silent();
            args = parsed.values();
            if (args.length < 1) {
                Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /" + label + " <玩家名>");
                return true;
            }
            String normalized = IpMatcher.normalizeIpOrCidr(args[0]);
            if (normalized != null) args[0] = normalized;

            boolean removed = plugin.getMuteManager().unmutePlayerIfMuted(args[0], Utils.getSenderName(sender));
            if (!removed) {
                Utils.sendMessage(sender, plugin.prefix() + "§e玩家 " + args[0] + " 当前并未被禁言");
                return true;
            }
            String message = ModelManager.getInstance().getCurrentModel().removeMute(args[0]);
            if (silent) {
                Utils.sendMessage(sender, message);
            } else {
                Utils.broadcast(message);
            }
            return true;
        }
    }

    public static final class ListMute implements CommandExecutor {
        private final Lengbanlist plugin;

        public ListMute(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!plugin.isFeatureEnabled("mute")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }
            if (!sender.hasPermission("lengbanlist.listmute")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return true;
            }
            Utils.sendMessage(sender, "§7--§bLengbanlist 禁言名单§7--");
            Map<String, Long> muteStarts = plugin.getDatabaseManager().getActiveStartTimes("mutes");
            for (MuteEntry entry : plugin.getMuteManager().getMuteList()) {
                Long start = muteStarts.get(entry.getTarget().toLowerCase(Locale.ROOT));
                Utils.sendMessage(sender, "§c被禁言者：§f" + entry.getTarget() + " §e处理人：§f" + entry.getStaff() + " §e禁言原因：§f" + entry.getReason() + " §f禁言时长：§e" + TimeUtils.formatIssuedDuration(start == null ? 0L : start, entry.getTime()) + " §f解禁时间：§b" + TimeUtils.timestampToReadable(entry.getTime()));
            }
            return true;
        }
    }

    public static final class AllowMsg implements CommandExecutor {
        private final Lengbanlist plugin;

        public AllowMsg(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!plugin.isFeatureEnabled("chat-filter")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }


            if (!sender.hasPermission("lengbanlist.allowmsg")) {
                sender.sendMessage(plugin.prefix() + "§c只有管理员可以使用此命令。");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(plugin.prefix() + "§c用法错误喵: /allowmsg <玩家名>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.prefix() + "§c玩家 " + args[0] + " 不在线或不存在。");
                return true;
            }


            ChatListener chatListener = plugin.getChatListener();
            String pending = chatListener == null ? null : chatListener.takePendingChat(target.getName());
            if (pending == null) {
                sender.sendMessage(plugin.prefix() + "§e玩家 " + target.getName()
                        + " 没有待放行的消息（可能已过期或已被处理）。");
                return true;
            }

            // 原消息被聊天过滤拦下了，管理员判定"正常"后要真正把它放出去
            Utils.broadcast("§f<" + target.getName() + "> §f" + pending);
            target.sendMessage(plugin.prefix() + "§a管理员已放行你刚才的消息。");
            sender.sendMessage(plugin.prefix() + "§a已放行并广播玩家 " + target.getName() + " 的消息。");
            return true;
        }
    }
}

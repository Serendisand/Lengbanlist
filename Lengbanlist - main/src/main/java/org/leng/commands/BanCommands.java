package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.BanManager;
import org.leng.manager.BanMutationFeedback;
import org.leng.manager.EscalationManager.EscalationResult;
import org.leng.object.BanEntry;
import org.leng.utils.TimeUtils;
import org.leng.utils.Utils;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.leng.utils.IpMatcher;
import org.leng.object.BanIpEntry;
import org.leng.object.PlayerIdentity;
import org.leng.models.Model;
import org.leng.utils.SchedulerUtils;

public final class BanCommands {

    private BanCommands() {}

    public static final class Ban implements CommandExecutor, TabCompleter {

        private final Lengbanlist plugin;

        public Ban(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!plugin.isFeatureEnabled("ban")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }


            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!sender.isOp() && !player.hasPermission("lengbanlist.ban")) {
                    Utils.sendMessage(sender, "§c你没有权限使用此命令。");
                    return false;
                }
            }


            boolean silent = false;
            if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
                silent = true;
                args = Arrays.copyOfRange(args, 1, args.length);
            }

            if (args.length < 3) {
                sendUsage(sender);
                return false;
            }

            String target = args[0];
            String timeArg = args[1];
            String rawReason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            String reason = resolvePresetReason(rawReason);


            if (!plugin.getImmunityManager().canPunish(sender, target)) {
                Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(target));
                return false;
            }

            if (plugin.getBanManager().isPlayerBanned(target)) {
                Utils.sendMessage(sender, "§c玩家 " + target + " 已经被封禁");
                return false;
            }

            long banDuration;
            boolean isAuto = false;
            EscalationResult escalationResult = null;

            if (timeArg.equalsIgnoreCase("auto")) {
                isAuto = true;
                escalationResult = plugin.getEscalationManager().resolveBan(target);
                banDuration = escalationResult.durationMillis;
            } else {
                banDuration = TimeUtils.parseDurationToMillis(timeArg);
                if (banDuration <= 0) {
                    showTimeFormatError(sender);
                    return false;
                }

            }

            long banEndTime = TimeUtils.calculateEndTime(banDuration);

            BanEntry entry = new BanEntry(
                    target,
                    Utils.getSenderName(sender),
                    banEndTime,
                    reason,
                    isAuto
            );

            BanManager.BanMutationResult result = plugin.getBanManager().tryBanPlayer(entry, silent);
            if (!result.isApplied()) {
                BanMutationFeedback.sendFailure(sender, result, target, false);
                return true;
            }

            if (escalationResult != null && escalationResult.offenseCount > 0) {
                Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().onEscalatedBan(
                        target, escalationResult.offenseCount, TimeUtils.formatDuration(banDuration, TimeUtils.isEnglishLocale())));
            }
            return true;
        }

        private void sendUsage(CommandSender sender) {
            Utils.sendMessage(sender, "§c用法错误喵: /ban <玩家> <时间/auto> <原因>");
            Utils.sendMessage(sender, "§c时间单位喵: s(秒), m(分), h(时), d(天), w(周), M(月), y(年)");
            Utils.sendMessage(sender, "§c使用 auto 自动计算封禁时间喵（基于警告次数）");
            Utils.sendMessage(sender, "§7在第一位加上 -s 可静默执行（不向全服广播）喵");
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            int offset = (args.length > 0 && args[0].equalsIgnoreCase("-s")) ? 1 : 0;
            if (args.length - offset == 1) {
                String prefix = args[offset].toLowerCase();
                List<String> completions = new ArrayList<>();
                if (offset == 0 && "-s".startsWith(prefix)) completions.add("-s");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
                }
                return completions;
            }
            if (args.length - offset == 3) {
                String prefix = args[offset + 2].toLowerCase();
                List<String> presets = plugin.getConfig().getStringList("preset-reasons");

                if (presets.isEmpty() && plugin.getConfig().isConfigurationSection("preset-reasons")) {
                    presets = new ArrayList<>(plugin.getConfig().getConfigurationSection("preset-reasons").getKeys(false));
                }
                List<String> completions = new ArrayList<>();
                for (String key : presets) {
                    if (key.toLowerCase().startsWith(prefix)) completions.add(key);
                }
                return completions;
            }
            return null;
        }

        private void showTimeFormatError(CommandSender sender) {
            Utils.sendMessage(sender, "§c时间格式错误喵，请使用以下格式:");
            Utils.sendMessage(sender, "§c - 10s: 秒 (10 秒)");
            Utils.sendMessage(sender, "§c - 5m: 分钟 (5 分钟)");
            Utils.sendMessage(sender, "§c - 2h: 小时 (2 小时)");
            Utils.sendMessage(sender, "§c - 7d: 天 (7 天)");
            Utils.sendMessage(sender, "§c - 1w: 周 (1 周，等于 7 天)");
            Utils.sendMessage(sender, "§c - 1M: 月 (1 月，按 30 天计算)");
            Utils.sendMessage(sender, "§c - 1y: 年 (1 年，按 365 天计算)");
            Utils.sendMessage(sender, "§c - forever: 永久封禁");
            Utils.sendMessage(sender, "§c - auto: 自动计算封禁时间");
        }

        private String resolvePresetReason(String input) {
            if (input == null || !plugin.getConfig().isConfigurationSection("preset-reasons")) return input;
            String value = plugin.getConfig().getString("preset-reasons." + input.toLowerCase());
            return value != null ? value : input;
        }

    }

    public static final class BanIp implements CommandExecutor, TabCompleter {

        private final Lengbanlist plugin;

        public BanIp(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        public boolean execute(CommandSender sender, String s, String[] args) {
            if (!plugin.isFeatureEnabled("ban-ip")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }

            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!sender.isOp() && !player.hasPermission("lengbanlist.banip")) {
                    Utils.sendMessage(sender, "§c你没有权限使用此命令。");
                    return false;
                }
            }

            boolean silent = false;
            if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
                silent = true;
                args = Arrays.copyOfRange(args, 1, args.length);
            }

            if (args.length < 3) {
                Utils.sendMessage(sender, "§c用法错误喵: /ban-ip <IP> <时间/auto> <原因>");
                Utils.sendMessage(sender, "§c时间单位喵: s(秒), m(分), h(时), d(天), w(周), M(月), y(年)");
                Utils.sendMessage(sender, "§c使用 auto 自动计算封禁时间喵");
                Utils.sendMessage(sender, "§7在第一位加上 -s 可静默执行（不向全服广播）喵");
                return false;
            }

            if (!isValidIp(args[0])) {
                Utils.sendMessage(sender, "§c无效的IP地址或不允许封禁此IP");
                return false;
            }

            if (!plugin.getImmunityManager().canPunishTarget(plugin.getImmunityManager().getStaffWeight(sender), args[0])) {
                Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(args[0]));
                return false;
            }

            if (plugin.getBanManager().isIpBanned(args[0])) {
                Utils.sendMessage(sender, "§cIP " + args[0] + " 已经被封禁");
                return false;
            }

            boolean isAuto = args[1].equalsIgnoreCase("auto");
            long banDuration;
            EscalationResult escalationResult = null;

            if (isAuto) {
                escalationResult = plugin.getEscalationManager().resolveIpBan(args[0]);
                banDuration = escalationResult.durationMillis;
            } else {
                banDuration = TimeUtils.parseDurationToMillis(args[1]);
                if (banDuration <= 0) {
                    showTimeFormatError(sender);
                    return false;
                }
            }

            long banEndTime = TimeUtils.calculateEndTime(banDuration);
            String rawReason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            String reason = resolvePresetReason(rawReason);

            BanManager.BanMutationResult result = plugin.getBanManager().tryBanIp(
                    new org.leng.object.BanIpEntry(args[0], Utils.getSenderName(sender), banEndTime, reason, isAuto),
                    silent
            );
            if (!result.isApplied()) {
                BanMutationFeedback.sendFailure(sender, result, args[0], true);
                return true;
            }

            if (escalationResult != null && escalationResult.offenseCount > 0) {
                Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().onEscalatedBan(
                        args[0], escalationResult.offenseCount, TimeUtils.formatDuration(banDuration, TimeUtils.isEnglishLocale())));
            }
            return true;
        }

        private boolean isValidIp(String ip) {
            return IpMatcher.isValidIpOrCidrOrWildcard(ip);
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            int offset = (args.length > 0 && args[0].equalsIgnoreCase("-s")) ? 1 : 0;
            if (args.length - offset == 1) {
                List<String> completions = new ArrayList<>();
                if (offset == 0 && "-s".startsWith(args[0].toLowerCase())) completions.add("-s");
                return completions;
            }
            if (args.length - offset == 3) {
                String prefix = args[offset + 2].toLowerCase();
                List<String> presets = new ArrayList<>();
                if (plugin.getConfig().isConfigurationSection("preset-reasons")) {
                    presets.addAll(plugin.getConfig().getConfigurationSection("preset-reasons").getKeys(false));
                }
                List<String> completions = new ArrayList<>();
                for (String key : presets) {
                    if (key.toLowerCase().startsWith(prefix)) completions.add(key);
                }
                return completions;
            }
            return null;
        }

        private void showTimeFormatError(CommandSender sender) {
            Utils.sendMessage(sender, "§c时间格式错误喵，请使用以下格式:");
            Utils.sendMessage(sender, "§c - 10s: 秒 (10 秒)");
            Utils.sendMessage(sender, "§c - 5m: 分钟 (5 分钟)");
            Utils.sendMessage(sender, "§c - 2h: 小时 (2 小时)");
            Utils.sendMessage(sender, "§c - 7d: 天 (7 天)");
            Utils.sendMessage(sender, "§c - 1w: 周 (1 周，等于 7 天)");
            Utils.sendMessage(sender, "§c - 1M: 月 (1 月，按 30 天计算)");
            Utils.sendMessage(sender, "§c - 1y: 年 (1 年，按 365 天计算)");
            Utils.sendMessage(sender, "§c - auto: 自动计算封禁时间");
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            return execute(sender, label, args);
        }

        private String resolvePresetReason(String input) {
            if (input == null || !plugin.getConfig().isConfigurationSection("preset-reasons")) return input;
            String value = plugin.getConfig().getString("preset-reasons." + input.toLowerCase());
            return value != null ? value : input;
        }

    }

    public static final class Unban implements CommandExecutor {

        private final Lengbanlist plugin;

        public Unban(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        public boolean execute(CommandSender sender, String s, String[] args) {
            if (!plugin.isFeatureEnabled("unban")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }


            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!sender.isOp() && !(player.hasPermission("lengbanlist.unban"))) {
                    Utils.sendMessage(sender, "§c你没有权限使用此命令。");
                    return false;
                }
            }


            boolean silent = false;
            if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
                silent = true;
                args = java.util.Arrays.copyOfRange(args, 1, args.length);
            }

            if (args.length < 1) {
                Utils.sendMessage(sender, "§c用法错误喵: /unban [-s] <玩家名/IP>");
                return false;
            }

            if (args[0].contains(".")) {
                String normalized = IpMatcher.normalizeIpOrCidr(args[0]);
                if (normalized == null) {
                    Utils.sendMessage(sender, "§c无效的IP地址");
                    return false;
                }
                BanManager.BanMutationResult result = plugin.getBanManager()
                        .tryUnbanIp(normalized, Utils.getSenderName(sender), silent);
                if (!result.isApplied()) {
                    BanMutationFeedback.sendFailure(sender, result, normalized, true);
                }
            } else {
                BanManager.BanMutationResult result = plugin.getBanManager()
                        .tryUnbanPlayer(args[0], Utils.getSenderName(sender), silent);
                if (!result.isApplied()) {
                    BanMutationFeedback.sendFailure(sender, result, args[0], false);
                }
            }
            return true;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            return execute(sender, label, args);
        }

    }

    public static final class SetBan implements CommandExecutor, TabCompleter {

        private final Lengbanlist plugin;

        public SetBan(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!plugin.isFeatureEnabled("setban")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }


            if (!(sender instanceof Player) || !sender.isOp()) {
                if (!sender.hasPermission("lengbanlist.setban")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                    return true;
                }
            }


            if (args.length < 3) {
                sendUsage(sender);
                return true;
            }


            String target = args[0];
            String timeArg = args[1];
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

            BanManager banManager = plugin.getBanManager();


            boolean isIp = banManager.isValidIpOrCidr(target);


            if (!isIp && !plugin.getImmunityManager().canPunish(sender, target)) {
                Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(target));
                return true;
            }

            if (!isIp) {
                boolean banned = banManager.isPlayerBanned(target) || banManager.isIpBanned(target);
                if (!banned) {
                    PlayerIdentity identity = plugin.getIdentityResolver().resolve(target);
                    banned = identity.hasUuid() && banManager.isPlayerBannedByUuid(identity.uuid());
                }
                if (!banned) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c目标 " + target + " 未被封禁，无法设置封禁时间。");
                    return true;
                }
            }


            long banDuration;
            boolean isAuto = false;
            EscalationResult escalationResult = null;

            if (timeArg.equalsIgnoreCase("forever")) {
                banDuration = Long.MAX_VALUE;
            } else if (timeArg.equalsIgnoreCase("auto")) {
                isAuto = true;
                escalationResult = isIp ? plugin.getEscalationManager().resolveIpBan(target)
                        : plugin.getEscalationManager().resolveBan(target);
                banDuration = escalationResult.durationMillis;
            } else {
                banDuration = TimeUtils.parseDurationToMillis(timeArg);
                if (banDuration <= 0) {
                    showTimeFormatError(sender);
                    return true;
                }
            }


            BanManager.BanMutationResult result;
            if (isIp) {

                BanIpEntry existingBanIp = banManager.getBanIpEntry(target);
                if (existingBanIp == null) {
                    Utils.sendMessage(sender, plugin.prefix() + "§cIP " + target + " 未被封禁，无法设置封禁时间。");
                    return true;
                }
                BanIpEntry updatedIp = existingBanIp.withEndTime(TimeUtils.calculateEndTime(banDuration))
                        .withReason(reason)
                        .withAuto(isAuto);
                result = banManager.tryUpdateIpBan(updatedIp);
            } else {

                BanEntry existingBan = banManager.getBanEntry(target);
                if (existingBan == null) {
                    PlayerIdentity identity = plugin.getIdentityResolver().resolve(target);
                    if (identity.hasUuid()) {
                        existingBan = banManager.getBanEntryByUuid(identity.uuid());
                    }
                }
                if (existingBan == null) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c玩家 " + target + " 未被封禁，无法设置封禁时间。");
                    return true;
                }
                BanEntry updatedBan = existingBan.withEndTime(TimeUtils.calculateEndTime(banDuration))
                        .withReason(reason)
                        .withAuto(isAuto);
                result = banManager.tryUpdateBan(updatedBan);
            }

            if (!result.isApplied()) {
                BanMutationFeedback.sendFailure(sender, result, target, isIp);
                return true;
            }


            String durationStr;
            if (banDuration == Long.MAX_VALUE) {
                durationStr = "永久";
            } else {
                durationStr = TimeUtils.formatDuration(banDuration);
            }

            if (escalationResult != null && escalationResult.offenseCount > 0) {
                Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().onEscalatedBan(
                        target, escalationResult.offenseCount, TimeUtils.formatDuration(banDuration)));
            }
            Utils.sendMessage(sender, plugin.prefix() + "§a成功更新目标 " + target + " 的封禁时间，新的封禁时长为: §e" + durationStr + "§a，理由: §e" + reason);
            plugin.getAuditManager().log("设置封禁时间", Utils.getSenderName(sender), target, durationStr + " - " + reason);

            return true;
        }

        private void sendUsage(CommandSender sender) {
            Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /setban <玩家名/IP> <时间/forever/auto> <理由>");
            Utils.sendMessage(sender, plugin.prefix() + "§c时间单位喵: s(秒), m(分钟), h(小时), d(天), w(周), M(月), y(年)");
            Utils.sendMessage(sender, plugin.prefix() + "§c使用 'forever' 表示永久封禁，使用 'auto' 自动计算封禁时间喵（基于警告次数）");
        }

        private void showTimeFormatError(CommandSender sender) {
            Utils.sendMessage(sender, plugin.prefix() + "§c时间格式错误喵，请使用以下格式:");
            Utils.sendMessage(sender, plugin.prefix() + "§c - 10s: 秒 (10 秒)");
            Utils.sendMessage(sender, plugin.prefix() + "§c - 5m: 分钟 (5 分钟)");
            Utils.sendMessage(sender, plugin.prefix() + "§c - 2h: 小时 (2 小时)");
            Utils.sendMessage(sender, plugin.prefix() + "§c - 7d: 天 (7 天)");
            Utils.sendMessage(sender, plugin.prefix() + "§c - 1w: 周 (1 周，等于 7 天)");
            Utils.sendMessage(sender, plugin.prefix() + "§c - 1M: 月 (1 月，按 30 天计算)");
            Utils.sendMessage(sender, plugin.prefix() + "§c - 1y: 年 (1 年，按 365 天计算)");
            Utils.sendMessage(sender, plugin.prefix() + "§c - forever: 永久封禁");
            Utils.sendMessage(sender, plugin.prefix() + "§c - auto: 自动计算封禁时间");
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                for (BanEntry e : plugin.getBanManager().getBanList()) {
                    if (e.getTarget().toLowerCase().startsWith(prefix)) completions.add(e.getTarget());
                }
                for (BanIpEntry e : plugin.getBanManager().getBanIpList()) {
                    if (e.getIp().toLowerCase().startsWith(prefix)) completions.add(e.getIp());
                }
            }
            return completions;
        }

    }

    public static final class Kick implements CommandExecutor, TabCompleter {

        private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_KICK = new java.util.concurrent.ConcurrentHashMap<>();
        private static final long KICK_COOLD_MS = 1500L;

        private final Lengbanlist plugin;

        public Kick(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!plugin.isFeatureEnabled("kick")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }

            if (!sender.hasPermission("lengbanlist.kick")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return true;
            }

            String senderKey = Utils.getSenderName(sender);
            long now = System.currentTimeMillis();
            Long last = LAST_KICK.get(senderKey);
            if (last != null && now - last < KICK_COOLD_MS) {
                Utils.sendMessage(sender, plugin.prefix() + "§e操作过于频繁,请稍候再试");
                return true;
            }
            LAST_KICK.put(senderKey, now);

            boolean silent = false;
            if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
                silent = true;
                args = Arrays.copyOfRange(args, 1, args.length);
            }

            if (args.length < 1) {
                Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /kick [-s] <玩家> [原因]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                Utils.sendMessage(sender, plugin.prefix() + "§c玩家 " + args[0] + " 不在线或不存在。");
                return true;
            }

            if (!plugin.getImmunityManager().canPunish(sender, target.getName())) {
                Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(target.getName()));
                return true;
            }

            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "§c你已被管理员踢出服务器";
            Model model = plugin.getModelManager().getCurrentModel();

            SchedulerUtils.runTask(plugin, target, () -> target.kickPlayer(model.getKickMessage(reason)));
            plugin.getAuditManager().log("踢出", Utils.getSenderName(sender), target.getName(), reason);
            if (!silent) {
                Utils.sendMessage(sender, plugin.prefix() + model.onKickSuccess(target.getName(), reason));
            }

            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
                }
            }
            return completions;
        }
    }
}

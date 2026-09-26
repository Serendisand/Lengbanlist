package org.leng.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.manager.ModelManager;
import org.leng.manager.WarnManager;
import org.leng.utils.IpMatcher;
import org.leng.utils.Utils;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.leng.object.WarnEntry;

public final class WarnCommands {

    private WarnCommands() {}

    public static final class Warn implements CommandExecutor, TabCompleter {
        private final Lengbanlist plugin;

        public Warn(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        public boolean execute(CommandSender sender, String s, String[] args) {
            if (!plugin.isFeatureEnabled("warn")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }


            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!sender.isOp() && !player.hasPermission("lengbanlist.warn")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                    return false;
                }
            }


            boolean silent = false;
            if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
                silent = true;
                args = Arrays.copyOfRange(args, 1, args.length);
            }

            if (args.length < 2) {
                Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /warn [-s] <玩家名/IP> <原因>");
                return false;
            }

            String target = args[0];
            String rawReason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            String reason = resolvePresetReason(rawReason);
            WarnManager warnManager = plugin.getWarnManager();


            boolean isIp = target.contains(".");


            if (!isIp && !plugin.getImmunityManager().canPunish(sender, target)) {
                Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(target));
                return false;
            }

            if (isIp) {
                if (!IpMatcher.isValidIpOrCidrOrWildcard(target)) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c无效的IP地址");
                    return false;
                }

                String normalized = IpMatcher.normalizeIpOrCidr(target);
                if (normalized != null) target = normalized;
                warnManager.warnPlayer(target, Utils.getSenderName(sender), reason);
                if (!silent) {
                    Utils.sendMessage(sender, ModelManager.getInstance().getCurrentModel().addWarn(target, reason));
                }
                return true;
            }

            warnManager.warnPlayer(target, Utils.getSenderName(sender), reason);
            if (!silent) {
                Utils.sendMessage(sender, ModelManager.getInstance().getCurrentModel().addWarn(target, reason));
            }

            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                List<String> completions = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
                }
                return completions;
            }
            if (args.length == 2) {
                String prefix = args[1].toLowerCase();
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

    public static final class Unwarn implements CommandExecutor {
        private final Lengbanlist plugin;

        public Unwarn(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        public boolean execute(CommandSender sender, String s, String[] args) {
            if (!plugin.isFeatureEnabled("unwarn")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }

            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!sender.isOp() && !player.hasPermission("lengbanlist.unwarn")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                    return false;
                }
            }

            boolean silent = false;
            if (args.length > 0 && args[0].equalsIgnoreCase("-s")) {
                silent = true;
                args = java.util.Arrays.copyOfRange(args, 1, args.length);
            }

            if (args.length < 1) {
                Utils.sendMessage(sender, plugin.prefix() + "§c用法错误喵: /unwarn [-s] <玩家名/IP> [警告ID]");
                return false;
            }

            String target = args[0];
            String normalized = IpMatcher.normalizeIpOrCidr(target);
            if (normalized != null) target = normalized;
            WarnManager warnManager = plugin.getWarnManager();

            List<WarnEntry> allWarnings = warnManager.getAllWarnings(target);
            if (allWarnings.isEmpty()) {
                Utils.sendMessage(sender, plugin.prefix() + "§c玩家 " + target + " 没有警告记录。");
                return false;
            }

            try {

                if (args.length > 1) {
                    int warnId = parseWarnId(args[1], allWarnings);
                    if (warnId != -1) {
                        WarnEntry entry = allWarnings.get(warnId - 1);
                        if (!entry.isRevoked()) {
                            entry = entry.revoke();
                            plugin.getDatabaseManager().updateWarningRevoked(entry.getId(), true, target);
                            plugin.getAuditManager().log("取消警告", Utils.getSenderName(sender), target, "警告ID: " + entry.getId());
                            if (!silent) {
                                Utils.sendMessage(sender, plugin.prefix() + "§a警告 #" + warnId + " 已移除");
                            }

                            warnManager.checkUnbanIfNecessary(target);
                        } else {
                            Utils.sendMessage(sender, plugin.prefix() + "§c警告 #" + warnId + " 已经被移除");
                        }
                    } else {
                        Utils.sendMessage(sender, plugin.prefix() + "§c警告ID无效");
                    }
                } else {

                    StringBuilder reasonBuilder = new StringBuilder();
                    for (WarnEntry warning : allWarnings) {
                        if (warning.isRevoked()) {
                            continue;
                        }
                        warning = warning.revoke();
                        plugin.getDatabaseManager().updateWarningRevoked(warning.getId(), true, target);
                        if (reasonBuilder.length() > 0) {
                            reasonBuilder.append(",");
                        }
                        reasonBuilder.append("警告ID: ").append(warning.getId());
                    }
                    String reason = reasonBuilder.length() == 0 ? "全部警告" : reasonBuilder.toString();
                    plugin.getAuditManager().log("取消警告", Utils.getSenderName(sender), target, reason);
                    if (!silent) {
                        Utils.sendMessage(sender, plugin.prefix() + "§a已移除玩家 " + target + " 的所有警告");
                    }

                    warnManager.checkUnbanIfNecessary(target);
                }
            } catch (Exception e) {

                String detail = e.getMessage();
                Utils.sendMessage(sender, plugin.prefix() + "§c处理警告时出错: " + (detail == null ? e.getClass().getSimpleName() : detail));
                return false;
            }

            return true;
        }

        private int parseWarnId(String input, List<WarnEntry> warnings) {
            try {
                int id = Integer.parseInt(input);
                if (id > 0 && id <= warnings.size()) {
                    return id;
                }
            } catch (NumberFormatException e) {

            }
            return -1;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            return execute(sender, label, args);
        }
    }

    public static final class WarnMsg implements CommandExecutor {
        private final Lengbanlist plugin;

        public WarnMsg(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!plugin.isFeatureEnabled("warn")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }


            if (!sender.hasPermission("lengbanlist.warnmsg")) {
                sender.sendMessage(plugin.prefix() + "§c只有管理员可以使用此命令。");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(plugin.prefix() + "§c用法错误喵: /warnmsg <玩家名>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.prefix() + "§c玩家 " + args[0] + " 不在线或不存在。");
                return true;
            }


            plugin.getWarnManager().warnPlayer(target.getName(), Utils.getSenderName(sender), "消息违规");
            target.sendMessage(plugin.prefix() + "§c你因消息违规被警告一次。");
            sender.sendMessage(plugin.prefix() + "§a已警告玩家 " + target.getName() + "。");
            return true;
        }
    }
}

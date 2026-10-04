package org.leng.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.AuditEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.api.DurationPolicyHook;
import org.leng.service.BanManager;
import org.leng.service.BanMutationFeedback;
import org.leng.integration.ModelManager;
import org.leng.integration.WebhookNotifier;
import org.leng.models.Model;
import org.leng.util.TimeUtils;
import org.leng.util.Utils;
import org.leng.util.IpMatcher;
import org.leng.integration.IpGeoLookup;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LengbanlistCommand extends Command implements CommandExecutor, TabCompleter {

    private final Lengbanlist plugin;
    private final IpGeoLookup ipGeoLookup;
    private final GuiCommands.Gui guiCommand;

    public LengbanlistCommand(String name, Lengbanlist plugin) {
        super(name);
        this.plugin = plugin;
        this.ipGeoLookup = new IpGeoLookup(plugin);
        this.guiCommand = new GuiCommands.Gui(plugin);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        Model currentModel = ModelManager.getInstance().getCurrentModel();
        if (args.length == 0) {
            currentModel.showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "toggle":
                if (!sender.hasPermission("lengbanlist.toggle")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (!plugin.isFeatureActive("broadcast")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                boolean enabled = !plugin.isBroadcastEnabled();
                plugin.setBroadcastEnabled(enabled);
                Utils.sendMessage(sender, currentModel.toggleBroadcast(enabled));
                plugin.getAuditManager().log(enabled ? "开启广播" : "关闭广播", Utils.getSenderName(sender), "", "");
                break;
            case "a":
                if (!sender.hasPermission("lengbanlist.broadcast")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (!plugin.isFeatureActive("broadcast")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                plugin.getBroadCastManager().broadcastNow(sender);
                break;
            case "list":
                if (!plugin.isFeatureEnabled("ban")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.list")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                showBanList(sender);
                break;
            case "reload":
                if (!sender.hasPermission("lengbanlist.reload")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (!plugin.isFeatureActive("reload")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                plugin.reloadExtensionsConfig();
                ModelManager.getInstance().reloadModel();

                File broadcastFile = new File(plugin.getDataFolder(), "broadcast.yml");
                if (broadcastFile.exists()) {
                    try {
                        plugin.getBroadcastFC().load(broadcastFile);
                    } catch (Exception e) {
                        plugin.getLogger().warning("重载broadcast.yml失败: " + e.getMessage());
                    }
                }
                File chatConfigFile = new File(plugin.getDataFolder(), "chatconfig.yml");
                if (chatConfigFile.exists()) {
                    try {
                        plugin.getChatConfig().load(chatConfigFile);
                    } catch (Exception e) {
                        plugin.getLogger().warning("重载chatconfig.yml失败: " + e.getMessage());
                    }
                }
                plugin.refreshFeatureCommands();
                Utils.sendMessage(sender, currentModel.reloadConfig());
                plugin.reloadWebServer();

                if (plugin.getThemeManager() != null) {
                    plugin.getThemeManager().load();
                }
                org.leng.util.PlayerProfileHelper.clearCache();
                if (plugin.getMuteManager() != null) {
                    plugin.getMuteManager().reloadMuteCache();
                }

                if (plugin.getDatabaseManager() != null) {
                    plugin.reloadStorageConfig();
                    plugin.getDatabaseManager().applyCacheConfig();
                    plugin.getDatabaseManager().reloadBanCache();
                    plugin.getIdentityResolver().invalidateAll();
                }
                if (plugin.getChatListener() != null) {
                    plugin.getChatListener().invalidateFilterCache();
                }
                if (plugin.getModelCloudManager() != null) {
                    plugin.getModelCloudManager().cachedIndexOnly();
                }
                if (!plugin.isFeatureEnabled("vanish") && plugin.getVanishManager() != null) {
                    plugin.getVanishManager().restoreAll();
                }
                plugin.restartScheduledTasks();
                if (plugin.getWebServer() != null) {
                    plugin.getWebServer().reloadAuth();
                }
                break;
            case "add": {
                int probeOffset = (args.length >= 2 && args[1].equalsIgnoreCase("-s")) ? 2 : 1;
                if (args.length > probeOffset && args[probeOffset].contains(".")) {
                    if (!plugin.isFeatureEnabled("ban-ip")) {
                        plugin.sendFeatureDisabled(sender);
                        return true;
                    }
                } else {
                    if (!plugin.isFeatureEnabled("ban")) {
                        plugin.sendFeatureDisabled(sender);
                        return true;
                    }
                }

                if (!sender.hasPermission("lengbanlist.ban") && !sender.hasPermission("lengbanlist.banip")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                String[] delegateArgs = Arrays.copyOfRange(args, 1, args.length);

                int ipOffset = 0;
                if (delegateArgs.length > 0 && delegateArgs[0].equalsIgnoreCase("-s")) {
                    ipOffset = 1;
                }
                boolean isIp = delegateArgs.length > ipOffset && (
                        IpMatcher.isValidIpOrCidrOrWildcard(delegateArgs[ipOffset])
                                || delegateArgs[ipOffset].contains(":"));
                if (isIp) {
                    return new BanCommands.BanIp(plugin).onCommand(sender, null, label, delegateArgs);
                }
                return new BanCommands.Ban(plugin).onCommand(sender, null, label, delegateArgs);
            }
            case "remove":
                if (!plugin.isFeatureEnabled("unban")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.unban")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new BanCommands.Unban(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "help":

                currentModel.showHelp(sender);
                break;
            case "open":
                if (!plugin.isFeatureEnabled("chest-ui")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.open")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                    guiCommand.openChestUI(player);
                } else {
                    Utils.sendMessage(sender, plugin.prefix() + "§c此命令只能由玩家执行。");
                }
                break;
            case "getip":
                if (!plugin.isFeatureEnabled("getip")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.getip")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new QueryCommands.GetIp(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "model":
                if (!plugin.isFeatureEnabled("model")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.model")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式/lban model <模型名称>");
                    StringBuilder availableModels = new StringBuilder("§6§l可用模型： §b");
                    for (String modelName : ModelManager.getInstance().getModels().keySet()) {
                        availableModels.append(modelName).append(" ");
                    }
                    Utils.sendMessage(sender, availableModels.toString());
                    return true;
                }
                String modelName = args[1].toLowerCase();
                boolean found = false;
                for (String name : ModelManager.getInstance().getModels().keySet()) {
                    if (name.equalsIgnoreCase(modelName)) {
                        ModelManager.switchModel(name);
                        Utils.sendMessage(sender, plugin.prefix() + "§a已切换到模型: " + name);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不认识这个模型喵。");
                    StringBuilder availableModels = new StringBuilder("§6§l可用模型： §b");
                    for (String name : ModelManager.getInstance().getModels().keySet()) {
                        availableModels.append(name).append(" ");
                    }
                    Utils.sendMessage(sender, availableModels.toString());
                }
                break;
            case "models":
                if (!sender.hasPermission("lengbanlist.model")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (!plugin.isFeatureActive("model")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                return new ModelsCommand(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "mute":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.mute")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new MuteCommands.Mute(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "unmute":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.mute")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new MuteCommands.Unmute(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "list-mute":
                if (!plugin.isFeatureEnabled("mute")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.listmute")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                showMuteList(sender);
                break;
            case "warn":
                if (!plugin.isFeatureEnabled("warn")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.warn")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new WarnCommands.Warn(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "unwarn":
                if (!plugin.isFeatureEnabled("unwarn")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.unwarn")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new WarnCommands.Unwarn(plugin).onCommand(sender, null, label, Arrays.copyOfRange(args, 1, args.length));
            case "report":
                if (!plugin.isFeatureEnabled("report")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!(sender instanceof Player)) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c此命令只能由玩家执行。");
                    return true;
                }

                String[] reportArgs = Arrays.copyOfRange(args, 1, args.length);
                return new ReportCommands.Report(plugin).onCommand(sender, this, label, reportArgs);
            case "tp":
                if (!plugin.isFeatureEnabled("tp")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.admin")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /lban tp <玩家名>");
                    return true;
                }
                Player targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer != null && sender instanceof Player) {
                    ((Player) sender).teleport(targetPlayer);
                    Utils.sendMessage(sender, plugin.prefix() + "§a已传送到玩家 " + targetPlayer.getName());
                } else {
                    Utils.sendMessage(sender, plugin.prefix() + "§c玩家不在线");
                }
                break;
            case "vanish":
                return new StaffCommands.Vanish(plugin).onCommand(sender, this, label, Arrays.copyOfRange(args, 1, args.length));
            case "freeze":
                return new FreezeCommands.Freeze(plugin).onCommand(sender, this, label, Arrays.copyOfRange(args, 1, args.length));
            case "unfreeze":
                return new FreezeCommands.Unfreeze(plugin).onCommand(sender, this, label, Arrays.copyOfRange(args, 1, args.length));
            case "admin":
                if (!plugin.isFeatureEnabled("admin")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.admin")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                new ReportCommands.AdminReport(plugin).onCommand(sender, this, label, args);
                break;
            case "check":
                if (!plugin.isFeatureEnabled("check")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.check")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式：/lban check <玩家名/IP>");
                    return true;
                }
                String checkTarget = args[1];
                String normalizedCheck = IpMatcher.normalizeIpOrCidr(checkTarget);
                if (normalizedCheck != null) checkTarget = normalizedCheck;
                QueryCommands.Check checkCommand = new QueryCommands.Check(plugin);
                checkCommand.execute(sender, "check", new String[]{checkTarget});
                break;
            case "info":
                if (!plugin.isFeatureEnabled("info")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.info")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                return new QueryCommands.Info(plugin).onCommand(sender, null, "info", new String[0]);
            case "history":
                if (!plugin.isFeatureEnabled("history")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.history")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /lban history <玩家名>");
                    return true;
                }
                String[] histArgs = Arrays.copyOfRange(args, 1, args.length);
                if (histArgs.length > 0) {
                    String histTarget = histArgs[0];
                    String normalizedHist = IpMatcher.normalizeIpOrCidr(histTarget);
                    if (normalizedHist != null) histArgs[0] = normalizedHist;
                }
                return new QueryCommands.History(plugin).onCommand(sender, this, "history", histArgs);
            case "audit":
                if (!plugin.isFeatureEnabled("audit")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.audit")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("export")) {
                    if (!plugin.isFeatureEnabled("export")) {
                        plugin.sendFeatureDisabled(sender);
                        return true;
                    }
                    if (!sender.hasPermission("lengbanlist.export")) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                        return true;
                    }
                    int auditExportLimit = 0;
                    if (args.length >= 3) {
                        try {
                            auditExportLimit = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            Utils.sendMessage(sender, plugin.prefix() + "§c§l导出数量格式不对喵，正确格式: /lban audit export [数量]");
                            return true;
                        }
                    }
                    plugin.getAuditManager().exportAudit(sender, auditExportLimit);
                    break;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("verify")) {
                    if (!sender.hasPermission("lengbanlist.export")) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                        return true;
                    }
                    if (!plugin.isFeatureActive("export")) {
                        plugin.sendFeatureDisabled(sender);
                        return true;
                    }
                    plugin.getAuditManager().verifyAudit(sender);
                    break;
                }
                String auditFilter = args.length >= 2 ? args[1] : "";
                List<AuditEntry> auditLogs = plugin.getAuditManager().getLogsByActor(auditFilter, 20);
                if (auditLogs.isEmpty()) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c暂无审计记录" + (auditFilter.isEmpty() ? "" : " (操作人: " + auditFilter + ")"));
                    return true;
                }
                Utils.sendMessage(sender, "§7--§bLengbanlist 审计日志" + (auditFilter.isEmpty() ? "" : " (操作人: §f" + auditFilter + "§b)") + "§7--");
                for (AuditEntry auditEntry : auditLogs) {
                    String mark = auditEntry.isSuccess() ? "§a[成功]" : "§c[失败]";
                    String serverTag = auditEntry.getServer().isEmpty() ? "" : "§8@" + auditEntry.getServer();
                    Utils.sendMessage(sender, mark + " §7[" + TimeUtils.timestampToReadable(auditEntry.getTimestamp()) + "] §e" + auditEntry.getAction() + " §f" + auditEntry.getActor() + serverTag + " §7→ §f" + auditEntry.getTarget() + " §7" + auditEntry.getReason());
                }
                break;
            case "webhook":
                if (!sender.hasPermission("lengbanlist.webhook")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (!plugin.isFeatureEnabled("webhook-events")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (args.length >= 2 && args[1].equalsIgnoreCase("test")) {
                    if (plugin.getWebhookNotifier() == null) {
                        Utils.sendMessage(sender, plugin.prefix() + "§cWebhook 模块尚未初始化。");
                        break;
                    }
                    plugin.getWebhookNotifier().sendTest(sender);
                    break;
                }
                showWebhookStatus(sender);
                break;
            case "handle":
                if (!plugin.isFeatureEnabled("report")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.admin")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 3) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式: /lban handle <举报ID> <时间/auto/forever> [原因]");
                    return true;
                }
                ReportEntry handleReport = plugin.getReportManager().getReport(args[1]);
                if (handleReport == null) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c未找到举报编号: " + args[1]);
                    return true;
                }
                String handleStatus = handleReport.getStatus();
                if (handleStatus == null || (!handleStatus.equals("未处理") && !handleStatus.equals("受理中") && !handleStatus.equals("已读"))) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c该举报已处理，无法再次操作。");
                    return true;
                }
                String handleTarget = handleReport.getTarget();
                if (!plugin.getPunishmentGate().canPunish(sender, handleTarget)) {
                    Utils.sendMessage(sender, plugin.getModelManager().getCurrentModel().getImmunityDenied(handleTarget));
                    return true;
                }
                boolean handleAuto = args[2].equalsIgnoreCase("auto");
                try {
                    long handleEndTime;
                    DurationPolicyHook.Decision handleEscalationResult = null;
                    if (args[2].equalsIgnoreCase("forever")) {
                        handleEndTime = Long.MAX_VALUE;
                    } else if (handleAuto) {
                        handleEscalationResult = handleTarget.contains(".")
                                ? plugin.getDurationPolicy().autoIpBan(handleTarget)
                                : plugin.getDurationPolicy().autoBan(handleTarget);
                        handleEndTime = TimeUtils.calculateEndTime(handleEscalationResult.durationMillis());
                    } else {
                        long handleDuration = TimeUtils.parseDurationToMillis(args[2]);
                        if (handleDuration <= 0) {
                            Utils.sendMessage(sender, plugin.prefix() + "§c时间格式无效喵，请使用：10s, 5m, 2h, 7d, 1w, 1M, 1y, forever, auto");
                            return true;
                        }
                        handleEndTime = TimeUtils.calculateEndTime(handleDuration);
                    }
                    String handleReason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : handleReport.getReason();
                    BanManager.BanMutationResult handleResult = plugin.getReportManager().tryBanFromReport(
                            handleReport, Utils.getSenderName(sender), handleEndTime, handleReason, handleAuto);
                    if (!handleResult.isApplied()) {
                        BanMutationFeedback.sendFailure(sender, handleResult, handleTarget, handleTarget.contains("."));
                        break;
                    }
                    if (handleEscalationResult != null && handleEscalationResult.offenseCount() > 0) {
                        Utils.sendMessage(sender, currentModel.onEscalatedBan(handleTarget,
                                handleEscalationResult.offenseCount(),
                                TimeUtils.formatDuration(handleEscalationResult.durationMillis(), TimeUtils.isEnglishLocale())));
                    }
                    String handleDurationText = handleEndTime == Long.MAX_VALUE
                            ? (TimeUtils.isEnglishLocale() ? "permanently" : "永久")
                            : TimeUtils.formatDuration(handleEndTime - System.currentTimeMillis(), TimeUtils.isEnglishLocale());
                    Utils.sendMessage(sender, plugin.prefix() + "§a已处理举报 " + handleReport.getId() + "，封禁玩家 " + handleTarget + "（" + handleDurationText + "）");
                } catch (IllegalArgumentException e) {

                    String detail = e.getMessage();
                    Utils.sendMessage(sender, plugin.prefix() + "§c" + (detail == null ? "参数无效" : detail));
                }
                break;
            case "alts":
                if (!plugin.isFeatureEnabled("alts")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.alts")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                if (args.length < 2 || args[1].isEmpty()) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式: /lban alts <玩家名>");
                    return true;
                }
                if (args[1].contains(".")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c§l参数应为玩家名，不能是 IP：/lban alts <玩家名>");
                    return true;
                }
                plugin.getAltsCommand().execute(sender, args[1]);
                break;
            case "sync":
                if (!plugin.isFeatureEnabled("sync")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.sync")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                new org.leng.service.SyncManager(plugin).execute(sender);
                break;
            case "rollback":
                if (!plugin.isFeatureEnabled("rollback")) {
                    plugin.sendFeatureDisabled(sender);
                    return true;
                }
                if (!sender.hasPermission("lengbanlist.rollback")) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c不是你的工作喵！");
                    return true;
                }
                String[] rollbackArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                return new StaffCommands.Rollback(plugin).onCommand(sender, null, "lban rollback", rollbackArgs);
            default:
                // 扩展提供的子命令。放在所有内置分支之后：核心自己的子命令优先级更高，
                // 扩展无法顶替它们。详见 CommandSpec.parent。
                if (plugin.getCommandRegistry() != null
                        && plugin.getCommandRegistry().dispatchSubcommand(
                                CommandRegistry.SUBCOMMAND_PARENT, args[0], sender, label, args)) {
                    return true;
                }

                Utils.sendMessage(sender, plugin.prefix() + "§c未知子命令喵: §f" + args[0] + "§c，输入 §f/lban help §c看看能用什么喵。");
                StringBuilder available = new StringBuilder("§6§l可用子命令： §b");
                java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                        "toggle", "a", "list", "reload", "add", "remove", "help", "open",
                        "getip", "model", "models", "mute", "unmute", "list-mute", "warn", "unwarn",
                        "report", "admin", "check", "info", "tp", "history", "audit", "webhook", "handle", "alts", "sync", "rollback",
                        "vanish", "freeze", "unfreeze"));
                // 并入扩展提供的子命令，避免功能迁出后这张列表变成过期的谎话
                if (plugin.getCommandRegistry() != null) {
                    names.addAll(plugin.getCommandRegistry().subcommandNames());
                }
                for (String s : names) {
                    available.append(s).append(" ");
                }
                Utils.sendMessage(sender, available.toString());
                break;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(sender, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            String[] subs = {"toggle", "a", "list", "reload", "add", "remove", "help", "open",
                    "getip", "model", "models", "mute", "unmute", "list-mute", "warn", "unwarn",
                    "report", "admin", "check", "info", "tp", "history", "audit", "webhook", "handle", "alts", "sync", "rollback",
                    "vanish", "freeze", "unfreeze"};
            for (String s : subs) {
                if (s.startsWith(prefix)) completions.add(s);
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("models")) {

            return new ModelsCommand(plugin).onTabComplete(sender, null, "", Arrays.copyOfRange(args, 1, args.length));
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("freeze")) {

            return new FreezeCommands.Freeze(plugin).onTabComplete(sender, null, "", Arrays.copyOfRange(args, 1, args.length));
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("unfreeze")) {

            return new FreezeCommands.Unfreeze(plugin).onTabComplete(sender, null, "", Arrays.copyOfRange(args, 1, args.length));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            switch (sub) {
                case "mute":
                case "warn":
                case "add":
                case "check":
                case "getip":
                case "tp":
                case "history":
                case "alts":
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
                    }
                    break;
                case "audit":
                    for (String s : new String[]{"export", "verify"}) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                    break;
                case "report":
                    for (String s : new String[]{"accept", "close"}) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                    break;
                case "webhook":
                    for (String s : new String[]{"test", "status"}) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                    break;
                case "handle":
                    for (ReportEntry r : plugin.getReportManager().getPendingReports()) {
                        if (r.getId().startsWith(prefix)) completions.add(r.getId());
                    }
                    break;
                case "unmute":
                    for (MuteEntry e : plugin.getMuteManager().getMuteList()) {
                        if (e.getTarget().toLowerCase().startsWith(prefix)) completions.add(e.getTarget());
                    }
                    break;
                case "unwarn":
                    for (String name : plugin.getWarnManager().getWarnedPlayers()) {
                        if (name.toLowerCase().startsWith(prefix)) completions.add(name);
                    }
                    break;
                case "remove":
                    for (BanEntry e : plugin.getBanManager().getBanList()) {
                        if (e.getTarget().toLowerCase().startsWith(prefix)) completions.add(e.getTarget());
                    }
                    break;
                case "model":
                    for (String name : ModelManager.getInstance().getModels().keySet()) {
                        if (name.toLowerCase().startsWith(prefix)) completions.add(name);
                    }
                    break;
            }
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("report")) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase();
                for (String s : new String[]{"accept", "close"}) {
                    if (s.startsWith(prefix)) completions.add(s);
                }
            } else if (args.length == 3 && (args[1].equalsIgnoreCase("accept") || args[1].equalsIgnoreCase("close"))) {
                String prefix = args[2].toLowerCase();
                for (ReportEntry r : plugin.getReportManager().getPendingReports()) {
                    if (r.getId().startsWith(prefix)) completions.add(r.getId());
                }
            }
        }
        return completions;
    }

    private static final int LIST_DISPLAY_LIMIT = 50;

    private void showBanList(CommandSender sender) {
        List<BanEntry> bans = plugin.getBanManager().getBanList();
        List<BanIpEntry> ipBans = plugin.getBanManager().getBanIpList();
        Utils.sendMessage(sender, "§7--§bLengbanlist 封禁名单 §7(玩家 " + bans.size() + " / IP " + ipBans.size() + ")§7--");
        if (bans.isEmpty() && ipBans.isEmpty()) {
            Utils.sendMessage(sender, "§7当前没有封禁记录。");
            return;
        }
        int shown = 0;
        Map<String, Long> banStarts = plugin.getDatabaseManager().getActiveStartTimes("bans");
        Map<String, Long> ipBanStarts = plugin.getDatabaseManager().getActiveStartTimes("ip_bans");
        for (BanEntry entry : bans) {
            if (shown++ >= LIST_DISPLAY_LIMIT) {
                break;
            }
            Long start = banStarts.get(entry.getTarget().toLowerCase(java.util.Locale.ROOT));
            Utils.sendMessage(sender, "§c被封禁者：§f" + entry.getTarget() + " §e处理人：§f" + entry.getStaff() + " §e封禁原因：§f" + entry.getReason() + " §f封禁时长：§e" + TimeUtils.formatIssuedDuration(start == null ? 0L : start, entry.getTime()) + " §f解封时间：§b" + TimeUtils.timestampToReadable(entry.getTime()));
        }
        for (BanIpEntry entry : ipBans) {
            if (shown++ >= LIST_DISPLAY_LIMIT) {
                break;
            }
            Long start = ipBanStarts.get(entry.getIp().toLowerCase(java.util.Locale.ROOT));
            Utils.sendMessage(sender, "§c被封禁IP：§f" + entry.getIp() + " §e处理人：§f" + entry.getStaff() + " §e封禁原因：§f" + entry.getReason() + " §f封禁时长：§e" + TimeUtils.formatIssuedDuration(start == null ? 0L : start, entry.getTime()) + " §f解封时间：§b" + TimeUtils.timestampToReadable(entry.getTime()));
        }
        int total = bans.size() + ipBans.size();
        if (total > LIST_DISPLAY_LIMIT) {
            Utils.sendMessage(sender, "§7仅显示前 " + LIST_DISPLAY_LIMIT + " 条，共 " + total + " 条。完整列表：§f/lban open §7或 Web 面板。");
        }
    }

    private void showWebhookStatus(CommandSender sender) {
        WebhookNotifier notifier = plugin.getWebhookNotifier();
        if (notifier == null) {
            Utils.sendMessage(sender, plugin.prefix() + "§cWebhook 模块尚未初始化。");
            return;
        }
        WebhookNotifier.Status status = notifier.status();
        Utils.sendMessage(sender, "§7--§bLengbanlist Webhook 状态§7--");
        Utils.sendMessage(sender, plugin.prefix() + "§7配置：" + (status.isConfigured() ? "§a" : "§e") + status.configState());
        Utils.sendMessage(sender, plugin.prefix() + "§7投递线程：§f" + (status.isRunning() ? "运行中" : "未启动（暂无可投递事件）"));
        Utils.sendMessage(sender, plugin.prefix() + "§7队列深度：§f" + status.queueSize() + "§7/§f" + status.queueCapacity());
        Utils.sendMessage(sender, plugin.prefix() + "§7上次成功：" + (status.lastSuccessAt() > 0L ? "§f" + TimeUtils.timestampToReadable(status.lastSuccessAt()) : "§7无记录"));
        Utils.sendMessage(sender, plugin.prefix() + "§7上次失败：" + (status.lastError().isEmpty() ? "§7无记录"
                : "§c" + status.lastError() + " §7(" + TimeUtils.timestampToReadable(status.lastErrorAt()) + ")"));
        Utils.sendMessage(sender, plugin.prefix() + "§7统计：§a送达 " + status.delivered() + " §e重试 " + status.retried()
                + " §c失败 " + status.failed() + " §c丢弃 " + status.dropped());
        Utils.sendMessage(sender, plugin.prefix() + "§7发送测试消息：§f/lban webhook test");
    }

    private void showMuteList(CommandSender sender) {
        Utils.sendMessage(sender, "§7--§bLengbanlist 禁言名单§7--");
        Map<String, Long> muteStarts = plugin.getDatabaseManager().getActiveStartTimes("mutes");
        for (MuteEntry entry : plugin.getMuteManager().getMuteList()) {
            Long start = muteStarts.get(entry.getTarget().toLowerCase(java.util.Locale.ROOT));
            Utils.sendMessage(sender, "§c被禁言者：§f" + entry.getTarget() + " §e处理人：§f" + entry.getStaff() + " §e禁言原因：§f" + entry.getReason() + " §f禁言时长：§e" + TimeUtils.formatIssuedDuration(start == null ? 0L : start, entry.getTime()) + " §f解禁时间：§b" + TimeUtils.timestampToReadable(entry.getTime()));
        }
    }
}

package org.leng.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.leng.Lengbanlist;
import org.leng.object.BanIpEntry;
import org.leng.object.PlayerIdentity;
import org.leng.util.IpMatcher;
import org.leng.util.TimeUtils;
import org.leng.util.Utils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.TabCompleter;
import org.leng.integration.ModelManager;
import org.leng.models.Model;
import org.leng.object.BanEntry;
import org.leng.object.MuteEntry;
import org.leng.object.WarnEntry;
import java.util.Comparator;
import java.util.Map;
import org.leng.integration.IpGeoLookup;
import org.leng.service.PlayerIpService;
import org.leng.util.SchedulerUtils;
import java.net.InetSocketAddress;
import org.leng.integration.GitHubUpdateChecker;
import java.lang.management.ManagementFactory;

public final class QueryCommands {

    private QueryCommands() {}

    public static final class Check implements CommandExecutor {
        private final Lengbanlist plugin;

        public Check(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            if (!plugin.isFeatureEnabled("check")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }

            if (!sender.hasPermission("lengbanlist.check")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return true;
            }

            if (args.length < 1) {
                Utils.sendMessage(sender, plugin.prefix() + "§c§l命令格式不对喵，正确格式：/check <玩家名/IP>");
                return true;
            }

            String target = args[0];

            if (IpMatcher.isValidIpOrCidrOrWildcard(target) || target.contains(":")) {
                checkIpInfo(sender, target);
            } else {
                checkPlayerInfo(sender, target);
            }
            return true;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            return execute(sender, label, args);
        }

        private void checkPlayerInfo(CommandSender sender, String playerName) {
            Player online = plugin.getServer().getPlayerExact(playerName);
            OfflinePlayer player;
            if (online != null) {
                player = online;
            } else {
                player = org.leng.util.PlayerProfileHelper.lookupSync(playerName);
                if (player == null) {
                    Utils.sendMessage(sender, plugin.prefix() + "§c未找到玩家：" + playerName + "（离线查询不可用，请在 Folia 服务端查询在线玩家）");
                    return;
                }
            }

            String uuid = player.getUniqueId().toString();
            long lastLogin = player.getLastPlayed();
            String lastLoginTime = lastLogin == 0 ? "从未登录" : TimeUtils.timestampToReadable(lastLogin);
            boolean isMuted = plugin.getMuteManager().isPlayerMuted(playerName);
            boolean isBanned = plugin.getBanManager().isPlayerBanned(playerName)
                    || plugin.getBanManager().isPlayerBannedByUuid(uuid);
            boolean isOp = player.isOp();

            String specialTag = "a5dc2127-d472-4c87-90b6-0b9fff386236".equals(uuid) ? "§c[DEV] " : "";

            Utils.sendMessage(sender, plugin.prefix() + "§a玩家信息：");
            Utils.sendMessage(sender, plugin.prefix() + "§b玩家名: " + specialTag + playerName);
            Utils.sendMessage(sender, plugin.prefix() + "§bUUID: " + uuid);
            showIdentityNames(sender, uuid, playerName);
            Utils.sendMessage(sender, plugin.prefix() + "§b最后登录时间: " + lastLoginTime);
            Utils.sendMessage(sender, plugin.prefix() + "§b是否禁言: " + (isMuted ? "是" : "否"));
            Utils.sendMessage(sender, plugin.prefix() + "§b是否封禁: " + (isBanned ? "是" : "否"));
            Utils.sendMessage(sender, plugin.prefix() + "§b是否是OP: " + (isOp ? "是" : "否"));
            if (online != null) {
                org.leng.object.FreezeEntry freeze = plugin.getFreezeManager().get(online);
                if (freeze != null) {
                    Utils.sendMessage(sender, plugin.prefix() + "§b冻结状态: §c已冻结 §7(处理人 " + freeze.staff() + "，理由：" + freeze.reason() + ")");
                }
                if (plugin.getVanishManager().isVanished(online)) {
                    Utils.sendMessage(sender, plugin.prefix() + "§b隐身状态: §d隐身中");
                }
            }

            if (plugin.isFeatureEnabled("ip-association")) {
                Utils.sendMessage(sender, "§7--- §cIP关联信息 §7---");
                List<String[]> ipHistory = plugin.getIpAssociationManager().getPlayerIps(playerName);
                Set<String> ownNames = plugin.getIpAssociationManager().ownNames(uuid);
                ownNames.add(playerName.toLowerCase(Locale.ROOT));
                if (ipHistory.isEmpty()) {
                    Utils.sendMessage(sender, plugin.prefix() + "§e暂无 IP 记录");
                } else {
                    for (String[] record : ipHistory) {
                        String ip = record[0];
                        String firstSeen = TimeUtils.timestampToReadable(Long.parseLong(record[1]));
                        List<String> others = new ArrayList<>();
                        for (String ap : plugin.getDatabaseManager().getPlayersByIpFromHistory(ip)) {
                            if (!ownNames.contains(ap.toLowerCase(Locale.ROOT))) {
                                others.add(ap);
                            }
                        }
                        StringBuilder line = new StringBuilder();
                        line.append(" §7- §f").append(ip).append(" §7(首次: ").append(firstSeen).append(")");
                        if (!others.isEmpty()) {
                            line.append(" §c关联: §f").append(String.join(" ", others));
                        }
                        Utils.sendMessage(sender, plugin.prefix() + line.toString());
                    }
                }
            }

            if ("a5dc2127-d472-4c87-90b6-0b9fff386236".equals(uuid)) {
                showSponsorInfo(sender);
            }

        }

        private void showIdentityNames(CommandSender sender, String uuid, String playerName) {
            PlayerIdentity identity = plugin.getIdentityResolver().resolve(uuid);
            if (!identity.hasUuid()) {
                identity = plugin.getIdentityResolver().resolve(playerName);
            }
            if (!identity.hasUuid()) {
                return;
            }
            if (!identity.name().isEmpty() && !identity.name().equalsIgnoreCase(playerName)) {
                Utils.sendMessage(sender, plugin.prefix() + "§b当前名: §f" + identity.name());
            }
            List<String> aliases = new ArrayList<>();
            for (String name : identity.knownNames()) {
                if (!name.equalsIgnoreCase(identity.name())) {
                    aliases.add(name);
                }
            }
            if (!aliases.isEmpty()) {
                Utils.sendMessage(sender, plugin.prefix() + "§b曾用名: §f" + String.join("§7, §f", aliases));
            }
        }

        private void checkIpInfo(CommandSender sender, String ip) {

            if (!IpMatcher.isValidIpOrCidrOrWildcard(ip) && !ip.contains(":")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c这不是合法的 IP 地址: §f" + ip);
                return;
            }
            String normalized = IpMatcher.normalizeIpOrCidr(ip);
            if (normalized != null) ip = normalized;

            boolean isBanned = plugin.getBanManager().isIpBanned(ip);
            if (IpMatcher.isIpv4(ip) && plugin.getBanManager().isIpBannedByCidr(ip)) {
                isBanned = true;
            }
            List<String> associatedPlayers = plugin.getDatabaseManager().getPlayersByIpFromHistory(ip);

            Utils.sendMessage(sender, plugin.prefix() + "§aIP信息：");
            Utils.sendMessage(sender, plugin.prefix() + "§bIP: " + ip);
            Utils.sendMessage(sender, plugin.prefix() + "§b是否封禁: " + (isBanned ? "是" : "否"));

            if (IpMatcher.isIpv4(ip)) {
                for (BanIpEntry entry : plugin.getBanManager().getBanIpList()) {
                    if (IpMatcher.isCidr(entry.getIp()) && IpMatcher.cidrMatches(ip, entry.getIp())) {
                        Utils.sendMessage(sender, plugin.prefix() + "§c命中网段封禁: §f" + entry.getIp() + " §7原因: " + entry.getReason() + " §7解封时间: " + TimeUtils.timestampToReadable(entry.getTime()));
                    }
                }
            }

            Utils.sendMessage(sender, plugin.prefix() + "§b关联玩家: " + (associatedPlayers.isEmpty() ? "无" : String.join(", ", associatedPlayers)));
        }

        private void showSponsorInfo(CommandSender sender) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                TextComponent sponsorButton = new TextComponent(plugin.prefix() + "§6支持作者，让他更有动力开发插件！§b[§a点击赞助§b]");
                sponsorButton.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("§a点击支持作者§bawa").create()));
                sponsorButton.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://afdian.com/a/lengmc"));

                player.spigot().sendMessage(sponsorButton);

                Utils.sendMessage(player, plugin.prefix() + "§b请我喝杯奶茶：￥20.00 CNY/月 - 加入感谢名单，优先反馈");
                Utils.sendMessage(player, plugin.prefix() + "§bBETA权限组：￥50.00 CNY/月 - 解锁高级功能，优先支持");
                Utils.sendMessage(player, plugin.prefix() + "§b一次性打赏：任意金额 - 表达你的支持");
            } else {

                Utils.sendMessage(sender, plugin.prefix() + "§6支持作者，让他更有动力开发插件！§b[§a点击赞助§b] §c(https://afdian.com/a/lengmc)");
                Utils.sendMessage(sender, plugin.prefix() + "§b请我喝杯奶茶：￥20.00 CNY/月 - 加入感谢名单，优先反馈");
                Utils.sendMessage(sender, plugin.prefix() + "§bBETA权限组：￥50.00 CNY/月 - 解锁高级功能，优先支持");
                Utils.sendMessage(sender, plugin.prefix() + "§b一次性打赏：任意金额 - 表达你的支持");
            }
        }
    }

    public static final class History implements CommandExecutor, TabCompleter {
        private final Lengbanlist plugin;

        public History(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) {
                        completions.add(p.getName());
                    }
                }
            }
            return completions;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!plugin.isFeatureEnabled("history")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }
            if (!sender.hasPermission("lengbanlist.history")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return true;
            }
            if (args.length < 1) {
                Utils.sendMessage(sender, plugin.prefix() + "§c用法喵: /" + label + " <玩家名/IP>");
                return true;
            }

            String target = args[0];
            String normalizedTarget = IpMatcher.normalizeIpOrCidr(target);
            if (normalizedTarget != null) target = normalizedTarget;

            List<HistoryEntry> raw = new ArrayList<>();

            boolean isIp = target.contains(".");

            if (!isIp) {
                showKnownNames(sender, target);
            }

            Map<Long, Long> banStarts = plugin.getDatabaseManager().getStartTimesByPlayer("bans", target);
            Map<Long, Long> ipBanStarts = plugin.getDatabaseManager().getStartTimesByPlayer("ip_bans", target);
            Map<Long, Long> muteStarts = plugin.getDatabaseManager().getStartTimesByPlayer("mutes", target);

            if (isIp) {
                for (BanIpEntry ban : plugin.getDatabaseManager().getIpBansByIp(target)) {
                    raw.add(new HistoryEntry(ban.getTime(), "ipban", ban, startOf(ipBanStarts, ban.getTime())));
                }
            } else {
                for (BanEntry ban : plugin.getDatabaseManager().getBansByPlayer(target)) {
                    raw.add(new HistoryEntry(ban.getTime(), "ban", ban, startOf(banStarts, ban.getTime())));
                }
            }

            for (MuteEntry mute : plugin.getDatabaseManager().getMutesByPlayer(target)) {
                raw.add(new HistoryEntry(mute.getTime(), "mute", mute, startOf(muteStarts, mute.getTime())));
            }

            for (WarnEntry warn : plugin.getDatabaseManager().getWarnings(target, false)) {
                raw.add(new HistoryEntry(warn.getTime(), "warn", warn, 0L));
            }

            raw.sort(Comparator.comparingLong(e -> e.time));

            List<String> entries = new ArrayList<>();
            for (HistoryEntry he : raw) {
                String line = he.format();
                if (!line.isEmpty()) entries.add(line);
            }

            if (entries.isEmpty()) {
                if (isIp) {
                    Utils.sendMessage(sender, plugin.prefix() + "§7该IP暂无封禁记录。");
                } else {
                    Utils.sendMessage(sender, plugin.prefix() + "§7该玩家暂无处罚记录。");
                }
                return true;
            }

            Model model = ModelManager.getInstance().getCurrentModel();
            String result = model.getHistory(target, entries);
            for (String line : result.split("\n")) {
                Utils.sendMessage(sender, line);
            }
            return true;
        }

        private static long startOf(Map<Long, Long> startTimes, long endTime) {
            Long value = startTimes.get(endTime);
            return value == null ? 0L : value;
        }

        private void showKnownNames(CommandSender sender, String target) {
            PlayerIdentity identity = plugin.getIdentityResolver().resolve(target);
            List<String> aliases = new ArrayList<>();
            for (String name : identity.knownNames()) {
                if (!name.equalsIgnoreCase(identity.name())) {
                    aliases.add(name);
                }
            }
            if (!aliases.isEmpty()) {
                Utils.sendMessage(sender, plugin.prefix() + "§b曾用名: §f" + String.join("§7, §f", aliases));
            }
        }

        private static class HistoryEntry {
            final long time;
            final String type;
            final Object data;
            final long startTime;

            HistoryEntry(long time, String type, Object data, long startTime) {
                this.time = time;
                this.type = type;
                this.data = data;
                this.startTime = startTime;
            }

            String format() {
                switch (type) {
                    case "ban": {
                        BanEntry b = (BanEntry) data;
                        boolean inactive = !b.isActive();
                        boolean expired = b.isExpired();
                        boolean permanent = b.getTime() == Long.MAX_VALUE;
                        String durationPart = startTime > 0
                                ? " §7| 封禁时长: §e" + TimeUtils.formatIssuedDuration(startTime, b.getTime()) : "";
                        if (inactive || expired) {
                            String expiryAt = permanent ? "永久" : TimeUtils.timestampToReadable(b.getTime());
                            return "§7- §c封禁 §7| §a已过期 §7| 过期时间: §f" + expiryAt + durationPart + " §7| 处理人: §b" + b.getStaff() + " §7| 原因: §f" + b.getReason();
                        }
                        String expiryStr = permanent ? "永久" : TimeUtils.timestampToReadable(b.getTime());
                        return "§7- §c封禁 §7| 处理人: §b" + b.getStaff() + " §7| 封禁至: §f" + expiryStr + durationPart + " §7| 原因: §f" + b.getReason();
                    }
                    case "ipban": {
                        BanIpEntry b = (BanIpEntry) data;
                        boolean inactive = !b.isActive();
                        boolean expired = b.isExpired();
                        boolean permanent = b.getTime() == Long.MAX_VALUE;
                        String durationPart = startTime > 0
                                ? " §7| 封禁时长: §e" + TimeUtils.formatIssuedDuration(startTime, b.getTime()) : "";
                        if (inactive || expired) {
                            String expiryAt = permanent ? "永久" : TimeUtils.timestampToReadable(b.getTime());
                            return "§7- §cIP封禁 §7| §a已过期 §7| 过期时间: §f" + expiryAt + durationPart + " §7| 处理人: §b" + b.getStaff() + " §7| 原因: §f" + b.getReason();
                        }
                        String expiryStr = permanent ? "永久" : TimeUtils.timestampToReadable(b.getTime());
                        return "§7- §cIP封禁 §7| 处理人: §b" + b.getStaff() + " §7| 封禁至: §f" + expiryStr + durationPart + " §7| 原因: §f" + b.getReason();
                    }
                    case "mute": {
                        MuteEntry m = (MuteEntry) data;
                        String durationPart = startTime > 0
                                ? " §7| 禁言时长: §e" + TimeUtils.formatIssuedDuration(startTime, m.getTime()) : "";
                        return "§7- §c禁言 §7| 处理人: §b" + m.getStaff() + " §7| 解禁时间: §f" + TimeUtils.timestampToReadable(m.getTime()) + durationPart + " §7| 原因: §f" + m.getReason();
                    }
                    case "warn": {
                        WarnEntry w = (WarnEntry) data;
                        String status = w.isRevoked() ? "§a是" : "§c否";
                        return "§7- §e警告 §7| 是否撤销: " + status + " §7| 处理人: §b" + w.getStaff() + " §7| 时间: §f" + TimeUtils.timestampToReadable(w.getTime()) + " §7| 原因: §f" + w.getReason();
                    }
                    default:
                        return "";
                }
            }
        }
    }

    public static final class GetIp implements CommandExecutor {
        private final Lengbanlist plugin;
        private final IpGeoLookup ipGeoLookup;

        public GetIp(Lengbanlist plugin) {
            this.plugin = plugin;
            this.ipGeoLookup = new IpGeoLookup(plugin);
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission("lengbanlist.getip")) {
                sender.sendMessage(plugin.prefix() + "§c你没有权限使用该命令！");
                return true;
            }

            if (args.length == 0) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    java.net.InetSocketAddress addr = player.getAddress();
                    if (addr == null || addr.getAddress() == null) {
                        sender.sendMessage(plugin.prefix() + "§c无法获取你的地址");
                        return true;
                    }
                    showIpLocation(player, addr.getAddress().getHostAddress(), "你");
                } else {
                    sender.sendMessage(plugin.prefix() + "§c请指定一个玩家名称，例如: /lban getip <玩家名称>");
                }
            } else {

                Player targetPlayer = plugin.getServer().getPlayer(args[0]);
                if (targetPlayer != null) {
                    java.net.InetSocketAddress addr = targetPlayer.getAddress();
                    if (addr == null || addr.getAddress() == null) {
                        sender.sendMessage(plugin.prefix() + "§c该玩家没有可用地址");
                        return true;
                    }
                    showIpLocation(sender, addr.getAddress().getHostAddress(), "玩家 " + targetPlayer.getName());
                } else {
                    String cachedIp = PlayerIpService.getIP(args[0]);
                    if (cachedIp == null) {
                        sender.sendMessage(plugin.prefix() + "§c未找到玩家：" + args[0]);
                        return true;
                    }
                    showIpLocation(sender, cachedIp, "玩家 " + args[0]);
                }
            }
            return true;
        }

        private void showIpLocation(CommandSender sender, String ip, String who) {
            if (isLocalIp(ip)) {
                sender.sendMessage(plugin.prefix() + "§e" + who + " 的 IP 为 " + ip + "（本地/局域网地址，无法查询地理位置）");
                return;
            }
            getPlayerLocationAsync(ip, sender, location -> {
                if (location != null) {
                    sender.sendMessage(plugin.prefix() + "§a" + who + " 的 IP 地理位置为：§e" + location);
                } else {
                    sender.sendMessage(plugin.prefix() + "§c无法获取 " + who + " 的 IP 地理位置信息！");
                }
            });
        }

        private boolean isLocalIp(String ip) {
            if (ip == null) return true;
            if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) return true;
            if (ip.startsWith("10.") || ip.startsWith("172.") || ip.startsWith("192.168.")) return true;
            if (ip.startsWith("fd") || ip.startsWith("fc")) return true;
            return false;
        }

        private void getPlayerLocationAsync(String ip, CommandSender sender, LocationInfoCallback callback) {
            SchedulerUtils.runAsync(plugin, () -> {
                String locationInfo = ipGeoLookup.lookup(ip);
                SchedulerUtils.runTask(plugin, sender, () -> callback.onLocationInfoReceived(locationInfo));
            });
        }

        public interface LocationInfoCallback {
            void onLocationInfoReceived(String locationInfo);
        }
    }

    public static final class Info implements CommandExecutor {
        private final Lengbanlist plugin;

        public Info(Lengbanlist plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!plugin.isFeatureEnabled("info")) {
                plugin.sendFeatureDisabled(sender);
                return true;
            }

            if (!sender.hasPermission("lengbanlist.info")) {
                Utils.sendMessage(sender, plugin.prefix() + "§c你没有权限使用此命令。");
                return true;
            }

            String serverVersion = plugin.getServer().getVersion();
            String serverCore = getServerCoreName();
            long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long totalMemory = Runtime.getRuntime().totalMemory();
            int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
            double cpuLoad = getSystemCpuLoad();

            StringBuilder infoMessage = new StringBuilder();
            infoMessage.append("§b§lLengbanlist 插件信息 §b§l").append(plugin.getDescription().getVersion()).append("\n");
            infoMessage.append("§7当前运行在：§b").append(serverVersion).append("\n");
            infoMessage.append("§7当前服务端核心：§b").append(serverCore).append("\n");
            infoMessage.append("§7当前内存占用：§b").append(usedMemory / (1024 * 1024)).append("MB / ").append(totalMemory / (1024 * 1024)).append("MB\n");
            infoMessage.append("§7当前在线玩家：§b").append(onlinePlayers).append("\n");
            infoMessage.append("§7当前CPU占用：§b").append(String.format("%.2f", cpuLoad)).append("%\n");
            String serverName = plugin.getServerName();
            infoMessage.append("§7本服标识：§b").append(serverName.isEmpty() ? "未设置" : serverName)
                    .append(" §7| §b存储：").append(plugin.getDatabaseManager().getDatabaseProductName()).append("\n");
            infoMessage.append("§7隐身中：§b").append(plugin.getVanishManager().count())
                    .append(" §7| §b冻结中：§b").append(plugin.getFreezeManager().count()).append("\n");

            if (plugin.isUpdateCheckEnabled()) {
                GitHubUpdateChecker.getLatestReleaseVersionAsync(plugin).thenAccept(latestVersion -> {
                    String message;
                    if (latestVersion == null) {
                        message = "§c检查更新失败，请检查网络连接或稍后再试。\n";
                    } else if (GitHubUpdateChecker.compareVersions(plugin.getDescription().getVersion(), latestVersion) < 0) {
                        message = "§a发现新版本：§e" + latestVersion + "§a，当前版本：§e" + plugin.getDescription().getVersion() + "\n" +
                                 "§b更新地址：§e" + GitHubUpdateChecker.RELEASES_URL + "\n";
                    } else {
                        message = "§a你正在使用最新版本：§e" + plugin.getDescription().getVersion() + "\n";
                    }
                    Utils.sendMessage(sender, infoMessage.toString() + message);
                });
            } else {
                infoMessage.append("§a更新检查已禁用\n");
                Utils.sendMessage(sender, infoMessage.toString());
            }

            return true;
        }

        private String getServerCoreName() {
            try {
                String serverPackage = plugin.getServer().getClass().getPackage().getName();
                if (serverPackage.contains("spigot")) return "Spigot";
                else if (serverPackage.contains("paper")) return "Paper";
                else if (serverPackage.contains("leaves")) return "Leaves";
                else if (serverPackage.contains("bukkit")) return "Bukkit";
                else return "Unknown";
            } catch (Exception e) {
                return "Unknown";
            }
        }

        private double getSystemCpuLoad() {
            try {
                com.sun.management.OperatingSystemMXBean osBean =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                return osBean.getSystemCpuLoad() * 100;
            } catch (Exception e) {
                return 0.0;
            }
        }
    }
}

package org.leng.service;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.api.PunishmentDecisionHook;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * 权重免疫<b>策略</b>：实现 {@link PunishmentDecisionHook}，由核心作为内置提供者
 * 注册到 {@code immunity} 功能键上。
 *
 * <p>职责边界（Phase 0.6 拆分后）：本类只回答"权重是多少"——权重从
 * LuckPerms 组权重、{@code lengbanlist.weight.N} 权限、配置默认值三处依次取。
 * "谁能罚谁"的判断与"策略缺席一律放行"的兜底都在
 * {@link org.leng.extension.PunishmentGate}，因此本类不再自行检查
 * {@code features.immunity}——是否启用由闸门统一决定。
 *
 * <p>Phase 3 会把这个类整体搬到 {@code Lengbanlist-Extensions} 的
 * {@code punishpolicy} 模块；届时它只需在原位置注册同一个钩子，核心代码零改动。
 */
public class ImmunityManager implements PunishmentDecisionHook {

    private final Lengbanlist plugin;
    private Boolean luckPermsPresent;

    public ImmunityManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @Override
    public int operatorWeight(CommandSender staff) {
        if (!(staff instanceof Player)) {
            return Integer.MAX_VALUE;
        }
        Player player = (Player) staff;
        if (player.isOp()) {
            return Integer.MAX_VALUE;
        }
        return resolveWeight(player);
    }

    @Override
    public int webOperatorWeight() {
        return plugin.getConfig().getInt("web.operator-weight", Integer.MAX_VALUE);
    }

    @Override
    public int playerWeight(String targetName) {
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            // 离线玩家不享受免疫
            return Integer.MIN_VALUE;
        }
        return resolveWeight(target);
    }

    @Override
    public int ipWeight(String ip) {
        int highest = Integer.MIN_VALUE;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getAddress() == null || online.getAddress().getAddress() == null) {
                continue;
            }
            String playerIp = online.getAddress().getAddress().getHostAddress();
            if (playerIp == null || !playerIp.equals(ip)) {
                continue;
            }
            int weight = resolveWeight(online);
            if (weight > highest) {
                highest = weight;
            }
        }
        return highest;
    }

    private int resolveWeight(Player player) {
        if (isLuckPermsPresent()) {
            int luckPermsWeight = getLuckPermsWeight(player);
            if (luckPermsWeight != -1) {
                return luckPermsWeight;
            }
        }
        for (int i = 99; i >= 1; i--) {
            if (player.hasPermission("lengbanlist.weight." + i)) {
                return i;
            }
        }
        return plugin.getConfig().getInt("immunity.default-weight", 0);
    }

    private boolean isLuckPermsPresent() {
        if (luckPermsPresent == null) {
            luckPermsPresent = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
        }
        return luckPermsPresent;
    }

    private int getLuckPermsWeight(Player player) {
        try {
            Object api = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) {
                return -1;
            }
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            if (cachedData == null) {
                return -1;
            }
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            if (metaData == null) {
                return -1;
            }
            Object primaryGroup = metaData.getClass().getMethod("getPrimaryGroup").invoke(metaData);
            if (primaryGroup == null) {
                return -1;
            }
            Object groupManager = api.getClass().getMethod("getGroupManager").invoke(api);
            Object group = groupManager.getClass().getMethod("getGroup", String.class).invoke(groupManager, primaryGroup);
            if (group == null) {
                return -1;
            }
            Object weight = group.getClass().getMethod("getWeight").invoke(group);
            if (weight instanceof OptionalInt) {
                OptionalInt optionalWeight = (OptionalInt) weight;
                if (optionalWeight.isPresent()) {
                    return optionalWeight.getAsInt();
                }
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }
}

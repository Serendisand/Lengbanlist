package org.leng.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.leng.Lengbanlist;

import java.io.File;
import java.util.List;
import org.leng.service.MuteCommandBlockPolicy;

public class MuteCommandBlockListener implements Listener {
    private final Lengbanlist plugin;
    private volatile Policy policy;

    private record Policy(List<String> blockedCommands, boolean stripNamespace) {}

    public MuteCommandBlockListener(Lengbanlist plugin) {
        this.plugin = plugin;
        MuteCommandBlockPolicy.migrateLegacyConfig(
                plugin.getChatConfig(), plugin.getConfig(),
                new File(plugin.getDataFolder(), "chatconfig.yml"));
    }

    /** 配置重载后调用；下一次命令检查会重新读取屏蔽表。 */
    public void invalidateConfigCache() {
        policy = null;
    }

    private Policy policy() {
        Policy cached = policy;
        if (cached == null) {
            cached = new Policy(
                    List.copyOf(MuteCommandBlockPolicy.resolveBlockedCommands(
                            plugin.getChatConfig(), plugin.getConfig())),
                    plugin.getChatConfig().getBoolean("mute-command-block-strip-namespace", true));
            policy = cached;
        }
        return cached;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.isFeatureActive("mute-command-block")) {
            return;
        }

        Player player = event.getPlayer();

        if (!plugin.getMuteManager().isPlayerMuted(player)) {
            return;
        }

        Policy current = policy();
        if (!MuteCommandBlockPolicy.isBlocked(event.getMessage(), current.blockedCommands(), current.stripNamespace())) {
            return;
        }

        event.setCancelled(true);

        player.sendMessage(plugin.getModelManager().getCurrentModel().onMuteCommandBlocked());
    }
}

package org.leng.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.leng.Lengbanlist;

import java.io.File;
import java.util.List;

public class MuteCommandBlockListener implements Listener {
    private final Lengbanlist plugin;

    public MuteCommandBlockListener(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.isFeatureEnabled("mute-command-block")) {
            return;
        }

        Player player = event.getPlayer();

        if (!plugin.getMuteManager().isPlayerMuted(player)) {
            return;
        }

        MuteCommandBlockPolicy.migrateLegacyConfig(
                plugin.getChatConfig(), plugin.getConfig(),
                new File(plugin.getDataFolder(), "chatconfig.yml"));

        List<String> blockedCommands = MuteCommandBlockPolicy.resolveBlockedCommands(
                plugin.getChatConfig(), plugin.getConfig());
        boolean stripNamespace = plugin.getChatConfig().getBoolean("mute-command-block-strip-namespace", true);
        if (!MuteCommandBlockPolicy.isBlocked(event.getMessage(), blockedCommands, stripNamespace)) {
            return;
        }

        event.setCancelled(true);
        // noinspection AccessStaticViaInstance
        player.sendMessage(plugin.getModelManager().getCurrentModel().onMuteCommandBlocked());
    }
}

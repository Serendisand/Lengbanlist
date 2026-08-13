package org.leng.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.leng.Lengbanlist;

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

        List<String> blockedCommands = plugin.getConfig().getStringList("mute-blocked-commands");
        if (blockedCommands.isEmpty()) {
            return;
        }

        String message = event.getMessage().trim();
        if (message.isEmpty()) {
            return;
        }
        String firstPart = message.split("\\s+")[0];
        String commandName = firstPart.startsWith("/") ? firstPart.substring(1) : firstPart;

        for (String blocked : blockedCommands) {
            String blockedName = blocked.startsWith("/") ? blocked.substring(1) : blocked;
            if (blockedName.equalsIgnoreCase(commandName)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getModelManager().getCurrentModel().onMuteCommandBlocked());
                return;
            }
        }
    }
}

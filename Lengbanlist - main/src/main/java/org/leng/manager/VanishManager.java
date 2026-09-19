package org.leng.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.leng.Lengbanlist;
import org.leng.utils.SchedulerUtils;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VanishManager {

    public static final String PERMISSION_USE = "lengbanlist.vanish";
    public static final String PERMISSION_SEE = "lengbanlist.vanish.see";

    private final Lengbanlist plugin;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(Player player) {
        return player != null && vanished.contains(player.getUniqueId());
    }

    public boolean canSee(Player viewer) {
        return viewer != null && viewer.hasPermission(PERMISSION_SEE);
    }

    public int count() {
        return vanished.size();
    }

    public boolean toggle(Player player) {
        if (isVanished(player)) {
            unvanish(player);
            return false;
        }
        vanish(player);
        return true;
    }

    public void vanish(Player player) {
        vanished.add(player.getUniqueId());
        setListed(player, false);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId()) || canSee(viewer)) {
                continue;
            }
            hide(viewer, player);
        }
    }

    public void unvanish(Player player) {
        vanished.remove(player.getUniqueId());
        setListed(player, true);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            show(viewer, player);
        }
    }

    public void handleJoin(Player joiner) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(joiner.getUniqueId())) {
                continue;
            }
            if (isVanished(online) && !canSee(joiner)) {
                hide(joiner, online);
            }
        }
        if (isVanished(joiner)) {
            vanish(joiner);
        }
    }

    public boolean handleQuit(Player player) {
        return isVanished(player);
    }

    public void restoreAll() {
        for (Player vanishedPlayer : Bukkit.getOnlinePlayers()) {
            if (!isVanished(vanishedPlayer)) {
                continue;
            }
            setListed(vanishedPlayer, true);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(vanishedPlayer.getUniqueId())) {
                    continue;
                }
                show(viewer, vanishedPlayer);
            }
        }
        vanished.clear();
    }

    private void hide(Player viewer, Player target) {
        SchedulerUtils.runTask(plugin, viewer, () -> {
            if (viewer.isOnline() && target.isOnline()) {
                viewer.hidePlayer(plugin, target);
            }
        });
    }

    private void show(Player viewer, Player target) {
        SchedulerUtils.runTask(plugin, viewer, () -> {
            if (viewer.isOnline() && target.isOnline()) {
                viewer.showPlayer(plugin, target);
            }
        });
    }

    private static Method setListedMethod;
    private static boolean setListedResolved;

    private void setListed(Player player, boolean listed) {
        if (!setListedResolved) {
            setListedResolved = true;
            try {
                setListedMethod = Player.class.getMethod("setListed", boolean.class);
            } catch (NoSuchMethodException e) {
                setListedMethod = null;
            }
        }
        if (setListedMethod == null) {
            return;
        }
        SchedulerUtils.runTask(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            try {
                setListedMethod.invoke(player, listed);
            } catch (Exception ignored) {
            }
        });
    }
}

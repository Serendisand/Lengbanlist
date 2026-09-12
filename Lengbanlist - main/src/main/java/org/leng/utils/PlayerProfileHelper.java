package org.leng.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class PlayerProfileHelper {

    private PlayerProfileHelper() {}

    private static final ConcurrentHashMap<String, OfflinePlayer> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000L;
    private static final ConcurrentHashMap<String, Long> cacheTime = new ConcurrentHashMap<>();

    public static OfflinePlayer lookupSync(String name) {
        if (name == null || name.isEmpty()) return null;

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        OfflinePlayer cached = cache.get(name);
        Long t = cacheTime.get(name);
        if (cached != null && t != null && System.currentTimeMillis() - t < CACHE_TTL_MS) {
            return cached;
        }

        try {
            OfflinePlayer fresh = Bukkit.getOfflinePlayer(name);
            if (fresh != null && (fresh.hasPlayedBefore() || fresh.isOnline())) {
                cache.put(name, fresh);
                cacheTime.put(name, System.currentTimeMillis());
                return fresh;
            }
        } catch (UnsupportedOperationException ex) {

            return null;
        } catch (Exception ex) {

            return null;
        }
        return null;
    }

    public static CompletableFuture<OfflinePlayer> lookupAsync(String name) {
        CompletableFuture<OfflinePlayer> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(LengbanlistAccessor.getPlugin(), () -> {
            OfflinePlayer result = lookupSync(name);
            future.complete(result);
        });
        return future.orTimeout(5, TimeUnit.SECONDS).exceptionally(ex -> null);
    }

    public static void lookupAsync(String name, Consumer<OfflinePlayer> onResult) {
        lookupAsync(name).thenAccept(p ->
            SchedulerUtils.runTask(LengbanlistAccessor.getPlugin(), () -> onResult.accept(p)));
    }

    public static OfflinePlayer lookupByUuid(UUID uuid) {
        if (uuid == null) return null;
        try {
            return Bukkit.getOfflinePlayer(uuid);
        } catch (UnsupportedOperationException ex) {

            return null;
        }
    }

    public static void clearCache() {
        cache.clear();
        cacheTime.clear();
    }

    private static final class LengbanlistAccessor {
        private static org.leng.Lengbanlist plugin;
        static {
            try {

                org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("Lengbanlist");
                if (p instanceof org.leng.Lengbanlist) {
                    plugin = (org.leng.Lengbanlist) p;
                }
            } catch (Exception ignored) {}
        }

        static org.leng.Lengbanlist getPlugin() {
            if (plugin == null) {
                org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin("Lengbanlist");
                if (p instanceof org.leng.Lengbanlist) {
                    plugin = (org.leng.Lengbanlist) p;
                }
            }
            return plugin;
        }
    }
}

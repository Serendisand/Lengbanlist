package org.leng.manager;

import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;
import org.leng.utils.IpMatcher;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;


public class MuteManager {
    private static final int MAX_RELOAD_ATTEMPTS = 3;

    private final Lengbanlist plugin;
    private final DatabaseManager db;
    private final Map<String, Long> muteCache = new ConcurrentHashMap<>();
    private final Map<String, Long> ipMuteCache = new ConcurrentHashMap<>();
    private final Object muteLock = new Object();
    private final Object reloadLock = new Object();
    private long mutationGeneration;

    public MuteManager(Lengbanlist plugin) throws SQLException {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        if (!reloadMuteCacheOrThrow()) {
            throw new SQLException("初始禁言缓存在并发变更中无法加载");
        }
    }

    public void mutePlayer(MuteEntry muteEntry) {
        synchronized (muteLock) {
            String target = muteEntry.getTarget();
            if (isPlayerMuted(target) || (IpMatcher.isIpv4(target) && hasEquivalentIpv4Mute(target))) {
                return;
            }
            db.upsertMute(muteEntry);
            muteCache.put(target, muteEntry.getTime());
            if (isIpTarget(target)) {
                ipMuteCache.put(target, muteEntry.getTime());
            }
            mutationGeneration++;
            plugin.getAuditManager().log("禁言", muteEntry.getStaff(), target, muteEntry.getReason());
        }
    }

    public void unmutePlayer(String target) {
        unmutePlayer(target, null);
    }

    public void unmutePlayer(String target, String actor) {
        boolean wasMuted;
        List<String> targetsToDelete;
        synchronized (muteLock) {
            List<String> storedTargets = storedTargetsFor(target);
            targetsToDelete = new ArrayList<>(storedTargets);
            wasMuted = false;
            for (String storedTarget : storedTargets) {
                Long cached = muteCache.get(storedTarget);
                if (cached != null && isActive(cached)) {
                    wasMuted = true;
                } else {
                    MuteEntry storedEntry = db.getMute(storedTarget);
                    if (storedEntry != null && isActive(storedEntry.getTime())) {
                        wasMuted = true;
                    }
                }
                muteCache.remove(storedTarget);
                ipMuteCache.remove(storedTarget);
            }
            mutationGeneration++;
            if (wasMuted) {
                plugin.getAuditManager().log("解除禁言", actor, target, "");
            }
        }
        for (String storedTarget : targetsToDelete) {
            db.deleteMute(storedTarget);
        }
    }

    public void clearMuteCache() {
        synchronized (muteLock) {
            muteCache.clear();
            ipMuteCache.clear();
            mutationGeneration++;
        }
    }

    public boolean reloadMuteCache() {
        try {
            boolean reloaded = reloadMuteCacheOrThrow();
            if (!reloaded) {
                plugin.getLogger().warning("刷新禁言缓存失败：加载期间缓存持续变更");
            }
            return reloaded;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "刷新禁言缓存失败，将保留现有缓存", e);
            return false;
        }
    }

    private boolean reloadMuteCacheOrThrow() throws SQLException {
        synchronized (reloadLock) {
            return loadStableMuteSnapshot();
        }
    }

    private boolean loadStableMuteSnapshot() throws SQLException {
        for (int attempt = 0; attempt < MAX_RELOAD_ATTEMPTS; attempt++) {
            long generationBeforeLoad;
            synchronized (muteLock) {
                generationBeforeLoad = mutationGeneration;
            }

            Map<String, Long> loadedMutes = new HashMap<>();
            Map<String, Long> loadedIpMutes = new HashMap<>();
            long now = System.currentTimeMillis();
            for (MuteEntry entry : db.loadMutesForCache()) {
                if (entry.getTime() == Long.MAX_VALUE || entry.getTime() > now) {
                    loadedMutes.put(entry.getTarget(), entry.getTime());
                    if (isIpTarget(entry.getTarget())) {
                        loadedIpMutes.put(entry.getTarget(), entry.getTime());
                    }
                } else {
                    db.deleteMuteIfExpiresAt(entry.getTarget(), entry.getTime());
                }
            }

            synchronized (muteLock) {
                if (mutationGeneration != generationBeforeLoad) {
                    continue;
                }
                muteCache.clear();
                muteCache.putAll(loadedMutes);
                ipMuteCache.clear();
                ipMuteCache.putAll(loadedIpMutes);
                mutationGeneration++;
                return true;
            }
        }
        return false;
    }

    public List<MuteEntry> getMuteList() {
        return db.getMutes();
    }

    public boolean isPlayerMuted(String playerName) {
        synchronized (muteLock) {
            Long cached = muteCache.get(playerName);
            if (cached != null) {
                if (cached == Long.MAX_VALUE || cached > System.currentTimeMillis()) {
                    return true;
                }
                muteCache.remove(playerName, cached);
                db.deleteMuteIfExpiresAt(playerName, cached);
                mutationGeneration++;
                return false;
            }
            MuteEntry entry = db.getMute(playerName);
            if (entry == null) return false;
            if (entry.getTime() == Long.MAX_VALUE || entry.getTime() > System.currentTimeMillis()) {
                muteCache.put(playerName, entry.getTime());
                return true;
            }
            db.deleteMuteIfExpiresAt(playerName, entry.getTime());
            mutationGeneration++;
            return false;
        }
    }

    public boolean isIpMuted(String ip) {
        synchronized (muteLock) {
            if (ip == null) return false;
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : ipMuteCache.entrySet()) {
                String target = entry.getKey();
                Long time = entry.getValue();
                boolean matches = IpMatcher.isIpv4(target)
                        ? sameIpv4(ip, target)
                        : IpMatcher.cidrMatches(ip, target);
                if (matches) {
                    if (time == Long.MAX_VALUE || time > now) {
                        return true;
                    }
                    ipMuteCache.remove(target, time);
                    muteCache.remove(target, time);
                    db.deleteMuteIfExpiresAt(target, time);
                    mutationGeneration++;
                }
            }
            return false;
        }
    }

    private static boolean isIpTarget(String target) {
        return IpMatcher.isIpv4(target) || IpMatcher.isCidr(target);
    }

    private boolean hasEquivalentIpv4Mute(String target) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : ipMuteCache.entrySet()) {
            String storedTarget = entry.getKey();
            Long time = entry.getValue();
            if (!IpMatcher.isIpv4(storedTarget) || !sameIpv4(target, storedTarget)) {
                continue;
            }
            if (time == Long.MAX_VALUE || time > now) {
                return true;
            }
            ipMuteCache.remove(storedTarget, time);
            muteCache.remove(storedTarget, time);
            db.deleteMuteIfExpiresAt(storedTarget, time);
            mutationGeneration++;
        }
        return false;
    }

    private List<String> storedTargetsFor(String target) {
        List<String> storedTargets = new ArrayList<>();
        if (IpMatcher.isIpv4(target)) {
            for (String storedTarget : ipMuteCache.keySet()) {
                if (sameIpv4(target, storedTarget)) {
                    storedTargets.add(storedTarget);
                }
            }
        }
        if (storedTargets.isEmpty()) {
            storedTargets.add(target);
        }
        return storedTargets;
    }

    private static boolean sameIpv4(String first, String second) {
        return IpMatcher.isIpv4(first)
                && IpMatcher.isIpv4(second)
                && IpMatcher.ipToLong(first) == IpMatcher.ipToLong(second);
    }

    private static boolean isActive(long endTime) {
        return endTime == Long.MAX_VALUE || endTime > System.currentTimeMillis();
    }

    public boolean isPlayerMuted(org.bukkit.entity.Player player) {
        if (isPlayerMuted(player.getName())) return true;
        if (player.getAddress() != null) {
            String ip = player.getAddress().getAddress().getHostAddress();
            return isIpMuted(ip);
        }
        return false;
    }
}

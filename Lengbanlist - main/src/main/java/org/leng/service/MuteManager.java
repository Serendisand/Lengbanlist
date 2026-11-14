package org.leng.service;

import org.leng.Lengbanlist;
import org.leng.object.MuteEntry;
import org.leng.object.PlayerIdentity;
import org.leng.util.IpMatcher;
import org.leng.util.SchedulerUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.leng.storage.DatabaseManager;

public class MuteManager {
    private static final int MAX_RELOAD_ATTEMPTS = 3;

    /**
     * 未禁言结果的短缓存。聊天和命令预处理都会走 {@link #isPlayerMuted}，
     * 没有这层缓存时每个未禁言玩家每说一句话、敲一条命令都要查一次库。
     *
     * <p>本地禁言/解禁和跨服事件都会主动清掉对应键，所以最坏只是 5 秒内
     * 看不到别处刚发生的变更，不会把正在生效的禁言长期漏掉。
     */
    private static final long MISS_TTL_MS = 5_000L;
    private static final int MISS_CACHE_LIMIT = 4096;

    private final Lengbanlist plugin;
    private final DatabaseManager db;
    private final Map<String, Long> muteCache = new ConcurrentHashMap<>();
    private final Map<String, Long> ipMuteCache = new ConcurrentHashMap<>();
    private final Map<String, Long> missCache = new ConcurrentHashMap<>();
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

    public Long mutePlayer(MuteEntry muteEntry) {
        synchronized (muteLock) {
            String target = muteEntry.getTarget().toLowerCase(Locale.ROOT);
            Long existing = existingActiveMute(target);
            if (existing != null) {
                if (existing.equals(muteEntry.getTime())) {
                    return null; 
                }
                db.upsertMute(muteEntry);
                muteCache.put(target, muteEntry.getTime());
                missCache.remove(target);
                if (isIpTarget(target)) {
                    ipMuteCache.put(target, muteEntry.getTime());
                }
                mutationGeneration++;
                plugin.getAuditManager().log("修改禁言", muteEntry.getStaff(), muteEntry.getTarget(), muteEntry.getReason());
                SchedulerUtils.runSync(plugin, () -> org.bukkit.Bukkit.getPluginManager()
                        .callEvent(new org.leng.api.events.LengbanlistMuteEvent(muteEntry)));
                return muteEntry.getTime();
            }
            db.upsertMute(muteEntry);
            muteCache.put(target, muteEntry.getTime());
            missCache.remove(target);
            if (isIpTarget(target)) {
                ipMuteCache.put(target, muteEntry.getTime());
            }
            mutationGeneration++;
            plugin.getAuditManager().log("禁言", muteEntry.getStaff(), muteEntry.getTarget(), muteEntry.getReason());
            SchedulerUtils.runSync(plugin, () -> org.bukkit.Bukkit.getPluginManager()
                    .callEvent(new org.leng.api.events.LengbanlistMuteEvent(muteEntry)));
            return muteEntry.getTime();
        }
    }

    private Long existingActiveMute(String target) {
        Long cached = muteCache.get(target);
        if (cached != null) {
            if (cached == Long.MAX_VALUE || cached > System.currentTimeMillis()) {
                return cached;
            }
            muteCache.remove(target, cached);
            ipMuteCache.remove(target, cached);
            db.deleteMuteIfExpiresAt(target, cached);
            mutationGeneration++;
            return null;
        }
        if (IpMatcher.isIp(target) && hasEquivalentIpMute(target)) {
            return Long.MAX_VALUE;
        }
        MuteEntry entry = db.getMute(identityOf(target));
        if (entry == null) return null;
        if (entry.getTime() == Long.MAX_VALUE || entry.getTime() > System.currentTimeMillis()) {
            muteCache.put(target, entry.getTime());
            return entry.getTime();
        }
        db.deleteMuteIfExpiresAt(entry.getTarget().toLowerCase(Locale.ROOT), entry.getTime());
        mutationGeneration++;
        return null;
    }

    public void unmutePlayer(String target) {
        unmutePlayer(target, null);
    }

    public void unmutePlayer(String target, String actor) {
        synchronized (muteLock) {
            List<String> storedTargets = storedTargetsFor(target);
            boolean wasMuted = false;
            for (String storedTarget : storedTargets) {
                String cacheKey = storedTarget.toLowerCase(Locale.ROOT);
                Long cached = muteCache.get(cacheKey);
                if (cached != null && isActive(cached)) {
                    wasMuted = true;
                } else {
                    MuteEntry storedEntry = db.getMute(cacheKey);
                    if (storedEntry != null && isActive(storedEntry.getTime())) {
                        wasMuted = true;
                    }
                }
                muteCache.remove(cacheKey);
                ipMuteCache.remove(storedTarget);
                missCache.remove(cacheKey);

                db.deleteMute(cacheKey);
            }
            mutationGeneration++;
            if (wasMuted) {
                plugin.getAuditManager().log("解除禁言", actor, target, "");
                SchedulerUtils.runSync(plugin, () -> org.bukkit.Bukkit.getPluginManager()
                        .callEvent(new org.leng.api.events.LengbanlistUnmuteEvent(target, actor == null ? "System" : actor)));
            }
        }
    }

    public boolean unmutePlayerIfMuted(String target, String actor) {
        synchronized (muteLock) {
            if (!hasActiveMuteLocked(target)) {
                return false;
            }
        }
        unmutePlayer(target, actor);
        return true;
    }

    private boolean hasActiveMuteLocked(String target) {
        String key = target.toLowerCase(Locale.ROOT);
        Long cached = muteCache.get(key);
        if (cached != null) {
            return isActive(cached);
        }
        MuteEntry entry = db.getMute(identityOf(key));
        return entry != null && isActive(entry.getTime());
    }

    public void invalidate(String target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        String key = target.toLowerCase(Locale.ROOT);
        synchronized (muteLock) {
            muteCache.remove(key);
            ipMuteCache.remove(key);
            missCache.remove(key);
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
            org.leng.util.ErrorLog.record(plugin, "刷新禁言缓存失败，将保留现有缓存", e);
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
                    String targetKey = entry.getTarget().toLowerCase();
                    loadedMutes.put(targetKey, entry.getTime());
                    if (isIpTarget(targetKey)) {
                        loadedIpMutes.put(targetKey, entry.getTime());
                    }
                } else {
                    db.deleteMuteIfExpiresAt(entry.getTarget().toLowerCase(), entry.getTime());
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
                missCache.clear();
                mutationGeneration++;
                return true;
            }
        }
        return false;
    }

    public List<MuteEntry> getMuteList() {
        return db.getMutes();
    }

    public int countActiveMutes() {
        return db.countActiveMutes();
    }

    public Long getActiveMuteEndTime(String target) {
        if (target == null) {
            return null;
        }
        String normalized = target.toLowerCase(Locale.ROOT);
        Long expired = null;
        synchronized (muteLock) {
            Long cached = muteCache.get(normalized);
            if (cached != null) {
                if (cached == Long.MAX_VALUE || cached > System.currentTimeMillis()) {
                    return cached;
                }
                muteCache.remove(normalized, cached);
                ipMuteCache.remove(normalized, cached);
                expired = cached;
            }
        }
        if (expired != null) {
            db.deleteMuteIfExpiresAt(normalized, expired);
            markMutated();
        }
        if (isKnownUnmuted(normalized)) {
            return null;
        }
        MuteEntry entry = db.getMute(identityOf(normalized));
        if (entry == null) {
            rememberUnmuted(normalized);
            return null;
        }
        long endTime = entry.getTime();
        if (endTime == Long.MAX_VALUE || endTime > System.currentTimeMillis()) {
            synchronized (muteLock) {
                muteCache.put(normalized, endTime);
            }
            return endTime;
        }
        db.deleteMuteIfExpiresAt(entry.getTarget().toLowerCase(Locale.ROOT), endTime);
        markMutated();
        rememberUnmuted(normalized);
        return null;
    }

    public boolean isPlayerMuted(String playerName) {
        if (playerName == null) {
            return false;
        }
        String normalized = playerName.toLowerCase(Locale.ROOT);
        Long expired = null;
        synchronized (muteLock) {
            Long cached = muteCache.get(normalized);
            if (cached != null) {
                if (cached == Long.MAX_VALUE || cached > System.currentTimeMillis()) {
                    return true;
                }
                muteCache.remove(normalized, cached);
                ipMuteCache.remove(normalized, cached);
                expired = cached;
            }
        }
        if (expired != null) {
            db.deleteMuteIfExpiresAt(normalized, expired);
            markMutated();
        }
        if (isKnownUnmuted(normalized)) {
            return false;
        }
        MuteEntry entry = db.getMute(identityOf(normalized));
        if (entry == null) {
            rememberUnmuted(normalized);
            return false;
        }
        long endTime = entry.getTime();
        if (endTime == Long.MAX_VALUE || endTime > System.currentTimeMillis()) {
            synchronized (muteLock) {
                muteCache.put(normalized, endTime);
            }
            return true;
        }
        db.deleteMuteIfExpiresAt(entry.getTarget().toLowerCase(Locale.ROOT), endTime);
        markMutated();
        rememberUnmuted(normalized);
        return false;
    }

    public boolean isIpMuted(String ip) {
        if (ip == null || ipMuteCache.isEmpty()) {
            return false;
        }
        synchronized (muteLock) {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : ipMuteCache.entrySet()) {
                String target = entry.getKey();
                Long time = entry.getValue();
                boolean matches = IpMatcher.isCidrLike(target)
                        ? IpMatcher.cidrMatches(ip, target)
                        : IpMatcher.sameIp(ip, target);
                if (matches) {
                    if (time == Long.MAX_VALUE || time > now) {
                        return true;
                    }
                    ipMuteCache.remove(target, time);
                    muteCache.remove(target, time);
                    missCache.remove(target);
                    db.deleteMuteIfExpiresAt(target, time);
                    mutationGeneration++;
                }
            }
            return false;
        }
    }

    private boolean isKnownUnmuted(String key) {
        Long until = missCache.get(key);
        if (until == null) {
            return false;
        }
        if (until > System.currentTimeMillis()) {
            return true;
        }
        missCache.remove(key, until);
        return false;
    }

    private void rememberUnmuted(String key) {
        long now = System.currentTimeMillis();
        if (missCache.size() >= MISS_CACHE_LIMIT) {
            missCache.entrySet().removeIf(entry -> entry.getValue() <= now);
            if (missCache.size() >= MISS_CACHE_LIMIT) {
                missCache.clear();
            }
        }
        missCache.put(key, now + MISS_TTL_MS);
    }

    private void markMutated() {
        synchronized (muteLock) {
            mutationGeneration++;
        }
    }

    private static boolean isIpTarget(String target) {
        return IpMatcher.isIp(target) || IpMatcher.isCidrLike(target);
    }

    private boolean hasEquivalentIpMute(String target) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : ipMuteCache.entrySet()) {
            String storedTarget = entry.getKey();
            Long time = entry.getValue();
            if (!IpMatcher.sameIp(target, storedTarget)) {
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

    private PlayerIdentity identityOf(String target) {
        if (target == null || target.isEmpty()) {
            return PlayerIdentity.EMPTY;
        }
        String value = target.trim();
        if (isIpTarget(value) || IpMatcher.looksLikeIp(value)) {
            return PlayerIdentity.ofName(value);
        }
        return db.getIdentityResolver().resolve(value);
    }

    private List<String> storedTargetsFor(String target) {
        List<String> storedTargets = new ArrayList<>();
        if (IpMatcher.isIp(target)) {
            for (String storedTarget : ipMuteCache.keySet()) {
                if (IpMatcher.sameIp(target, storedTarget)) {
                    storedTargets.add(storedTarget);
                }
            }
        } else if (IpMatcher.isCidrLike(target)) {
            for (String storedTarget : ipMuteCache.keySet()) {
                boolean matches;
                if (IpMatcher.isIp(storedTarget)) {
                    matches = IpMatcher.cidrMatches(storedTarget, target);
                } else if (IpMatcher.isCidrLike(storedTarget)) {
                    String storedBase = storedTarget.substring(0, storedTarget.indexOf('/'));
                    matches = IpMatcher.cidrMatches(storedBase, target);
                } else {
                    matches = false;
                }
                if (matches) {
                    storedTargets.add(storedTarget);
                }
            }
        } else {
            PlayerIdentity identity = identityOf(target);
            storedTargets.addAll(identity.lowerNames());
            for (String stored : db.getMuteTargets(identity)) {
                String key = stored.toLowerCase(Locale.ROOT);
                if (!storedTargets.contains(key)) {
                    storedTargets.add(key);
                }
            }
        }
        if (storedTargets.isEmpty()) {
            storedTargets.add(target);
        }
        return storedTargets;
    }

    private static boolean isActive(long endTime) {
        return endTime == Long.MAX_VALUE || endTime > System.currentTimeMillis();
    }

    public boolean isPlayerMuted(org.bukkit.entity.Player player) {
        if (isPlayerMuted(player.getName())) return true;
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            String ip = player.getAddress().getAddress().getHostAddress();
            return isIpMuted(ip);
        }
        return false;
    }
}

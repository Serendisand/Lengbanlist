package org.leng.manager;

import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BanCache {

    interface Loader {

        List<BanEntry> loadActiveBans();

        List<BanIpEntry> loadActiveIpBans();
    }

    static final class LoadFailure extends RuntimeException {
        LoadFailure(Throwable cause) {
            super(cause);
        }
    }

    static final long FAILURE_RETRY_MILLIS = 1000L;

    private static final class Snapshot {
        final Map<String, BanEntry> bansByTarget;
        final Map<String, BanIpEntry> ipBansByIp;
        final List<BanIpEntry> ipBans;
        final List<BanEntry> allActiveBans;

        Snapshot(Map<String, BanEntry> bansByTarget, Map<String, BanIpEntry> ipBansByIp,
                 List<BanIpEntry> ipBans, List<BanEntry> allActiveBans) {
            this.bansByTarget = bansByTarget;
            this.ipBansByIp = ipBansByIp;
            this.ipBans = ipBans;
            this.allActiveBans = allActiveBans;
        }
    }

    private static final Snapshot EMPTY = new Snapshot(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());

    private static final Comparator<BanEntry> BY_TARGET =
            Comparator.comparing(BanEntry::getTarget, String.CASE_INSENSITIVE_ORDER);

    private final Loader loader;
    private final Logger logger;
    private final Object refreshLock = new Object();

    private volatile Snapshot snapshot = EMPTY;
    private volatile long loadedAt;
    private volatile long ttlMillis;
    private volatile boolean degraded;

    BanCache(Loader loader, long ttlMillis, Logger logger) {
        this.loader = loader;
        this.logger = logger;
        setTtlMillis(ttlMillis);
    }

    void setTtlMillis(long ttlMillis) {

        this.ttlMillis = Math.max(1000L, ttlMillis);
    }

    long getTtlMillis() {
        return ttlMillis;
    }

    void invalidate() {
        synchronized (refreshLock) {
            loadedAt = 0L;
        }
    }

    void reload() {
        invalidate();
        ensureFresh();
    }

    private void ensureFresh() {
        long stamp = loadedAt;
        if (stamp != 0L && System.currentTimeMillis() - stamp < ttlMillis) {
            return;
        }
        synchronized (refreshLock) {

            stamp = loadedAt;
            if (stamp != 0L && System.currentTimeMillis() - stamp < ttlMillis) {
                return;
            }
            try {
                snapshot = build();
                loadedAt = System.currentTimeMillis();
                if (degraded) {
                    degraded = false;
                    logger.info("封禁缓存已恢复刷新，重新以数据库结果为准。");
                }
            } catch (RuntimeException e) {

                scheduleRetryIn(FAILURE_RETRY_MILLIS);
                if (!degraded) {
                    degraded = true;
                    logger.log(Level.WARNING, "封禁缓存刷新失败，沿用上一次快照继续拦截封禁玩家，"
                            + FAILURE_RETRY_MILLIS + " 毫秒后重试。", e.getCause() == null ? e : e.getCause());
                }
            }
        }
    }

    private void scheduleRetryIn(long millis) {
        loadedAt = System.currentTimeMillis() + millis - ttlMillis;
    }

    private Snapshot build() {
        List<BanEntry> bans = loader.loadActiveBans();
        List<BanIpEntry> ipBans = loader.loadActiveIpBans();

        Map<String, BanEntry> byTarget = new HashMap<>(Math.max(16, bans.size() * 2));
        for (BanEntry ban : bans) {
            String key = normalize(ban.getTarget());
            BanEntry existing = byTarget.get(key);

            if (existing == null || ban.getTime() > existing.getTime()) {
                byTarget.put(key, ban);
            }
        }

        Map<String, BanIpEntry> byIp = new HashMap<>(Math.max(16, ipBans.size() * 2));
        for (BanIpEntry ban : ipBans) {
            BanIpEntry existing = byIp.get(ban.getIp());
            if (existing == null || ban.getTime() > existing.getTime()) {
                byIp.put(ban.getIp(), ban);
            }
        }

        return new Snapshot(byTarget, byIp, new ArrayList<>(ipBans), new ArrayList<>(bans));
    }

    private static String normalize(String target) {
        return target.toLowerCase(Locale.ROOT);
    }

    List<BanEntry> getActiveBans() {
        ensureFresh();
        List<BanEntry> result = new ArrayList<>(snapshot.allActiveBans);
        result.sort(BY_TARGET);
        return result;
    }

    List<BanEntry> getUnexpiredBans() {
        ensureFresh();
        long now = System.currentTimeMillis();
        List<BanEntry> result = new ArrayList<>();
        for (BanEntry ban : snapshot.bansByTarget.values()) {
            if (ban.getTime() > now) {
                result.add(ban);
            }
        }
        result.sort(BY_TARGET);
        return result;
    }

    BanEntry getBan(String target) {
        if (target == null) {
            return null;
        }
        ensureFresh();
        return snapshot.bansByTarget.get(normalize(target));
    }

    boolean isBanned(String target) {
        BanEntry ban = getBan(target);
        return ban != null && ban.getTime() > System.currentTimeMillis();
    }

    List<BanIpEntry> getUnexpiredIpBans() {
        ensureFresh();
        long now = System.currentTimeMillis();
        List<BanIpEntry> result = new ArrayList<>();
        for (BanIpEntry ban : snapshot.ipBans) {
            if (ban.getTime() > now) {
                result.add(ban);
            }
        }
        result.sort(Comparator.comparing(BanIpEntry::getIp));
        return result;
    }

    BanIpEntry getIpBan(String ip) {
        if (ip == null) {
            return null;
        }
        ensureFresh();
        return snapshot.ipBansByIp.get(ip);
    }

    boolean isIpBanned(String ip) {
        BanIpEntry ban = getIpBan(ip);
        return ban != null && ban.getTime() > System.currentTimeMillis();
    }

    int countUnexpiredBans() {
        ensureFresh();
        long now = System.currentTimeMillis();
        int count = 0;
        for (BanEntry ban : snapshot.bansByTarget.values()) {
            if (ban.getTime() > now) {
                count++;
            }
        }
        return count;
    }

    int countUnexpiredIpBans() {
        ensureFresh();
        long now = System.currentTimeMillis();
        int count = 0;
        for (BanIpEntry ban : snapshot.ipBans) {
            if (ban.getTime() > now) {
                count++;
            }
        }
        return count;
    }
}

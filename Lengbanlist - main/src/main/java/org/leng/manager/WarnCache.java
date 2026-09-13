package org.leng.manager;

import org.leng.object.WarnEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

final class WarnCache {

    interface Loader {

        List<WarnEntry> loadWarns(String player);
    }

    static final class LoadFailure extends RuntimeException {
        LoadFailure(Throwable cause) {
            super(cause);
        }
    }

    static final int MAX_CACHED_PLAYERS = 512;

    static final long FAILURE_RETRY_MILLIS = 1000L;

    private static final class Entry {
        final List<WarnEntry> warns;
        volatile long nextRefreshAt;

        Entry(List<WarnEntry> warns, long nextRefreshAt) {
            this.warns = warns;
            this.nextRefreshAt = nextRefreshAt;
        }
    }

    private final Loader loader;
    private final Logger logger;
    private final Map<String, Entry> entries = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > MAX_CACHED_PLAYERS;
        }
    };

    private volatile long ttlMillis;
    private volatile boolean degraded;

    WarnCache(Loader loader, long ttlMillis, Logger logger) {
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

    List<WarnEntry> get(String player, boolean activeOnly) {
        if (player == null || player.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String key = normalize(player);
        Entry entry = fresh(key);
        if (entry == null) {
            entry = load(key, peek(key));
        }
        return filter(entry.warns, activeOnly);
    }

    void invalidate(String player) {
        if (player == null) {
            return;
        }
        markStale(normalize(player));
    }

    void invalidateAll() {
        synchronized (entries) {
            for (Entry entry : entries.values()) {
                entry.nextRefreshAt = 0L;
            }
        }
    }

    private void markStale(String key) {
        synchronized (entries) {
            Entry entry = entries.get(key);
            if (entry != null) {
                entry.nextRefreshAt = 0L;
            }
        }
    }

    private Entry fresh(String key) {
        synchronized (entries) {
            Entry entry = entries.get(key);
            if (entry == null || System.currentTimeMillis() >= entry.nextRefreshAt) {
                return null;
            }
            return entry;
        }
    }

    private Entry peek(String key) {
        synchronized (entries) {
            return entries.get(key);
        }
    }

    private Entry load(String key, Entry previous) {
        try {
            Entry fresh = new Entry(List.copyOf(loader.loadWarns(key)),
                    System.currentTimeMillis() + ttlMillis);
            synchronized (entries) {
                entries.put(key, fresh);
            }
            if (degraded) {
                degraded = false;
                logger.info("警告缓存已恢复刷新，重新以数据库结果为准。");
            }
            return fresh;
        } catch (RuntimeException e) {
            Entry fallback = previous == null
                    ? new Entry(new ArrayList<>(), 0L)
                    : previous;
            fallback.nextRefreshAt = System.currentTimeMillis() + FAILURE_RETRY_MILLIS;
            synchronized (entries) {
                entries.putIfAbsent(key, fallback);
            }
            if (!degraded) {
                degraded = true;
                logger.log(Level.WARNING, "警告查询失败，暂用上一次结果继续判定，"
                        + FAILURE_RETRY_MILLIS + " 毫秒后重试。", e.getCause() == null ? e : e.getCause());
            }
            return fallback;
        }
    }

    private static List<WarnEntry> filter(List<WarnEntry> warns, boolean activeOnly) {
        List<WarnEntry> result = new ArrayList<>(warns.size());
        for (WarnEntry warn : warns) {
            if (!activeOnly || !warn.isRevoked()) {
                result.add(warn);
            }
        }
        return result;
    }

    private static String normalize(String player) {
        return player.trim().toLowerCase(Locale.ROOT);
    }
}

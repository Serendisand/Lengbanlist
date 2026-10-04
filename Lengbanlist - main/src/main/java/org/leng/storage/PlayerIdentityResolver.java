package org.leng.storage;

import org.leng.object.PlayerIdentity;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class PlayerIdentityResolver {

    static final long TTL_MILLIS = 300_000L;

    static final int MAX_ENTRIES = 4096;

    private static final class Cached {
        final PlayerIdentity identity;
        final long expiresAt;

        Cached(PlayerIdentity identity, long expiresAt) {
            this.identity = identity;
            this.expiresAt = expiresAt;
        }
    }

    private final DatabaseManager db;

    private final Map<String, Cached> cache = new LinkedHashMap<String, Cached>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    PlayerIdentityResolver(DatabaseManager db) {
        this.db = db;
    }

    public PlayerIdentity resolve(String nameOrUuid) {
        String key = normalize(nameOrUuid);
        if (key.isEmpty()) {
            return PlayerIdentity.EMPTY;
        }
        synchronized (cache) {
            Cached cached = cache.get(key);
            if (cached != null && System.currentTimeMillis() < cached.expiresAt) {
                return cached.identity;
            }
        }
        PlayerIdentity identity = db.resolveIdentity(nameOrUuid);
        if (identity == null) {
            identity = PlayerIdentity.ofName(nameOrUuid);
        }
        synchronized (cache) {
            cache.put(key, new Cached(identity, System.currentTimeMillis() + TTL_MILLIS));
        }
        return identity;
    }

    public void record(String uuid, String name, long time) {
        db.recordIdentity(uuid, name, time);
        String id = normalize(uuid);
        synchronized (cache) {
            cache.remove(normalize(name));
            if (id.isEmpty()) {
                return;
            }
            cache.remove(id);
            cache.entrySet().removeIf(entry -> id.equals(entry.getValue().identity.uuid()));
        }
    }

    public void invalidate(String nameOrUuid) {
        String key = normalize(nameOrUuid);
        if (key.isEmpty()) {
            return;
        }
        synchronized (cache) {
            cache.remove(key);
        }
    }

    public void invalidateAll() {
        synchronized (cache) {
            cache.clear();
        }
    }

    int cachedEntries() {
        synchronized (cache) {
            return cache.size();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

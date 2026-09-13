package org.leng.manager;

import org.junit.jupiter.api.Test;
import org.leng.object.WarnEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarnCacheTest {

    private static final Logger LOGGER = Logger.getLogger("WarnCacheTest");

    private static WarnEntry warn(String id, boolean revoked) {
        WarnEntry entry = new WarnEntry(id, "Alice", "staff", System.currentTimeMillis(), "原因" + id);
        return revoked ? entry.revoke() : entry;
    }

    private static class Stub implements WarnCache.Loader {
        final AtomicInteger loads = new AtomicInteger();
        final AtomicBoolean failing = new AtomicBoolean();
        final List<WarnEntry> warns = new ArrayList<>(List.of(warn("w1", false), warn("w2", true)));

        @Override
        public List<WarnEntry> loadWarns(String player) {
            loads.incrementAndGet();
            if (failing.get()) {
                throw new WarnCache.LoadFailure(new IllegalStateException("数据库连接已断开"));
            }
            return List.copyOf(warns);
        }
    }

    @Test
    void repeatedReadsWithinTtl_hitDatabaseOnce() {
        Stub loader = new Stub();
        WarnCache cache = new WarnCache(loader, 5000L, LOGGER);

        for (int i = 0; i < 20; i++) {
            cache.get("Alice", true);
            cache.get("Alice", false);
        }
        assertEquals(1, loader.loads.get(), "TTL 内的占位符轮询不应该每次都打数据库");
    }

    @Test
    void playerKeys_areCaseInsensitive() {
        Stub loader = new Stub();
        WarnCache cache = new WarnCache(loader, 5000L, LOGGER);

        assertEquals(1, cache.get("alice", true).size());
        assertEquals(1, cache.get("ALICE", true).size());
        assertEquals(1, cache.get(" Alice ", true).size());
        assertEquals(1, loader.loads.get());
    }

    @Test
    void activeOnly_filtersRevokedWithoutExtraQuery() {
        Stub loader = new Stub();
        WarnCache cache = new WarnCache(loader, 5000L, LOGGER);

        assertEquals(1, cache.get("Alice", true).size());
        assertEquals(2, cache.get("Alice", false).size());
        assertEquals(1, loader.loads.get());
    }

    @Test
    void invalidate_forcesReload() {
        Stub loader = new Stub();
        WarnCache cache = new WarnCache(loader, 5000L, LOGGER);

        cache.get("Alice", true);
        cache.invalidate("aLiCe");
        cache.get("Alice", true);
        assertEquals(2, loader.loads.get());

        cache.invalidateAll();
        cache.get("Alice", true);
        assertEquals(3, loader.loads.get());
    }

    @Test
    void loadFailure_keepsLastKnownWarns() {
        Stub loader = new Stub();
        WarnCache cache = new WarnCache(loader, 5000L, LOGGER);
        assertEquals(1, cache.get("Alice", true).size());

        loader.failing.set(true);
        cache.invalidateAll();

        assertEquals(1, cache.get("Alice", true).size(), "查询失败不能返回空表，否则 LBAC 会误判为无警告");
        assertEquals(2, cache.get("Alice", false).size());
    }

    @Test
    void loadFailure_isRetriedAndRecovers() throws Exception {
        Stub loader = new Stub();
        WarnCache cache = new WarnCache(loader, 5000L, LOGGER);
        cache.get("Alice", true);

        loader.failing.set(true);
        cache.invalidateAll();
        cache.get("Alice", true);
        int loadsWhileFailing = loader.loads.get();

        loader.failing.set(false);
        Thread.sleep(WarnCache.FAILURE_RETRY_MILLIS + 200L);
        assertEquals(1, cache.get("Alice", true).size());
        assertTrue(loader.loads.get() > loadsWhileFailing, "退避窗口过后应重试并恢复");
    }

    @Test
    void coldFailure_doesNotCacheGarbageForever() throws Exception {
        Stub loader = new Stub();
        loader.failing.set(true);
        WarnCache cache = new WarnCache(loader, 5000L, LOGGER);

        assertTrue(cache.get("Alice", true).isEmpty());
        Thread.sleep(WarnCache.FAILURE_RETRY_MILLIS + 200L);

        loader.failing.set(false);
        assertEquals(1, cache.get("Alice", true).size());
    }

    @Test
    void blankPlayer_isAnsweredWithoutTouchingDatabase() {
        Stub loader = new Stub();
        WarnCache cache = new WarnCache(loader, 5000L, LOGGER);

        assertTrue(cache.get(null, true).isEmpty());
        assertTrue(cache.get("   ", true).isEmpty());
        assertEquals(0, loader.loads.get());
    }

    @Test
    void cachedPlayers_areBounded() {
        Stub loader = new Stub();
        WarnCache cache = new WarnCache(loader, 5000L, LOGGER);

        for (int i = 0; i <= WarnCache.MAX_CACHED_PLAYERS; i++) {
            cache.get("player" + i, true);
        }
        int loadsAfterFill = loader.loads.get();
        assertEquals(WarnCache.MAX_CACHED_PLAYERS + 1, loadsAfterFill);

        cache.get("player0", true);
        assertEquals(loadsAfterFill + 1, loader.loads.get(), "最久未用的条目应被淘汰，不能让占位符把内存撑爆");
    }
}

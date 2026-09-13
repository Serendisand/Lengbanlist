package org.leng.manager;

import org.junit.jupiter.api.Test;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanCacheTest {

    private static final Logger LOGGER = Logger.getLogger("BanCacheTest");

    private static BanEntry ban(String target) {
        return new BanEntry(target, "staff", System.currentTimeMillis() + 600_000L, "作弊", false);
    }

    private static BanIpEntry ipBan(String ip) {
        return new BanIpEntry(ip, "staff", System.currentTimeMillis() + 600_000L, "VPN", false);
    }

    private static class Flaky implements BanCache.Loader {
        final AtomicBoolean failing = new AtomicBoolean(false);
        final AtomicInteger loads = new AtomicInteger();

        @Override
        public List<BanEntry> loadActiveBans() {
            loads.incrementAndGet();
            if (failing.get()) {
                throw new BanCache.LoadFailure(new IllegalStateException("数据库连接已断开"));
            }
            return List.of(ban("Alice"));
        }

        @Override
        public List<BanIpEntry> loadActiveIpBans() {
            if (failing.get()) {
                throw new BanCache.LoadFailure(new IllegalStateException("数据库连接已断开"));
            }
            return List.of(ipBan("203.0.113.7"));
        }
    }

    @Test
    void loadFailure_keepsLastKnownBansInsteadOfUnbanningEveryone() {
        Flaky loader = new Flaky();
        BanCache cache = new BanCache(loader, 5000L, LOGGER);

        assertTrue(cache.isBanned("Alice"));
        assertTrue(cache.isIpBanned("203.0.113.7"));

        loader.failing.set(true);
        cache.invalidate();

        assertTrue(cache.isBanned("Alice"), "查询失败必须沿用上一次快照，不能视作无封禁");
        assertTrue(cache.isIpBanned("203.0.113.7"), "IP 封禁同样不能被查询失败清空");
        assertFalse(cache.isBanned("Bob"));
    }

    @Test
    void loadFailure_isRetriedAndRecovers() throws Exception {
        Flaky loader = new Flaky();
        BanCache cache = new BanCache(loader, 5000L, LOGGER);
        assertTrue(cache.isBanned("Alice"));
        int loadsBeforeFailure = loader.loads.get();

        loader.failing.set(true);
        cache.reload();
        assertTrue(cache.isBanned("Alice"));
        int loadsAfterFailure = loader.loads.get();
        assertTrue(loadsAfterFailure > loadsBeforeFailure, "失败后必须真的重新查过库");

        loader.failing.set(false);
        Thread.sleep(BanCache.FAILURE_RETRY_MILLIS + 200L);
        assertTrue(cache.isBanned("Alice"));
        assertEquals(1, loader.loads.get() - loadsAfterFailure, "退避窗口过后应重试一次并恢复");
    }

    @Test
    void duplicateTargets_keepOnlyTheNewestEndTime() {
        long now = System.currentTimeMillis();
        BanCache cache = new BanCache(new BanCache.Loader() {
            @Override
            public List<BanEntry> loadActiveBans() {
                return List.of(
                        new BanEntry("Alice", "s1", now + 1000L, "旧", false),
                        new BanEntry("Alice", "s2", now + 900_000L, "新", false));
            }

            @Override
            public List<BanIpEntry> loadActiveIpBans() {
                return List.of();
            }
        }, 5000L, LOGGER);

        assertEquals(1, cache.getUnexpiredBans().size());
        assertEquals(now + 900_000L, cache.getBan("Alice").getTime());
        assertEquals(1, cache.countUnexpiredBans());
    }
}

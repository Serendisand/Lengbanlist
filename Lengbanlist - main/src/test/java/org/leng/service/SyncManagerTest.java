package org.leng.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.leng.object.SyncEvent;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.leng.storage.DatabaseManager;

@ExtendWith(MockitoExtension.class)
class SyncManagerTest {

    private static final int BATCH = 500;

    @Mock Lengbanlist plugin;
    @Mock DatabaseManager db;
    @Mock MuteManager muteManager;
    @Mock FreezeManager freezeManager;

    private SyncManager sync;

    @BeforeEach
    void setUp() {
        when(plugin.getDatabaseManager()).thenReturn(db);
        sync = new SyncManager(plugin);
    }

    @Test
    void firstPollOnlyInitializesCursor() {
        when(db.getLatestSyncEventId()).thenReturn(42L);

        sync.pollEvents();

        assertEquals(42L, sync.getCursor(), "棣栨�℃媺鍙栧簲鎶婃父鏍囨帹鍒版渶鏂�,涓嶅洖鏀惧巻鍙蹭簨浠�");
        verify(db, never()).getSyncEventsAfter(anyLong(), anyInt());
        verify(db, never()).reloadBanCache();
    }

    @Test
    void pollAppliesInvalidationByScope() {
        when(plugin.getMuteManager()).thenReturn(muteManager);
        when(plugin.getFreezeManager()).thenReturn(freezeManager);
        when(db.getLatestSyncEventId()).thenReturn(10L);
        when(db.getSyncEventsAfter(10L, BATCH)).thenReturn(List.of(
                new SyncEvent(11L, 1L, "survival", SyncEvent.SCOPE_BAN, "Alice", SyncEvent.ACTION_ADD),
                new SyncEvent(12L, 1L, "survival", SyncEvent.SCOPE_MUTE, "bob", SyncEvent.ACTION_ADD),
                new SyncEvent(13L, 1L, "survival", SyncEvent.SCOPE_WARN, "Carol", SyncEvent.ACTION_REMOVE),
                new SyncEvent(14L, 1L, "survival", SyncEvent.SCOPE_FREEZE, "Dave", SyncEvent.ACTION_ADD)));

        sync.pollEvents();
        sync.pollEvents();

        assertEquals(14L, sync.getCursor());
        assertEquals(4L, sync.getAppliedEvents());
        verify(db).reloadBanCache();
        verify(muteManager).invalidate("bob");
        verify(db).invalidateWarn("Carol");
        verify(freezeManager).reload();
        assertTrue(sync.getLastError().isEmpty());
    }

    @Test
    void ipBanEventRefreshesBanCache() {
        when(db.getLatestSyncEventId()).thenReturn(3L);
        when(db.getSyncEventsAfter(3L, BATCH)).thenReturn(List.of(
                new SyncEvent(4L, 1L, "lobby", SyncEvent.SCOPE_IP_BAN, "203.0.113.7", SyncEvent.ACTION_ADD)));

        sync.pollEvents();
        sync.pollEvents();

        verify(db).reloadBanCache();
        assertEquals(4L, sync.getCursor());
    }

    @Test
    void wildcardWarnEventReloadsWholeWarnCache() {
        when(db.getLatestSyncEventId()).thenReturn(1L);
        when(db.getSyncEventsAfter(1L, BATCH)).thenReturn(List.of(
                new SyncEvent(2L, 1L, "lobby", SyncEvent.SCOPE_WARN, SyncEvent.TARGET_ALL, SyncEvent.ACTION_REMOVE)));

        sync.pollEvents();
        sync.pollEvents();

        verify(db).reloadWarnCache();
        verify(db, never()).invalidateWarn(anyString());
    }

    @Test
    void pollFailureKeepsCursorAndRecordsError() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("LengbanlistTest"));
        when(db.getLatestSyncEventId()).thenReturn(7L);
        when(db.getSyncEventsAfter(7L, BATCH)).thenThrow(new IllegalStateException("鏁版嵁搴撹繛鎺ヤ腑鏂�"));

        sync.pollEvents();
        sync.pollEvents();

        assertEquals(7L, sync.getCursor(), "鎷夊彇澶辫触鏃舵父鏍囧繀椤诲仠鍦ㄥ師鍦�,鎭㈠�嶅悗缁х画娑堣垂");
        assertTrue(sync.getLastError().contains("鏁版嵁搴撹繛鎺ヤ腑鏂�"));
        verify(db, never()).reloadBanCache();
    }
}

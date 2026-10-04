package org.leng.extension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.leng.api.BanService;
import org.leng.api.DataStore;
import org.leng.api.MuteService;
import org.leng.api.Services;
import org.leng.api.WarnService;
import org.leng.service.BanManager;
import org.leng.storage.DatabaseManager;
import org.leng.service.MuteManager;
import org.leng.service.WarnManager;
import org.leng.object.BanEntry;
import org.leng.object.MuteEntry;
import org.leng.object.WarnEntry;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 核心侧契约实现的"薄委托"性质。
 *
 * <p>这些门面一旦自己实现业务逻辑，扩展看到的行为就会和命令层分叉——所以这里
 * 逐条验证它们只是把调用转给 manager，而不是"顺便"做了别的事。
 */
@ExtendWith(MockitoExtension.class)
class ExtensionFacadesTest {

    @Mock Lengbanlist plugin;
    @Mock BanManager banManager;
    @Mock MuteManager muteManager;
    @Mock WarnManager warnManager;

    private Services services() {
        lenient().when(plugin.getBanManager()).thenReturn(banManager);
        lenient().when(plugin.getMuteManager()).thenReturn(muteManager);
        lenient().when(plugin.getWarnManager()).thenReturn(warnManager);
        return new ExtensionFacades.ServicesImpl(plugin);
    }

    // ---------------------------------------------------------- DataStore

    @Test
    void tablePrefixIsDerivedFromExtensionId() {
        DataStore store = new ExtensionFacades.DataStoreImpl(plugin, "sync");
        assertEquals("ext_sync_", store.tablePrefix());

        DataStore dashed = new ExtensionFacades.DataStoreImpl(plugin, "audit-chain");
        assertEquals("ext_audit_chain_", dashed.tablePrefix(),
                "扩展 id 里的连字符要换成下划线，否则拼出的表名在 SQL 里难用");
    }

    @Test
    void connectionFailsCleanlyWhenDatabaseIsNotReady() {
        when(plugin.getDatabaseManager()).thenReturn(null);
        DataStore store = new ExtensionFacades.DataStoreImpl(plugin, "sync");

        assertThrows(SQLException.class, store::connection);
        assertFalse(store.isNetworkDatabase());
    }

    @Test
    void connectionIsBorrowedFromTheCorePool() throws Exception {
        DatabaseManager database = org.mockito.Mockito.mock(DatabaseManager.class);
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        when(plugin.getDatabaseManager()).thenReturn(database);
        when(database.getConnection()).thenReturn(connection);
        when(database.isNetworkDatabase()).thenReturn(true);

        DataStore store = new ExtensionFacades.DataStoreImpl(plugin, "sync");

        assertSame(connection, store.connection(), "必须是核心池子里的那个连接");
        assertTrue(store.isNetworkDatabase());
    }

    // ---------------------------------------------------------- 服务委托

    @Test
    void banReadsGoStraightToBanManager() {
        BanService bans = services().bans();
        BanEntry entry = org.mockito.Mockito.mock(BanEntry.class);
        when(banManager.isPlayerBanned("Steve")).thenReturn(true);
        when(banManager.getBanEntry("Steve")).thenReturn(entry);
        when(banManager.getBanList()).thenReturn(Collections.singletonList(entry));
        when(banManager.countActiveBans()).thenReturn(7);

        assertTrue(bans.isBanned("Steve"));
        assertEquals(Optional.of(entry), bans.findBan("Steve"));
        assertEquals(1, bans.activeBans().size());
        assertEquals(7, bans.activeBanCount());
    }

    @Test
    void banReturnsFalseWhenMutationIsNotApplied() {
        BanService bans = services().bans();
        when(banManager.isPlayerBanned("Steve")).thenReturn(false);
        when(banManager.getBanEntry("Steve")).thenReturn(null);

        assertFalse(bans.isBanned("Steve"));
        assertEquals(Optional.empty(), bans.findBan("Steve"), "查不到记录应返回空 Optional 而不是 null");
    }

    @Test
    void muteDelegatesBothWays() {
        MuteService mutes = services().mutes();
        MuteEntry entry = org.mockito.Mockito.mock(MuteEntry.class);
        when(muteManager.mutePlayer(org.mockito.ArgumentMatchers.any())).thenReturn(12345L);
        when(muteManager.unmutePlayerIfMuted("Steve", "Admin")).thenReturn(true);
        when(muteManager.isPlayerMuted("Steve")).thenReturn(true);
        when(muteManager.getMuteList()).thenReturn(Collections.singletonList(entry));
        when(muteManager.countActiveMutes()).thenReturn(3);

        assertEquals(12345L, mutes.mute("Steve", "Admin", 60_000L, "刷屏"));
        assertTrue(mutes.unmute("Steve", "Admin"));
        assertTrue(mutes.isMuted("Steve"));
        assertEquals(1, mutes.activeMutes().size());
        assertEquals(3, mutes.activeMuteCount());
    }

    @Test
    void warnDelegatesAndUsesCoreThresholdLogic() {
        WarnService warnings = services().warnings();
        WarnEntry entry = org.mockito.Mockito.mock(WarnEntry.class);
        List<String> warned = Collections.singletonList("Steve");
        when(warnManager.countActiveWarnings(anyString())).thenReturn(2);
        when(warnManager.getActiveWarnings(anyString())).thenReturn(Collections.singletonList(entry));
        when(warnManager.getWarnedPlayers()).thenReturn(warned);
        when(warnManager.unwarnPlayer(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);

        warnings.warn("Steve", "Admin", "辱骂");
        verify(warnManager).warnPlayer("Steve", "Admin", "辱骂");

        assertEquals(2, warnings.countActiveWarnings("Steve"));
        assertEquals(1, warnings.activeWarnings("Steve").size());
        assertSame(warned, warnings.warnedPlayers());
        assertTrue(warnings.unwarn("Steve", 0, null));
    }
}

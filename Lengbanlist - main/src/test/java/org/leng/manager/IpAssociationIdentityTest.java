package org.leng.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class IpAssociationIdentityTest {

    private static final String UUID_ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String UUID_BOB = "22222222-2222-2222-2222-222222222222";

    @Mock Lengbanlist plugin;
    @Mock BanManager banManager;
    @TempDir Path tempDir;

    private DatabaseManager db;
    private IpAssociationManager manager;

    @BeforeEach
    void setUp() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.type", "sqlite");
        config.set("database.sqlite.file", "lengbanlist.db");
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getStorageConfig()).thenReturn(config);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("IpAssociationIdentityTest"));
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getBanManager()).thenReturn(banManager);

        db = new DatabaseManager(plugin);
        db.initialize();
        lenient().when(plugin.getDatabaseManager()).thenReturn(db);
        manager = new IpAssociationManager(plugin);
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    private static List<String> names(List<IpAssociationManager.AltAccount> alts) {
        List<String> result = new ArrayList<>();
        for (IpAssociationManager.AltAccount alt : alts) {
            result.add(alt.name);
        }
        return result;
    }

    @Test
    void altsSkipThePlayersOwnOldNames() {
        long now = System.currentTimeMillis();
        db.recordIdentity(UUID_ALICE, "Alice", now);
        db.recordIdentity(UUID_ALICE, "Alicia", now);
        db.recordPlayerLoginIp(UUID_ALICE, "Alice", "203.0.113.5", now);
        db.recordIdentity(UUID_BOB, "Bob", now);
        db.recordPlayerLoginIp(UUID_BOB, "Bob", "203.0.113.5", now);

        assertEquals(List.of("Bob"), names(manager.getAlts("Alicia")),
                "自己的旧名和现名都不算小号，只有真正同 IP 的别人算");
        assertEquals(List.of("Bob"), names(manager.getAlts("Alice")), "按旧名查询结果一致");
    }

    @Test
    void altsStillReportedForUnknownPlayer() {
        long now = System.currentTimeMillis();
        db.recordPlayerLoginIp(UUID_ALICE, "Alice", "203.0.113.5", now);
        db.recordPlayerLoginIp(UUID_BOB, "Bob", "203.0.113.5", now);

        List<IpAssociationManager.AltAccount> alts = manager.getAlts("Alice");

        assertEquals(1, alts.size(), "没有身份记录时仍旧只按名字排除自己");
        assertEquals("Bob", alts.get(0).name);
        assertTrue(manager.getAlts("Nobody").isEmpty());
    }
}

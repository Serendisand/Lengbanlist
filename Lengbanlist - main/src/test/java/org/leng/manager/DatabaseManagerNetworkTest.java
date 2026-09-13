package org.leng.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.leng.object.AuditEntry;
import org.leng.object.BanEntry;
import org.leng.object.BanIpEntry;
import org.leng.object.MuteEntry;
import org.leng.object.ReportEntry;
import org.leng.object.WarnEntry;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("真实数据库冒烟测试：设置 LENGBANLIST_TEST_MARIADB_URL / LENGBANLIST_TEST_POSTGRESQL_URL"
        + "（形如 jdbc:mariadb://127.0.0.1:3306/lengbanlist_test）以及对应的 _USER / _PASSWORD 环境变量后运行；"
        + "未配置的方言自动跳过。请指向一次性测试库，测试会写入以 Lbtest 开头的少量数据。")
class DatabaseManagerNetworkTest {

    @Mock Lengbanlist plugin;
    @TempDir Path tempDir;

    @Test
    void mariadbLifecycle() throws Exception {
        String url = System.getenv("LENGBANLIST_TEST_MARIADB_URL");
        assumeTrue(url != null && !url.trim().isEmpty(), "未配置 LENGBANLIST_TEST_MARIADB_URL，跳过");
        runLifecycle(DatabaseDialect.MARIADB, url.trim());
    }

    @Test
    void postgresqlLifecycle() throws Exception {
        String url = System.getenv("LENGBANLIST_TEST_POSTGRESQL_URL");
        assumeTrue(url != null && !url.trim().isEmpty(), "未配置 LENGBANLIST_TEST_POSTGRESQL_URL，跳过");
        runLifecycle(DatabaseDialect.POSTGRESQL, url.trim());
    }

    private void runLifecycle(DatabaseDialect dialect, String url) throws Exception {
        String[] parsed = parseJdbcUrl(url);
        String prefix = "database." + dialect.configKey() + ".";
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.type", dialect.configKey());
        config.set(prefix + "host", parsed[0]);
        config.set(prefix + "port", Integer.parseInt(parsed[1]));
        config.set(prefix + "database", parsed[2]);
        config.set(prefix + "username", System.getenv("LENGBANLIST_TEST_" + dialect.name() + "_USER"));
        config.set(prefix + "password", System.getenv("LENGBANLIST_TEST_" + dialect.name() + "_PASSWORD"));
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("LengbanlistNetworkTest"));
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        DatabaseManager db = new DatabaseManager(plugin);
        db.initialize();
        String target = "Lbtest" + Long.toHexString(System.nanoTime());
        try {
            db.ensureSchema();

            long end = System.currentTimeMillis() + 600_000L;
            assertEquals(DatabaseManager.WriteResult.APPLIED,
                    db.replaceActiveBan(new BanEntry(target, "staff", end, "冒烟测试", false)));
            assertTrue(db.isPlayerBanned(target));
            assertEquals("冒烟测试", db.getBan(target).getReason());

            assertEquals(DatabaseManager.WriteResult.APPLIED,
                    db.replaceActiveBan(new BanEntry(target, "staff", end, "二次封禁", false)));
            assertEquals(2, db.countBanHistory(target), "旧封禁应保留为历史行");
            assertEquals(2, db.getBansByPlayer(target).size());

            assertEquals(DatabaseManager.WriteResult.APPLIED, db.deactivateBanForUnban(target, System.currentTimeMillis()));
            assertFalse(db.isPlayerBanned(target));
            deleteBanQuietly(db, target);
            assertNull(db.getBan(target));

            db.replaceActiveIpBan(new BanIpEntry("203.0.113.77", "staff", end, "冒烟测试", false));
            assertTrue(db.isIpBanned("203.0.113.77"));
            db.deactivateIpBanForUnban("203.0.113.77", System.currentTimeMillis());
            assertFalse(db.isIpBanned("203.0.113.77"));
            db.deleteIpBan("203.0.113.77");

            db.upsertMute(new MuteEntry(target, "staff", end, "冒烟测试"));
            assertNotNull(db.getMute(target));
            db.deleteMute(target);
            assertNull(db.getMute(target));

            String warnId = "warn" + target;
            db.upsertWarning(new WarnEntry(warnId, target, "staff", System.currentTimeMillis(), "冒烟测试"));
            List<WarnEntry> active = db.getWarnings(target, true);
            assertEquals(1, active.size());
            assertFalse(active.get(0).isRevoked());
            db.updateWarningRevoked(warnId, true);
            assertTrue(db.getWarnings(target, true).isEmpty());
            assertTrue(db.getWarnings(target, false).get(0).isRevoked());

            String reportId = "report" + target;
            db.upsertReport(new ReportEntry(target, "reporter", "冒烟测试", reportId, System.currentTimeMillis(), "未处理"));
            assertEquals("未处理", db.getReport(reportId).getStatus());
            db.deleteReport(reportId);
            assertNull(db.getReport(reportId));

            db.recordPlayerLoginIp(target, "203.0.113.88", System.currentTimeMillis());
            assertEquals("203.0.113.88", db.getPlayerIp(target));
            db.recordPlayerLoginIp(target, "203.0.113.89", System.currentTimeMillis());
            assertEquals(2, db.getPlayerIpHistory(target).size());
            assertTrue(db.getPlayersByIpFromHistory("203.0.113.89").contains(target));

            for (int i = 0; i < 3; i++) {
                assertTrue(db.addAuditLogChained("staff", "冒烟测试", target, "第" + i + "条", true));
            }
            List<AuditEntry> page = db.getAuditLogsAfter(0L, 1000);
            int checked = 0;
            for (int i = 1; i < page.size(); i++) {
                String expected = DatabaseManager.hashRow(page.get(i - 1).getPrevHash(),
                        page.get(i - 1).getTimestamp(), page.get(i - 1).getActor(), page.get(i - 1).getAction(),
                        page.get(i - 1).getTarget(), page.get(i - 1).getReason(), page.get(i - 1).isSuccess());
                assertEquals(expected, page.get(i).getPrevHash(), "第 " + i + " 行审计链断裂");
                checked++;
            }
            assertTrue(checked > 0, "审计链应有可校验的行");
        } finally {
            deleteBanQuietly(db, target);
            db.close();
        }
    }

    private void deleteBanQuietly(DatabaseManager db, String target) {
        try {
            db.deleteBan(target);
        } catch (RuntimeException ignored) {
        }
    }

    private static String[] parseJdbcUrl(String url) {
        String rest = url.substring(url.indexOf("//") + 2);
        int slash = rest.indexOf('/');
        String authority = slash < 0 ? rest : rest.substring(0, slash);
        String database = slash < 0 ? "lengbanlist" : rest.substring(slash + 1);
        int question = database.indexOf('?');
        if (question >= 0) {
            database = database.substring(0, question);
        }
        int colon = authority.indexOf(':');
        String host = colon < 0 ? authority : authority.substring(0, colon);
        String port = colon < 0 ? "0" : authority.substring(colon + 1);
        if (port.isEmpty()) {
            port = "0";
        }
        return new String[]{host, port, database};
    }
}

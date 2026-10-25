package org.leng.service;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.leng.api.events.LengbanlistReportEvent;
import org.leng.object.ReportBanEntry;
import org.leng.object.ReportEntry;
import org.leng.storage.DatabaseManager;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportManagerTest {

    @Mock Lengbanlist plugin;
    @Mock DatabaseManager db;
    @Mock AuditManager audit;
    @Mock PluginManager pluginManager;

    ReportManager manager;
    MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getDatabaseManager()).thenReturn(db);
        lenient().when(plugin.getAuditManager()).thenReturn(audit);
        bukkit = Mockito.mockStatic(Bukkit.class);
        lenient().when(Bukkit.getPluginManager()).thenReturn(pluginManager);
        lenient().when(Bukkit.getPlayer(anyString())).thenReturn(null);
        lenient().when(Bukkit.getOnlinePlayers()).thenReturn(List.of());
        manager = new ReportManager(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void rejectsSelfReport() {
        Player reporter = reporter("Alex");

        ReportManager.SubmitOutcome outcome = manager.submitReport(reporter, "Alex", "cheat");

        assertEquals(ReportManager.SubmitResult.SELF, outcome.result());
        verify(db, never()).upsertReport(any());
    }

    @Test
    void rejectsEmptyReason() {
        Player reporter = reporter("Alex");

        ReportManager.SubmitOutcome outcome = manager.submitReport(reporter, "Steve", "  ");

        assertEquals(ReportManager.SubmitResult.INVALID, outcome.result());
        verify(db, never()).upsertReport(any());
    }

    @Test
    void detectsCooldown() {
        Player reporter = reporter("Alex");
        when(plugin.getConfig()).thenReturn(configWithCooldown(86400));
        ReportEntry existing = new ReportEntry("Steve", "Alex", "old", "old-id",
                System.currentTimeMillis(), ReportEntry.STATUS_PENDING);
        when(db.getReportsByReporterAndTarget("Alex", "Steve")).thenReturn(List.of(existing));

        ReportManager.SubmitOutcome outcome = manager.submitReport(reporter, "Steve", "cheat");

        assertEquals(ReportManager.SubmitResult.COOLDOWN, outcome.result());
        assertTrue(outcome.remainingMillis() > 0L);
        verify(db, never()).upsertReport(any());
    }

    @Test
    void submitsReportAndNotifiesEvent() {
        Player reporter = reporter("Alex");
        when(plugin.getConfig()).thenReturn(configWithCooldown(86400));
        when(db.getReportsByReporterAndTarget("Alex", "Steve")).thenReturn(List.of());
        when(db.upsertReport(any(ReportEntry.class))).thenReturn(true);

        ReportManager.SubmitOutcome outcome = manager.submitReport(reporter, "Steve", "cheat");

        assertEquals(ReportManager.SubmitResult.OK, outcome.result());
        verify(db).upsertReport(argThat(report ->
                "Steve".equals(report.getTarget())
                        && "Alex".equals(report.getReporter())
                        && ReportEntry.STATUS_PENDING.equals(report.getStatus())));
        verify(pluginManager).callEvent(any(LengbanlistReportEvent.class));
        verify(audit).log(eq("提交举报"), eq("Alex"), eq("Steve"), eq("cheat"));
    }

    @Test
    void reportsDatabaseFailureOnSubmit() {
        Player reporter = reporter("Alex");
        when(plugin.getConfig()).thenReturn(configWithCooldown(86400));
        when(db.getReportsByReporterAndTarget("Alex", "Steve")).thenReturn(List.of());
        when(db.upsertReport(any(ReportEntry.class))).thenReturn(false);

        ReportManager.SubmitOutcome outcome = manager.submitReport(reporter, "Steve", "cheat");

        assertEquals(ReportManager.SubmitResult.DB_ERROR, outcome.result());
        verify(pluginManager, never()).callEvent(any(LengbanlistReportEvent.class));
    }

    @Test
    void acceptsPendingReport() {
        ReportEntry report = pending("id");
        when(db.getReport("id")).thenReturn(report);
        when(db.upsertReport(any(ReportEntry.class))).thenReturn(true);

        ReportManager.ActionResult result = manager.acceptReport("id", "Admin");

        assertEquals(ReportManager.ActionResult.OK, result);
        verify(db).upsertReport(argThat(updated ->
                ReportEntry.STATUS_ACCEPTED.equals(updated.getStatus())));
        verify(audit).log(eq("受理举报"), eq("Admin"), eq("Steve"), contains("id"));
    }

    @Test
    void rejectsAcceptingFinalizedReport() {
        when(db.getReport("id")).thenReturn(
                new ReportEntry("Steve", "Alex", "cheat", "id", 1L, ReportEntry.STATUS_CLOSED));

        assertEquals(ReportManager.ActionResult.FINALIZED,
                manager.acceptReport("id", "Admin"));
        verify(db, never()).upsertReport(any());
    }

    @Test
    void rejectsAcceptingAlreadyAcceptedReport() {
        when(db.getReport("id")).thenReturn(
                new ReportEntry("Steve", "Alex", "cheat", "id", 1L, ReportEntry.STATUS_ACCEPTED));

        assertEquals(ReportManager.ActionResult.ALREADY_ACCEPTED,
                manager.acceptReport("id", "Admin"));
        verify(db, never()).upsertReport(any());
    }

    @Test
    void closesPendingReport() {
        when(db.getReport("id")).thenReturn(pending("id"));
        when(db.upsertReport(any(ReportEntry.class))).thenReturn(true);

        ReportManager.ActionResult result = manager.closeReport("id", "Admin");

        assertEquals(ReportManager.ActionResult.OK, result);
        verify(db).upsertReport(argThat(updated ->
                ReportEntry.STATUS_CLOSED.equals(updated.getStatus())));
        verify(audit).log(eq("关闭举报"), eq("Admin"), eq("Steve"), contains("id"));
    }

    @Test
    void reportsDatabaseFailureOnAccept() {
        when(db.getReport("id")).thenReturn(pending("id"));
        when(db.upsertReport(any(ReportEntry.class))).thenReturn(false);

        assertEquals(ReportManager.ActionResult.DB_ERROR,
                manager.acceptReport("id", "Admin"));
    }

    @Test
    void rejectsReportsWhenReporterIsBanned() {
        Player reporter = reporter("Alex");
        when(db.getReportBan("Alex")).thenReturn(
                new ReportBanEntry("Alex", "Admin", Long.MAX_VALUE, "滥用举报"));

        ReportManager.SubmitOutcome outcome = manager.submitReport(reporter, "Steve", "cheat");

        assertEquals(ReportManager.SubmitResult.REPORT_BANNED, outcome.result());
        assertEquals(Long.MAX_VALUE, outcome.remainingMillis());
        verify(db, never()).upsertReport(any());
    }

    @Test
    void rejectsRateLimitedReporter() {
        Player reporter = reporter("Alex");
        YamlConfiguration config = configWithCooldown(86400);
        config.set("report-rate-limit.enabled", true);
        config.set("report-rate-limit.window-seconds", 600);
        config.set("report-rate-limit.max-reports", 2);
        when(plugin.getConfig()).thenReturn(config);
        when(db.getReporterReportStatsSince(eq("Alex"), anyLong()))
                .thenReturn(new DatabaseManager.ReporterReportStats(2, System.currentTimeMillis() - 1000L));

        ReportManager.SubmitOutcome outcome = manager.submitReport(reporter, "Steve", "cheat");

        assertEquals(ReportManager.SubmitResult.RATE_LIMITED, outcome.result());
        assertTrue(outcome.remainingMillis() > 0L);
        verify(db, never()).upsertReport(any());
    }

    @Test
    void disablesAndEnablesReporting() {
        when(db.upsertReportBan(any(ReportBanEntry.class))).thenReturn(true);
        when(db.getReportBan("Alex")).thenReturn(
                new ReportBanEntry("Alex", "Admin", Long.MAX_VALUE, "滥用举报"));

        assertTrue(manager.disableReporting("Alex", "Admin", Long.MAX_VALUE, "滥用举报"));
        verify(db).upsertReportBan(any(ReportBanEntry.class));
        verify(audit).log(eq("禁用举报"), eq("Admin"), eq("Alex"), eq("滥用举报"));

        assertTrue(manager.enableReporting("Alex", "Admin"));
        verify(db).deleteReportBan("Alex");
        verify(audit).log(eq("解除举报禁用"), eq("Admin"), eq("Alex"), eq("滥用举报"));
    }

    private Player reporter(String name) {
        Player player = Mockito.mock(Player.class);
        lenient().when(player.getName()).thenReturn(name);
        return player;
    }

    private ReportEntry pending(String id) {
        return new ReportEntry("Steve", "Alex", "cheat", id,
                System.currentTimeMillis(), ReportEntry.STATUS_PENDING);
    }

    private YamlConfiguration configWithCooldown(int seconds) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("report-cooldown-seconds", seconds);
        return config;
    }
}

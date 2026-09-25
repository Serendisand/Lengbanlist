package org.leng.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.leng.Lengbanlist;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookNotifierTest {

    @Mock Lengbanlist plugin;
    @Mock FileConfiguration config;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("LengbanlistWebhookTest"));
    }

    private WebhookNotifier newNotifier(int queueSize) {
        lenient().when(config.getInt("webhook.queue-size", 500)).thenReturn(queueSize);
        lenient().when(plugin.getServerName()).thenReturn("生存一区");
        return new WebhookNotifier(plugin);
    }

    private void enableDelivery() {
        lenient().when(plugin.isFeatureEnabled("webhook-events")).thenReturn(true);
        lenient().when(config.getBoolean("webhook.enabled", true)).thenReturn(true);
        lenient().when(config.getString("webhook.url", "")).thenReturn("http://127.0.0.1:1/webhook");
    }

    @Test
    void submit_overCapacity_dropsOldestJobAndCountsDrops() {
        WebhookNotifier notifier = newNotifier(3);

        for (int i = 0; i < 5; i++) {
            assertTrue(notifier.submit("封禁", "actor" + i, "target" + i, "", true));
        }

        WebhookNotifier.Status status = notifier.status();
        assertEquals(3, status.queueSize());
        assertEquals(2, status.dropped());

        WebhookNotifier.Job[] pending = notifier.pending();
        assertEquals(3, pending.length);
        assertEquals("target2", pending[0].target);
        assertEquals("target4", pending[2].target);
    }

    @Test
    void submit_underCapacity_dropsNothing() {
        WebhookNotifier notifier = newNotifier(10);

        assertTrue(notifier.submit("踢出", "actor", "target", "reason", true));

        WebhookNotifier.Status status = notifier.status();
        assertEquals(1, status.queueSize());
        assertEquals(0, status.dropped());
    }

    @Test
    void submit_afterStop_rejectedAndQueueDrained() {
        WebhookNotifier notifier = newNotifier(5);
        assertTrue(notifier.submit("封禁", "actor", "target", "", true));

        notifier.stop();
        assertFalse(notifier.submit("封禁", "actor", "target", "", true));
        notifier.stop();

        assertEquals(0, notifier.status().queueSize());
        assertFalse(notifier.isRunning());
    }

    @Test
    void notifyEvent_featureDisabled_enqueuesNothing() {
        when(plugin.isFeatureEnabled("webhook-events")).thenReturn(false);
        WebhookNotifier notifier = newNotifier(10);

        notifier.notifyEvent("封禁", "actor", "target", "reason", true);

        assertEquals(0, notifier.status().queueSize());
        assertFalse(notifier.isRunning());
    }

    @Test
    void notifyEvent_webhookDisabled_enqueuesNothing() {
        when(plugin.isFeatureEnabled("webhook-events")).thenReturn(true);
        when(config.getBoolean("webhook.enabled", true)).thenReturn(false);
        WebhookNotifier notifier = newNotifier(10);

        notifier.notifyEvent("封禁", "actor", "target", "reason", true);

        assertEquals(0, notifier.status().queueSize());
        assertFalse(notifier.isRunning());
    }

    @Test
    void notifyEvent_urlBlank_enqueuesNothing() {
        when(plugin.isFeatureEnabled("webhook-events")).thenReturn(true);
        when(config.getBoolean("webhook.enabled", true)).thenReturn(true);
        when(config.getString("webhook.url", "")).thenReturn("   ");
        WebhookNotifier notifier = newNotifier(10);

        notifier.notifyEvent("封禁", "actor", "target", "reason", true);

        assertEquals(0, notifier.status().queueSize());
        assertFalse(notifier.isRunning());
        assertEquals("未配置地址（webhook.url 为空）", notifier.configState());
    }

    @Test
    void notifyEvent_urlWithoutScheme_enqueuesNothing() {
        when(plugin.isFeatureEnabled("webhook-events")).thenReturn(true);
        when(config.getBoolean("webhook.enabled", true)).thenReturn(true);
        when(config.getString("webhook.url", "")).thenReturn("discord.com/api/webhooks/1/abc");
        WebhookNotifier notifier = newNotifier(10);

        notifier.notifyEvent("封禁", "actor", "target", "reason", true);

        assertEquals(0, notifier.status().queueSize());
        assertFalse(notifier.isConfigured());
    }

    @Test
    void notifyEvent_actionNotInEventTypes_enqueuesNothing() {
        when(plugin.isFeatureEnabled("webhook-events")).thenReturn(true);
        when(config.getBoolean("webhook.enabled", true)).thenReturn(true);
        when(config.getString("webhook.url", "")).thenReturn("http://127.0.0.1:1/webhook");
        when(config.getStringList("webhook.event-types")).thenReturn(List.of("踢出"));
        WebhookNotifier notifier = newNotifier(10);

        notifier.notifyEvent("封禁", "actor", "target", "reason", true);

        assertEquals(0, notifier.status().queueSize());
        assertFalse(notifier.isRunning());
    }

    @Test
    void notifyEvent_configured_startsDispatcherAndStopShutsDown() {
        enableDelivery();
        when(config.getStringList("webhook.event-types")).thenReturn(Collections.emptyList());
        WebhookNotifier notifier = newNotifier(10);

        notifier.notifyEvent("封禁", "actor", "target", "reason", true);
        assertTrue(notifier.isRunning());

        notifier.stop();
        assertFalse(notifier.isRunning());
    }

    @Test
    void notifyEvent_nullAction_ignored() {
        WebhookNotifier notifier = newNotifier(10);

        notifier.notifyEvent(null, "actor", "target", "reason", true);

        assertEquals(0, notifier.status().queueSize());
    }

    @Test
    void buildPayload_includesServerResultAndBanMention() {
        when(config.getString("webhook.username", "Lengbanlist")).thenReturn("Lengbanlist");
        when(config.getString("webhook.avatar-url", "")).thenReturn("");
        when(config.getString("webhook.mention-role", "")).thenReturn("<@&123456789012345678>");
        WebhookNotifier notifier = newNotifier(10);

        JSONObject payload = new JSONObject(notifier.buildPayload(new WebhookNotifier.Job("封禁", "Admin", "Steve", "使用外挂", true)));

        assertEquals("<@&123456789012345678>", payload.getString("content"));
        JSONObject embed = payload.getJSONArray("embeds").getJSONObject(0);
        assertEquals(0xE74C3C, embed.getInt("color"));
        assertEquals("生存一区", fieldValue(embed, "子服"));
        assertEquals("成功", fieldValue(embed, "结果"));
        assertEquals("Steve", fieldValue(embed, "目标"));
        assertEquals("使用外挂", fieldValue(embed, "原因"));
    }

    @Test
    void buildPayload_mentionOnlyForBanActions() {
        lenient().when(config.getString("webhook.username", "Lengbanlist")).thenReturn("Lengbanlist");
        lenient().when(config.getString("webhook.avatar-url", "")).thenReturn("");
        lenient().when(config.getString("webhook.mention-role", "")).thenReturn("123456789012345678");
        WebhookNotifier notifier = newNotifier(10);

        JSONObject unban = new JSONObject(notifier.buildPayload(new WebhookNotifier.Job("解封", "Admin", "Steve", "", true)));

        assertFalse(unban.has("content"));
        assertEquals(0x2ECC71, unban.getJSONArray("embeds").getJSONObject(0).getInt("color"));
    }

    @Test
    void buildPayload_invalidMentionRole_omitted() {
        lenient().when(config.getString("webhook.username", "Lengbanlist")).thenReturn("Lengbanlist");
        lenient().when(config.getString("webhook.avatar-url", "")).thenReturn("");
        when(config.getString("webhook.mention-role", "")).thenReturn("everyone");
        WebhookNotifier notifier = newNotifier(10);

        JSONObject payload = new JSONObject(notifier.buildPayload(new WebhookNotifier.Job("封禁IP", "Admin", "1.2.3.4", "", false)));

        assertFalse(payload.has("content"));
        assertEquals("失败", fieldValue(payload.getJSONArray("embeds").getJSONObject(0), "结果"));
    }

    @Test
    void buildPayload_truncatesFieldValuesToDiscordLimit() {
        lenient().when(config.getString("webhook.username", "Lengbanlist")).thenReturn("Lengbanlist");
        lenient().when(config.getString("webhook.avatar-url", "")).thenReturn("");
        lenient().when(config.getString("webhook.mention-role", "")).thenReturn("");
        WebhookNotifier notifier = newNotifier(10);

        JSONObject payload = new JSONObject(notifier.buildPayload(
                new WebhookNotifier.Job("警告", "Admin", "Steve", "r".repeat(3000), true)));
        JSONArray fields = payload.getJSONArray("embeds").getJSONObject(0).getJSONArray("fields");

        for (int i = 0; i < fields.length(); i++) {
            assertTrue(fields.getJSONObject(i).getString("value").length() <= 1024);
        }
        assertEquals(1024, fieldValue(payload.getJSONArray("embeds").getJSONObject(0), "原因").length());
        assertEquals("Steve", fieldValue(payload.getJSONArray("embeds").getJSONObject(0), "目标"));
    }

    @Test
    void parseRetryAfterMillis_numericSecondsAndDecimal() {
        long now = 1_700_000_000_000L;

        assertEquals(5000L, WebhookNotifier.parseRetryAfterMillis("5", null, now));
        assertEquals(1500L, WebhookNotifier.parseRetryAfterMillis("1.5", null, now));
        assertEquals(60000L, WebhookNotifier.parseRetryAfterMillis("3600", null, now));
        assertEquals(2000L, WebhookNotifier.parseRetryAfterMillis(null, "2", now));
    }

    @Test
    void parseRetryAfterMillis_invalidValuesFallBack() {
        long now = 1_700_000_000_000L;

        assertEquals(-1L, WebhookNotifier.parseRetryAfterMillis(null, null, now));
        assertEquals(-1L, WebhookNotifier.parseRetryAfterMillis("", null, now));
        assertEquals(-1L, WebhookNotifier.parseRetryAfterMillis("   ", null, now));
        assertEquals(-1L, WebhookNotifier.parseRetryAfterMillis("abc", null, now));
        assertEquals(-1L, WebhookNotifier.parseRetryAfterMillis("-3", null, now));
        assertEquals(-1L, WebhookNotifier.parseRetryAfterMillis("0", null, now));
        assertEquals(-1L, WebhookNotifier.parseRetryAfterMillis("NaN", null, now));
    }

    @Test
    void parseRetryAfterMillis_httpDate() {
        long now = 1_700_000_000_000L;
        String httpDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(now + 3000L), ZoneOffset.UTC));

        assertEquals(3000L, WebhookNotifier.parseRetryAfterMillis(httpDate, null, now));
    }

    @Test
    void backoffDelayMs_growsExponentiallyAndCaps() {
        assertEquals(1000L, WebhookNotifier.backoffDelayMs(0, 1000L, 30000L));
        assertEquals(2000L, WebhookNotifier.backoffDelayMs(1, 1000L, 30000L));
        assertEquals(4000L, WebhookNotifier.backoffDelayMs(2, 1000L, 30000L));
        assertEquals(30000L, WebhookNotifier.backoffDelayMs(20, 1000L, 30000L));
        assertEquals(1000L, WebhookNotifier.backoffDelayMs(-1, 1000L, 30000L));
        assertEquals(1L, WebhookNotifier.backoffDelayMs(0, 0L, 0L));
    }

    @Test
    void colorFor_mapsActionGroups() {
        assertEquals(0xE74C3C, WebhookNotifier.colorFor("封禁"));
        assertEquals(0xE74C3C, WebhookNotifier.colorFor("封禁IP"));
        assertEquals(0x2ECC71, WebhookNotifier.colorFor("解封"));
        assertEquals(0xE67E22, WebhookNotifier.colorFor("禁言"));
        assertEquals(0x1ABC9C, WebhookNotifier.colorFor("解除禁言"));
        assertEquals(0xF1C40F, WebhookNotifier.colorFor("警告"));
        assertEquals(0x992D22, WebhookNotifier.colorFor("踢出"));
        assertEquals(0x95A5A6, WebhookNotifier.colorFor("未知动作"));
        assertEquals(0x95A5A6, WebhookNotifier.colorFor(null));
    }

    @Test
    void truncate_andDash_handleEdgeCases() {
        assertEquals("abc", WebhookNotifier.truncate("  abc  ", 1024));
        assertEquals("", WebhookNotifier.truncate(null, 1024));
        assertEquals(1024, WebhookNotifier.truncate("x".repeat(5000), 1024).length());
        assertTrue(WebhookNotifier.truncate("x".repeat(5000), 1024).endsWith("…"));
        assertEquals("—", WebhookNotifier.dash(""));
        assertEquals("—", WebhookNotifier.dash(null));
        assertEquals("abc", WebhookNotifier.dash("abc"));
    }

    private String fieldValue(JSONObject embed, String name) {
        JSONArray fields = embed.getJSONArray("fields");
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.getJSONObject(i);
            if (name.equals(field.getString("name"))) {
                return field.getString("value");
            }
        }
        return null;
    }
}

package org.leng.integration;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.leng.Lengbanlist;
import org.leng.net.DownloadService;
import org.leng.net.DownloadSettings;
import org.leng.util.SchedulerUtils;
import org.leng.util.Utils;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class WebhookNotifier {

    private static final String USER_AGENT = "Lengbanlist-Webhook/1.0";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int DEFAULT_QUEUE_SIZE = 500;
    private static final long DEFAULT_MIN_INTERVAL_MS = 450L;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int MAX_RETRIES_LIMIT = 10;
    private static final long BASE_BACKOFF_MS = 1000L;
    private static final long MAX_BACKOFF_MS = 30000L;
    private static final long MAX_RETRY_WAIT_MS = 60000L;
    private static final long POLL_INTERVAL_MS = 200L;
    private static final long STOP_JOIN_TIMEOUT_MS = 2000L;
    private static final long LOG_THROTTLE_MS = 5000L;
    private static final int FIELD_VALUE_LIMIT = 1024;
    private static final int USERNAME_LIMIT = 80;
    private static final int CONTENT_LIMIT = 2000;
    private static final int COLOR_DEFAULT = 0x95A5A6;
    private static final int COLOR_BAN = 0xE74C3C;
    private static final int COLOR_UNBAN = 0x2ECC71;
    private static final int COLOR_MUTE = 0xE67E22;
    private static final int COLOR_UNMUTE = 0x1ABC9C;
    private static final int COLOR_WARN = 0xF1C40F;
    private static final int COLOR_UNWARN = 0x3498DB;
    private static final int COLOR_KICK = 0x992D22;
    private static final int COLOR_FREEZE = 0x5DADE2;
    private static final int COLOR_UNFREEZE = 0x58D68D;
    private static final int COLOR_REPORT = 0x9B59B6;

    private static final Map<String, Integer> ACTION_COLORS = Map.ofEntries(
            Map.entry("封禁", COLOR_BAN),
            Map.entry("封禁IP", COLOR_BAN),
            Map.entry("设置封禁时间", COLOR_BAN),
            Map.entry("解封", COLOR_UNBAN),
            Map.entry("解封IP", COLOR_UNBAN),
            Map.entry("禁言", COLOR_MUTE),
            Map.entry("修改禁言", COLOR_MUTE),
            Map.entry("解除禁言", COLOR_UNMUTE),
            Map.entry("警告", COLOR_WARN),
            Map.entry("取消警告", COLOR_UNWARN),
            Map.entry("踢出", COLOR_KICK),
            Map.entry("冻结玩家", COLOR_FREEZE),
            Map.entry("解除冻结", COLOR_UNFREEZE),
            Map.entry("解冻全部玩家", COLOR_UNFREEZE),
            Map.entry("提交举报", COLOR_REPORT),
            Map.entry("受理举报", COLOR_REPORT),
            Map.entry("关闭举报", COLOR_REPORT),
            Map.entry("已读举报", COLOR_REPORT)
    );
    private static final Set<String> BAN_ACTIONS = Set.of("封禁", "封禁IP", "设置封禁时间");
    private static final Pattern ROLE_ID = Pattern.compile("\\d{5,25}");

    private final Lengbanlist plugin;
    private final BlockingQueue<Job> queue;
    private final int queueCapacity;
    private final Object rateLock = new Object();
    private final Map<String, Long> lastLogAt = new ConcurrentHashMap<>();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong retried = new AtomicLong();

    private volatile Thread worker;
    private volatile boolean running;
    private volatile boolean stopped;
    private volatile long nextAllowedAt;
    private volatile long lastSuccessAt;
    private volatile String lastError = "";
    private volatile long lastErrorAt;
    private volatile DownloadService downloads;

    public WebhookNotifier(Lengbanlist plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin == null ? null : plugin.getConfig();
        int configured = config == null ? DEFAULT_QUEUE_SIZE : config.getInt("webhook.queue-size", DEFAULT_QUEUE_SIZE);
        this.queueCapacity = Math.max(1, configured);
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
    }

    public void notifyEvent(String action, String actor, String target, String reason, boolean success) {
        try {
            if (stopped || action == null || action.trim().isEmpty()) {
                return;
            }
            if (!plugin.isFeatureEnabled("webhook-events")) {
                return;
            }
            if (!isEnabled() || resolveUrl().isEmpty()) {
                return;
            }
            List<String> eventTypes = eventTypes();
            if (!eventTypes.isEmpty() && !eventTypes.contains(action)) {
                return;
            }
            ensureStarted();
            String safeActor = actor == null || actor.trim().isEmpty() ? "System" : actor.trim();
            submit(action, safeActor, target == null ? "" : target, reason == null ? "" : reason, success);
        } catch (Throwable t) {
            logThrottled("enqueue", "Webhook 事件入队失败: " + t);
        }
    }

    public void sendTest(CommandSender sender) {
        if (!isConfigured()) {
            Utils.sendMessage(sender, plugin.prefix() + "§c无法发送测试消息：" + configState());
            return;
        }
        Utils.sendMessage(sender, plugin.prefix() + "§7正在发送 Webhook 测试消息...");
        final String actor = Utils.getSenderName(sender);
        SchedulerUtils.runAsync(plugin, () -> {
            String url = resolveUrl();
            Outcome outcome = url.isEmpty()
                    ? new Outcome(false, false, 0, "配置已变更，地址为空", -1L)
                    : send(url, buildPayload(new Job("测试", actor, "", "收到即代表 Webhook 配置正常", true)));
            if (outcome.ok) {
                delivered.incrementAndGet();
                lastSuccessAt = System.currentTimeMillis();
            } else {
                failed.incrementAndGet();
                recordFailure(outcome.code, outcome.error);
            }
            final boolean ok = outcome.ok;
            final String detail = outcome.error == null || outcome.error.isEmpty() ? "HTTP " + outcome.code : outcome.error;
            SchedulerUtils.runTask(plugin, () -> Utils.sendMessage(sender, ok
                    ? plugin.prefix() + "§a测试消息已送达（" + detail + "）"
                    : plugin.prefix() + "§c测试消息发送失败：" + detail));
        });
    }

    public Status status() {
        return new Status(queue.size(), queueCapacity, dropped.get(), delivered.get(), failed.get(), retried.get(),
                lastSuccessAt, lastError, lastErrorAt, isRunning(), isConfigured(), configState());
    }

    public boolean isRunning() {
        return running && !stopped;
    }

    public boolean isConfigured() {
        try {
            return plugin.isFeatureEnabled("webhook-events") && isEnabled() && !resolveUrl().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    public String configState() {
        try {
            if (!plugin.isFeatureEnabled("webhook-events")) {
                return "功能已关闭（features.webhook-events = false）";
            }
            FileConfiguration config = plugin.getConfig();
            if (config == null) {
                return "配置尚未加载";
            }
            if (!config.getBoolean("webhook.enabled", true)) {
                return "已关闭（webhook.enabled = false）";
            }
            String raw = config.getString("webhook.url", "");
            if (raw == null || raw.trim().isEmpty()) {
                return "未配置地址（webhook.url 为空）";
            }
            if (resolveUrl().isEmpty()) {
                return "地址格式无效（需以 http:// 或 https:// 开头）";
            }
            return "就绪";
        } catch (Throwable t) {
            return "配置读取失败: " + t;
        }
    }

    public void stop() {
        Thread thread;
        synchronized (this) {
            stopped = true;
            running = false;
            thread = worker;
            worker = null;
        }
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(STOP_JOIN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int remaining = queue.size();
        queue.clear();
        DownloadService client = downloads;
        downloads = null;
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        if (remaining > 0) {
            Logger logger = plugin == null ? null : plugin.getLogger();
            if (logger != null) {
                logger.warning("Webhook 已停止，剩余 " + remaining + " 条消息未投递");
            }
        }
    }

    boolean submit(String action, String actor, String target, String reason, boolean success) {
        if (stopped) {
            return false;
        }
        Job job = new Job(action, actor, target, reason, success);
        if (queue.offer(job)) {
            return true;
        }
        if (queue.poll() != null) {
            dropped.incrementAndGet();
            logThrottled("queue-full", "Webhook 队列已满（容量 " + queueCapacity + "），已丢弃最旧的一条，累计丢弃 " + dropped.get()
                    + " 条；批量操作较频繁时可调大 webhook.queue-size 或调小 webhook.min-interval-ms");
        }
        if (queue.offer(job)) {
            return true;
        }
        dropped.incrementAndGet();
        return false;
    }

    private synchronized void ensureStarted() {
        if (stopped || running) {
            return;
        }
        running = true;
        Thread thread = new Thread(this::loop, "Lengbanlist-Webhook");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> {
            Logger logger = plugin == null ? null : plugin.getLogger();
            if (logger != null) {
                logger.warning("Webhook 投递线程异常退出: " + e);
            }
        });
        worker = thread;
        thread.start();
    }

    private void loop() {
        while (running) {
            try {
                Job job = queue.poll(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (job == null) {
                    continue;
                }
                deliver(job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                failed.incrementAndGet();
                recordFailure(0, "投递异常: " + t);
            }
        }
    }

    private void deliver(Job job) {
        int attempt = 0;
        while (running) {
            if (!plugin.isFeatureEnabled("webhook-events") || !isEnabled()) {
                return;
            }
            String url = resolveUrl();
            if (url.isEmpty()) {
                return;
            }
            awaitRateLimit();
            if (!running) {
                return;
            }
            Outcome outcome = send(url, buildPayload(job));
            if (outcome.ok) {
                delivered.incrementAndGet();
                lastSuccessAt = System.currentTimeMillis();
                return;
            }
            if (!outcome.retryable || attempt >= maxRetries()) {
                failed.incrementAndGet();
                recordFailure(outcome.code, outcome.error);
                return;
            }
            long wait = outcome.retryAfterMs > 0
                    ? outcome.retryAfterMs
                    : backoffDelayMs(attempt, BASE_BACKOFF_MS, MAX_BACKOFF_MS);
            attempt++;
            retried.incrementAndGet();
            if (sleepInterruptibly(wait)) {
                return;
            }
        }
    }

    private Outcome send(String url, String body) {
        try {
            DownloadService client = downloads();
            DownloadService.Response response = client.postJsonForResponse(url, body);
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                return new Outcome(true, false, code, "", -1L);
            }
            long retryAfter = parseRetryAfterMillis(response.header("Retry-After"),
                    response.header("X-RateLimit-Reset-After"), System.currentTimeMillis());
            String detail = "HTTP " + code;
            if (code == 401 || code == 403 || code == 404) {
                detail = detail + "（请检查 webhook.url 是否有效/被删除）";
            }
            return new Outcome(false, code == 408 || code == 429 || code >= 500, code, detail, retryAfter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(false, false, 0, "投递被中断", -1L);
        } catch (Throwable t) {
            String message = t.getMessage();
            return new Outcome(false, true, 0, t.getClass().getSimpleName() + (message == null ? "" : ": " + message), -1L);
        }
    }

    private DownloadService downloads() {
        DownloadService local = downloads;
        if (local == null) {
            synchronized (this) {
                if (downloads == null) {
                    downloads = new DownloadService(
                            new DownloadSettings(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, USER_AGENT, true));
                }
                local = downloads;
            }
        }
        return local;
    }

    private void awaitRateLimit() {
        synchronized (rateLock) {
            long interval = Math.max(0L, configLong("webhook.min-interval-ms", DEFAULT_MIN_INTERVAL_MS));
            long wait = nextAllowedAt - System.currentTimeMillis();
            if (wait > 0) {
                sleepInterruptibly(wait);
            }
            nextAllowedAt = System.currentTimeMillis() + interval;
        }
    }

    private boolean sleepInterruptibly(long millis) {
        if (millis <= 0L) {
            return !running || stopped;
        }
        long deadline = System.currentTimeMillis() + millis;
        try {
            while (running && !stopped) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return false;
                }
                Thread.sleep(remaining);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    Job[] pending() {
        return queue.toArray(new Job[0]);
    }

    String buildPayload(Job job) {
        String target = truncate(job.target, FIELD_VALUE_LIMIT);
        String reason = truncate(job.reason, FIELD_VALUE_LIMIT);
        String server = truncate(plugin == null ? "" : plugin.getServerName(), FIELD_VALUE_LIMIT);
        JSONArray fields = new JSONArray();
        fields.put(field("操作", dash(truncate(job.action, FIELD_VALUE_LIMIT)), true));
        fields.put(field("操作人", dash(truncate(job.actor, FIELD_VALUE_LIMIT)), true));
        fields.put(field("目标", dash(target), true));
        fields.put(field("子服", dash(server), true));
        fields.put(field("结果", job.success ? "成功" : "失败", true));
        fields.put(field("原因", dash(reason), false));

        JSONObject embed = new JSONObject();
        embed.put("title", "Lengbanlist 审计日志");
        embed.put("color", colorFor(job.action));
        embed.put("fields", fields);
        embed.put("timestamp", Instant.now().toString());

        JSONObject payload = new JSONObject();
        String username = truncate(configString("webhook.username", "Lengbanlist"), USERNAME_LIMIT);
        if (!username.isEmpty()) {
            payload.put("username", username);
        }
        String avatar = configString("webhook.avatar-url", "");
        if (!avatar.trim().isEmpty()) {
            payload.put("avatar_url", avatar.trim());
        }
        String mention = mentionContent(job.action);
        if (!mention.isEmpty()) {
            payload.put("content", truncate(mention, CONTENT_LIMIT));
        }
        payload.put("embeds", new JSONArray().put(embed));
        return payload.toString();
    }

    private String mentionContent(String action) {
        if (!BAN_ACTIONS.contains(action)) {
            return "";
        }
        String raw = configString("webhook.mention-role", "").trim();
        if (raw.isEmpty()) {
            return "";
        }
        String id = raw.startsWith("<@&") && raw.endsWith(">") ? raw.substring(3, raw.length() - 1).trim() : raw;
        if (!ROLE_ID.matcher(id).matches()) {
            logThrottled("mention-role", "webhook.mention-role 不是有效的身份组 ID（应为纯数字，可填 <@&ID> 形式），已忽略该配置");
            return "";
        }
        return "<@&" + id + ">";
    }

    private int maxRetries() {
        long configured = configLong("webhook.max-retries", DEFAULT_MAX_RETRIES);
        return (int) Math.max(0L, Math.min(configured, MAX_RETRIES_LIMIT));
    }

    private boolean isEnabled() {
        FileConfiguration config = plugin == null ? null : plugin.getConfig();
        return config != null && config.getBoolean("webhook.enabled", true);
    }

    private String resolveUrl() {
        String url = configString("webhook.url", "");
        if (url.isEmpty()) {
            return "";
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "";
        }
        return trimmed;
    }

    private List<String> eventTypes() {
        FileConfiguration config = plugin == null ? null : plugin.getConfig();
        if (config == null) {
            return Collections.emptyList();
        }
        List<String> types = config.getStringList("webhook.event-types");
        return types == null ? Collections.emptyList() : types;
    }

    private String configString(String path, String def) {
        FileConfiguration config = plugin == null ? null : plugin.getConfig();
        if (config == null) {
            return def == null ? "" : def;
        }
        String value = config.getString(path, def);
        return value == null ? "" : value;
    }

    private long configLong(String path, long def) {
        FileConfiguration config = plugin == null ? null : plugin.getConfig();
        if (config == null) {
            return def;
        }
        try {
            return config.getLong(path, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private void recordFailure(int code, String error) {
        String detail = error == null || error.isEmpty() ? "HTTP " + code : error;
        lastError = detail;
        lastErrorAt = System.currentTimeMillis();
        logThrottled("deliver-failed", "Webhook 投递失败：" + detail + "（累计失败 " + failed.get() + " 条，丢弃 " + dropped.get() + " 条）");
    }

    private void logThrottled(String key, String message) {
        try {
            long now = System.currentTimeMillis();
            Long previous = lastLogAt.get(key);
            if (previous != null && now - previous < LOG_THROTTLE_MS) {
                return;
            }
            lastLogAt.put(key, now);
            Logger logger = plugin == null ? null : plugin.getLogger();
            if (logger != null) {
                logger.warning(message);
            }
        } catch (Throwable ignored) {
        }
    }

    static int colorFor(String action) {
        Integer color = action == null ? null : ACTION_COLORS.get(action);
        return color == null ? COLOR_DEFAULT : color;
    }

    static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (limit <= 0 || trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, limit - 1) + "…";
    }

    static String dash(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }

    static long backoffDelayMs(int attempt, long baseMs, long maxMs) {
        long base = Math.max(1L, baseMs);
        long cap = Math.max(base, maxMs);
        long delay = base;
        for (int i = 0; i < attempt && delay < cap; i++) {
            delay *= 2;
        }
        return Math.min(delay, cap);
    }

    static long parseRetryAfterMillis(String retryAfter, String resetAfter, long nowMs) {
        Long millis = parseDelayMillis(retryAfter, nowMs);
        if (millis == null) {
            millis = parseDelayMillis(resetAfter, nowMs);
        }
        if (millis == null || millis <= 0L) {
            return -1L;
        }
        return Math.min(millis, MAX_RETRY_WAIT_MS);
    }

    private static Long parseDelayMillis(String value, long nowMs) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            double seconds = Double.parseDouble(trimmed);
            if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds <= 0d) {
                return null;
            }
            return (long) Math.ceil(seconds * 1000d);
        } catch (NumberFormatException ignored) {
        }
        try {
            ZonedDateTime date = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
            return date.toInstant().toEpochMilli() - nowMs;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JSONObject field(String name, String value, boolean inline) {
        return new JSONObject().put("name", name).put("value", value).put("inline", inline);
    }

    static final class Job {
        final String action;
        final String actor;
        final String target;
        final String reason;
        final boolean success;

        Job(String action, String actor, String target, String reason, boolean success) {
            this.action = action;
            this.actor = actor;
            this.target = target;
            this.reason = reason;
            this.success = success;
        }
    }

    static final class Outcome {
        final boolean ok;
        final boolean retryable;
        final int code;
        final String error;
        final long retryAfterMs;

        Outcome(boolean ok, boolean retryable, int code, String error, long retryAfterMs) {
            this.ok = ok;
            this.retryable = retryable;
            this.code = code;
            this.error = error == null ? "" : error;
            this.retryAfterMs = retryAfterMs;
        }
    }

    public static final class Status {
        private final int queueSize;
        private final int queueCapacity;
        private final long dropped;
        private final long delivered;
        private final long failed;
        private final long retried;
        private final long lastSuccessAt;
        private final String lastError;
        private final long lastErrorAt;
        private final boolean running;
        private final boolean configured;
        private final String configState;

        Status(int queueSize, int queueCapacity, long dropped, long delivered, long failed, long retried,
               long lastSuccessAt, String lastError, long lastErrorAt, boolean running, boolean configured, String configState) {
            this.queueSize = queueSize;
            this.queueCapacity = queueCapacity;
            this.dropped = dropped;
            this.delivered = delivered;
            this.failed = failed;
            this.retried = retried;
            this.lastSuccessAt = lastSuccessAt;
            this.lastError = lastError == null ? "" : lastError;
            this.lastErrorAt = lastErrorAt;
            this.running = running;
            this.configured = configured;
            this.configState = configState == null ? "" : configState;
        }

        public int queueSize() {
            return queueSize;
        }

        public int queueCapacity() {
            return queueCapacity;
        }

        public long dropped() {
            return dropped;
        }

        public long delivered() {
            return delivered;
        }

        public long failed() {
            return failed;
        }

        public long retried() {
            return retried;
        }

        public long lastSuccessAt() {
            return lastSuccessAt;
        }

        public String lastError() {
            return lastError;
        }

        public long lastErrorAt() {
            return lastErrorAt;
        }

        public boolean isRunning() {
            return running;
        }

        public boolean isConfigured() {
            return configured;
        }

        public String configState() {
            return configState;
        }
    }
}

package org.leng.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.leng.Lengbanlist;
import org.leng.utils.HttpHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class ModelCloudManager {

    private static final String DEFAULT_REPO = "Serendisand/Lengbanlist-Models";
    private static final String DEFAULT_BRANCH = "main";
    private static final long INDEX_CACHE_TTL_MS = 24L * 60 * 60 * 1000;
    private static final int HTTP_TIMEOUT_MS = 5000;
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Lengbanlist plugin;
    private final AtomicReference<ModelIndex> cachedIndex = new AtomicReference<>();
    private volatile long cacheLoadedAt = 0;

    public ModelCloudManager(Lengbanlist plugin) {
        this.plugin = plugin;
    }

    public record ModelInfo(String id, String name, String version, String author, String url, String sha256) {

        public static ModelInfo fromJson(JsonObject obj) {
            return new ModelInfo(
                    str(obj, "id"),
                    str(obj, "name"),
                    str(obj, "version"),
                    str(obj, "author"),
                    str(obj, "url"),
                    str(obj, "sha256")
            );
        }
    }

    public record FeaturedModel(String month, String modelId, String title, String description) {

        public static FeaturedModel fromJson(JsonObject obj) {
            return new FeaturedModel(
                    str(obj, "month"),
                    str(obj, "modelId"),
                    str(obj, "title"),
                    str(obj, "description")
            );
        }
    }

    public record ModelIndex(int version, String updated, List<ModelInfo> models, FeaturedModel featured) {

        public static ModelIndex fromJson(JsonObject obj) {
            int version = obj.has("version") ? obj.get("version").getAsInt() : 1;
            String updated = str(obj, "updated");
            List<ModelInfo> models = new ArrayList<>();
            if (obj.has("models") && obj.get("models").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("models");
                for (JsonElement el : arr) {
                    if (el.isJsonObject()) {
                        models.add(ModelInfo.fromJson(el.getAsJsonObject()));
                    }
                }
            }
            FeaturedModel featured = obj.has("featured") && obj.get("featured").isJsonObject()
                    ? FeaturedModel.fromJson(obj.getAsJsonObject("featured"))
                    : null;
            return new ModelIndex(version, updated, models, featured);
        }
    }

    private static final java.util.regex.Pattern ID_PATTERN =
            java.util.regex.Pattern.compile("[a-z0-9-]{1,32}");

    private static final String HTTPS_PREFIX = "https://";

    private static boolean isAllowedUrl(String url) {
        return url != null && (url.startsWith(HTTPS_PREFIX)
                || url.startsWith("http://127.0.0.1")
                || url.startsWith("http://localhost"));
    }

    public static boolean isValidModelId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    public String repo() {
        String repo = plugin.getConfig().getString("models-cloud.repo", DEFAULT_REPO);

        if (repo == null || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            repo = DEFAULT_REPO;
        }
        return repo;
    }

    public String branch() {
        String branch = plugin.getConfig().getString("models-cloud.branch", DEFAULT_BRANCH);
        if (branch == null || !branch.matches("[A-Za-z0-9_.-]+")) {
            branch = DEFAULT_BRANCH;
        }
        return branch;
    }

    public List<String> mirrors() {
        List<String> list = plugin.getConfig().getStringList("models-cloud.mirrors");
        if (list == null || list.isEmpty()) {

            String baseRaw = "https://raw.githubusercontent.com/" + repo() + "/" + branch() + "/index.json";
            list = List.of(
                    baseRaw,
                    "https://gh-proxy.com/" + baseRaw,
                    "https://mirror.ghproxy.com/" + baseRaw
            );
        } else {

            list = list.stream().filter(ModelCloudManager::isAllowedUrl).toList();
        }
        return list;
    }

    public Optional<ModelIndex> fetchIndex() {
        for (String url : mirrors()) {
            try (HttpHelper http = new HttpHelper(HTTP_TIMEOUT_MS, HTTP_TIMEOUT_MS)) {
                String body = http.get(url, "Lengbanlist-ModelCloud/1.0", "application/json");
                JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                ModelIndex index = ModelIndex.fromJson(obj);
                cachedIndex.set(index);
                cacheLoadedAt = System.currentTimeMillis();
                writeIndexCache(body);
                return Optional.of(index);
            } catch (IOException | InterruptedException e) {
                plugin.getLogger().log(Level.WARNING, "[ModelCloud] 镜像拉取失败: " + url + " — " + e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[ModelCloud] 解析失败: " + url + " — " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    public CompletableFuture<Optional<ModelIndex>> fetchIndexAsync() {
        return CompletableFuture.supplyAsync(this::fetchIndex);
    }

    public Optional<ModelIndex> cachedIndexOnly() {
        ModelIndex mem = cachedIndex.get();
        if (mem != null) {
            return Optional.of(mem);
        }
        return readIndexCache();
    }

    public Optional<ModelIndex> getIndex() {
        ModelIndex mem = cachedIndex.get();
        if (mem != null && System.currentTimeMillis() - cacheLoadedAt < INDEX_CACHE_TTL_MS) {
            return Optional.of(mem);
        }
        Optional<ModelIndex> disk = readIndexCache();
        if (disk.isPresent()) {
            cachedIndex.set(disk.get());
            cacheLoadedAt = System.currentTimeMillis();
            return disk;
        }
        return fetchIndex();
    }

    private Path cacheFile() {
        return plugin.getDataFolder().toPath().resolve("models/.cache/index.json");
    }

    private Path statsFile() {
        return plugin.getDataFolder().toPath().resolve("models/.cache/stats.json");
    }

    public void recordInstall(String id) {
        try {
            Path p = statsFile();
            Files.createDirectories(p.getParent());
            com.google.gson.JsonObject stats;
            if (Files.exists(p)) {
                stats = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            } else {
                stats = new com.google.gson.JsonObject();
            }
            int count = stats.has(id) ? stats.get(id).getAsInt() : 0;
            stats.addProperty(id, count + 1);
            Files.writeString(p, stats.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "[ModelCloud] 记录安装统计失败", e);
        }
        reportInstallAsync(id);
    }

    private String serverId() {
        Path p = plugin.getDataFolder().toPath().resolve("models/.cache/server-id");
        try {
            if (Files.exists(p)) {
                return Files.readString(p, StandardCharsets.UTF_8).trim();
            }
            String id = java.util.UUID.randomUUID().toString();
            Files.createDirectories(p.getParent());
            Files.writeString(p, id, StandardCharsets.UTF_8);
            return id;
        } catch (IOException e) {
            return "unknown";
        }
    }

    private boolean statsReportingEnabled() {
        if (!plugin.getConfig().getBoolean("models-cloud.stats.enabled", true)) {
            return false;
        }
        String url = plugin.getConfig().getString("models-cloud.stats.url", "");
        return url != null && !url.trim().isEmpty();
    }

    private void reportInstallAsync(String modelId) {
        if (!statsReportingEnabled()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            String url = plugin.getConfig().getString("models-cloud.stats.url", "");
            try (HttpHelper http = new HttpHelper(5000, 5000)) {
                JsonObject payload = new JsonObject();
                payload.addProperty("server", serverId());
                payload.addProperty("model", modelId);
                payload.addProperty("version", plugin.getPluginVersion());
                http.postJson(url, payload.toString(), "Lengbanlist-Stats/1.0");
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "[ModelCloud] 安装统计上报失败(不影响使用)", e);
            }
        });
    }

    public List<String[]> downloadStats() {
        List<String[]> result = new ArrayList<>();
        try {
            Path p = statsFile();
            if (!Files.exists(p)) {
                return result;
            }
            com.google.gson.JsonObject stats = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            stats.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().getAsInt(), a.getValue().getAsInt()))
                    .forEach(e -> result.add(new String[]{e.getKey(), String.valueOf(e.getValue().getAsInt())}));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[ModelCloud] 读取安装统计失败", e);
        }
        return result;
    }

    private void writeIndexCache(String body) {
        try {
            Path p = cacheFile();
            Files.createDirectories(p.getParent());
            Files.writeString(p, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[ModelCloud] 写入索引缓存失败", e);
        }
    }

    private Optional<ModelIndex> readIndexCache() {
        Path p = cacheFile();
        if (!Files.exists(p)) {
            return Optional.empty();
        }
        try {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            return Optional.of(ModelIndex.fromJson(obj));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[ModelCloud] 读取索引缓存失败", e);
            return Optional.empty();
        }
    }

    public boolean isFeaturedForCurrentMonth(FeaturedModel featured) {
        if (featured == null || featured.month() == null) return false;
        return featured.month().equals(currentMonth());
    }

    public Optional<FeaturedModel> currentFeatured() {
        return getIndex().map(ModelIndex::featured).filter(this::isFeaturedForCurrentMonth);
    }

    public static String currentMonth() {
        return LocalDate.now().format(MONTH_FMT);
    }

    private static String str(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : null;
    }

    public Path localModelsDir() {
        return plugin.getDataFolder().toPath().resolve("models");
    }

    private Path modelFile(String id) {
        return localModelsDir().resolve(id + ".yml");
    }

    private static final String META_PREFIX_PINNED = "pinned: ";
    private static final String META_PREFIX_VERSION = "version: ";
    private static final String META_COMMENT = "# 以下元数据由 Lengbanlist 自动维护（pinned=锁定,version=云端版本）";

    private String readMetaValue(String id, String prefix) {
        Path f = modelFile(id);
        if (!Files.exists(f)) {
            return "";
        }
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (line.startsWith(prefix)) {
                    return line.substring(prefix.length()).trim();
                }
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    private boolean setMetaValue(String id, String prefix, String value) {
        Path f = modelFile(id);
        if (!Files.exists(f)) {
            return false;
        }
        try {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(prefix)) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                lines.set(idx, prefix + value);
            } else {
                boolean hasMeta = lines.stream().anyMatch(l ->
                        l.startsWith(META_PREFIX_PINNED) || l.startsWith(META_PREFIX_VERSION));
                if (!hasMeta) {
                    lines.add("");
                    lines.add(META_COMMENT);
                }
                lines.add(prefix + value);
            }
            Files.write(f, lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[ModelCloud] 写入元数据失败: " + id, e);
            return false;
        }
    }

    public boolean isPinned(String id) {
        return "true".equals(readMetaValue(id, META_PREFIX_PINNED));
    }

    public boolean pin(String id) {
        return setMetaValue(id, META_PREFIX_PINNED, "true");
    }

    public boolean unpin(String id) {
        Path f = modelFile(id);
        if (!Files.exists(f)) {
            return true;
        }
        return setMetaValue(id, META_PREFIX_PINNED, "false");
    }

    public boolean isInstalled(String id) {
        return Files.exists(modelFile(id));
    }

    public Optional<ModelInfo> findInIndex(String id) {
        Optional<ModelIndex> idx = getIndex();
        if (idx.isEmpty()) {
            return Optional.empty();
        }
        String lower = id.toLowerCase();
        return idx.get().models().stream()
                .filter(m -> m.id().equalsIgnoreCase(lower))
                .findFirst();
    }

    public InstallResult installModel(String id) {
        if (!isValidModelId(id)) {
            return InstallResult.NOT_FOUND;
        }
        String lower = id.toLowerCase();
        if (isPinned(lower)) {
            return InstallResult.PINNED_SKIPPED;
        }
        Optional<ModelInfo> found = findInIndex(lower);
        if (found.isEmpty()) {
            return InstallResult.NOT_FOUND;
        }
        if (isPlaceholder(found.get())) {
            return InstallResult.NOT_FOUND;
        }
        if (isInstalled(lower) && isCurrent(lower, found.get().version())) {
            return InstallResult.ALREADY_INSTALLED;
        }
        return downloadModel(found.get());
    }

    public int syncAll() {
        Optional<ModelIndex> idx = getIndex();
        if (idx.isEmpty()) {
            return 0;
        }
        int installed = 0;
        for (ModelInfo info : idx.get().models()) {
            if (isPinned(info.id()) || isPlaceholder(info)) {
                continue;
            }
            if (isInstalled(info.id()) && isCurrent(info.id(), info.version())) {
                continue;
            }
            if (downloadModel(info) == InstallResult.INSTALLED) {
                installed++;
            }
        }
        return installed;
    }

    private boolean isPlaceholder(ModelInfo info) {
        return info.version() == null || info.version().isEmpty() || "0.0.0".equals(info.version());
    }

    private boolean isCurrent(String id, String version) {
        return version != null && !version.isEmpty() && version.equals(readMetaValue(id, META_PREFIX_VERSION));
    }

    private InstallResult downloadModel(ModelInfo info) {
        if (info.id() == null || info.id().isEmpty()) {
            return InstallResult.FAILED;
        }
        for (String url : modelDownloadCandidates(info)) {

            if (!isAllowedUrl(url)) {
                continue;
            }
            try (HttpHelper http = new HttpHelper(HTTP_TIMEOUT_MS, HTTP_TIMEOUT_MS)) {
                String body = http.get(url, "Lengbanlist-ModelCloud/1.0", "text/yaml");

                if (!body.contains("name:")) {
                    plugin.getLogger().warning("[ModelCloud] 模型 " + info.id() + " 下载内容缺少 name 字段,拒绝安装 (" + url + ")");
                    return InstallResult.FAILED;
                }
                Path target = modelFile(info.id());
                Files.createDirectories(target.getParent());
                Files.writeString(target, body, StandardCharsets.UTF_8);
                if (info.version() != null && !info.version().isEmpty()) {
                    setMetaValue(info.id(), META_PREFIX_VERSION, info.version());
                }
                plugin.getLogger().info("[ModelCloud] 模型已安装: " + info.id() + " (" + info.name() + " v" + info.version() + ") 来源 " + url);
                recordInstall(info.id());
                return InstallResult.INSTALLED;
            } catch (IOException | InterruptedException e) {
                plugin.getLogger().log(Level.FINE, "[ModelCloud] 下载候选失败: " + url + " — " + e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return InstallResult.FAILED;
                }
            }
        }
        plugin.getLogger().warning("[ModelCloud] 模型下载失败: " + info.id() + "（所有镜像均不可用）");
        return InstallResult.FAILED;
    }

    List<String> modelDownloadCandidates(ModelInfo info) {
        List<String> result = new ArrayList<>();
        for (String indexUrl : mirrors()) {
            String base = indexUrl.endsWith("/index.json")
                    ? indexUrl.substring(0, indexUrl.length() - "index.json".length())
                    : (indexUrl.endsWith("/") ? indexUrl : indexUrl + "/");
            result.add(base + "models/" + info.id() + "/" + info.id() + ".yml");
        }
        if (info.url() != null && !info.url().isEmpty() && !result.contains(info.url())) {
            result.add(info.url());
        }
        return result;
    }

    public enum InstallResult {
        INSTALLED,
        ALREADY_INSTALLED,
        PINNED_SKIPPED,
        NOT_FOUND,
        FAILED
    }
}

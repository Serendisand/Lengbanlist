package org.leng.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.leng.Lengbanlist;
import org.leng.download.DownloadService;
import org.leng.download.DownloadSettings;
import org.leng.download.MirrorChain;
import org.leng.download.MirrorSpec;
import org.leng.utils.HttpHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final String USER_AGENT = "Lengbanlist-ModelCloud/1.0";
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Lengbanlist plugin;
    private final DownloadService downloads;
    private final AtomicReference<ModelIndex> cachedIndex = new AtomicReference<>();
    private volatile long cacheLoadedAt = 0;
    /** 当前任务的镜像链。每个任务重建，因此失败记忆是"按任务"的。 */
    private volatile MirrorChain chain;

    public ModelCloudManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.downloads = new DownloadService(DownloadSettings.from(plugin.getConfig(), USER_AGENT));
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
        // 统一配置（download.mirrors.models）优先；未配置时回退到既有的
        // models-cloud.mirrors，再回退到内置默认链。
        List<String> unified = DownloadSettings.mirrors(plugin.getConfig(), "models")
                .stream().map(MirrorSpec::url).toList();
        if (!unified.isEmpty()) {
            return unified.stream().filter(ModelCloudManager::isAllowedUrl).toList();
        }
        List<String> list = plugin.getConfig().getStringList("models-cloud.mirrors");
        if (list != null && !list.isEmpty()) {
            return list.stream().filter(ModelCloudManager::isAllowedUrl).toList();
        }
        String baseRaw = "https://raw.githubusercontent.com/" + repo() + "/" + branch() + "/index.json";
        return List.of(
                baseRaw,
                "https://gh-proxy.com/" + baseRaw,
                "https://mirror.ghproxy.com/" + baseRaw
        );
    }

    /** 开始一个新任务：按当前配置重建镜像链，清空上一任务的失败记忆。 */
    private MirrorChain beginTask() {
        MirrorChain fresh = new MirrorChain(toSpecs(mirrors()));
        this.chain = fresh;
        return fresh;
    }

    /** 取当前任务的镜像链；尚未开始任务时立刻开始一个。 */
    private MirrorChain chain() {
        MirrorChain current = chain;
        return current != null ? current : beginTask();
    }

    private static List<MirrorSpec> toSpecs(List<String> urls) {
        List<MirrorSpec> specs = new ArrayList<>(urls.size());
        for (String url : urls) {
            if (isAllowedUrl(url)) {
                specs.add(MirrorSpec.custom(url));
            }
        }
        return specs;
    }

    private static boolean isAllowedUrl(String url) {
        return DownloadService.isAllowedUrl(url);
    }

    static boolean isUnreachableFailure(Exception error) {
        return MirrorChain.isUnreachableFailure(error);
    }

    public Optional<ModelIndex> fetchIndex() {
        MirrorChain task = beginTask();
        for (String url : task.filterUnreachable(mirrors())) {
            try {
                String body = downloads.get(url, "application/json");
                JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                ModelIndex index = ModelIndex.fromJson(obj);
                cachedIndex.set(index);
                cacheLoadedAt = System.currentTimeMillis();
                writeIndexCache(body);
                return Optional.of(index);
            } catch (IOException | InterruptedException e) {
                if (task.noteFailure(url, e)) {
                    plugin.getLogger().info("[ModelCloud] 镜像源 " + MirrorSpec.hostOf(url)
                            + " 本次任务不可达（" + e.getMessage() + "），后续请求将跳过它；下次任务仍会先试它。");
                }
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
        if (disk.isPresent() && cacheFileFresh()) {
            cachedIndex.set(disk.get());
            cacheLoadedAt = System.currentTimeMillis();
            return disk;
        }
        Optional<ModelIndex> fetched = fetchIndex();
        if (fetched.isPresent()) {
            return fetched;
        }
        if (disk.isPresent()) {
            cachedIndex.set(disk.get());
            cacheLoadedAt = System.currentTimeMillis();
            return disk;
        }
        return Optional.empty();
    }

    private boolean cacheFileFresh() {
        try {
            Path p = cacheFile();
            return Files.exists(p)
                    && System.currentTimeMillis() - Files.getLastModifiedTime(p).toMillis() < INDEX_CACHE_TTL_MS;
        } catch (IOException e) {
            return false;
        }
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
                .filter(m -> m.id() != null && m.id().equalsIgnoreCase(lower))
                .findFirst();
    }

    public InstallResult installModel(String id) {
        if (!isValidModelId(id)) {
            return InstallResult.NOT_FOUND;
        }
        beginTask();
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
        beginTask();
        Optional<ModelIndex> idx = getIndex();
        if (idx.isEmpty()) {
            return 0;
        }
        int installed = 0;
        for (ModelInfo info : idx.get().models()) {
            String id = info.id() == null ? "" : info.id().trim().toLowerCase(java.util.Locale.ROOT);
            if (!isValidModelId(id) || isPinned(id) || isPlaceholder(info)) {
                continue;
            }
            if (isInstalled(id) && isCurrent(id, info.version())) {
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
        String id = info.id() == null ? "" : info.id().trim().toLowerCase(java.util.Locale.ROOT);
        if (!isValidModelId(id)) {
            plugin.getLogger().warning("[ModelCloud] 云端索引里的模型 id 非法,已跳过: " + info.id());
            return InstallResult.FAILED;
        }
        Path root = localModelsDir().toAbsolutePath().normalize();
        Path target = root.resolve(id + ".yml").normalize();
        if (!target.startsWith(root)) {
            plugin.getLogger().warning("[ModelCloud] 模型 " + id + " 的写入路径越界,已拒绝");
            return InstallResult.FAILED;
        }
        MirrorChain task = chain();
        Path staged = target.resolveSibling(id + ".yml.staged");
        for (String url : task.filterUnreachable(modelDownloadCandidates(info))) {

            if (!isAllowedUrl(url)) {
                continue;
            }
            try {
                // 先落到 staged：索引给了 sha256 就校验，通过后才改名到正式文件，
                // 避免半截内容或被篡改的模型进入 models/。
                DownloadService.DownloadResult result =
                        downloads.downloadToFile(url, staged, info.sha256(), "text/yaml");

                if (!result.ok()) {
                    plugin.getLogger().warning("[ModelCloud] 模型 " + id
                            + " 校验未通过,拒绝安装 (" + url + "): " + result.error());
                    continue;
                }

                String body = Files.readString(staged, StandardCharsets.UTF_8);
                if (!body.contains("name:")) {
                    plugin.getLogger().warning("[ModelCloud] 模型 " + id + " 下载内容缺少 name 字段,拒绝安装 (" + url + ")");
                    return InstallResult.FAILED;
                }
                Files.createDirectories(target.getParent());
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                if (info.version() != null && !info.version().isEmpty()) {
                    setMetaValue(id, META_PREFIX_VERSION, info.version());
                }
                plugin.getLogger().info("[ModelCloud] 模型已安装: " + id + " (" + info.name() + " v" + info.version() + ") 来源 " + url);
                recordInstall(id);
                return InstallResult.INSTALLED;
            } catch (IOException | InterruptedException e) {
                if (task.noteFailure(url, e)) {
                    plugin.getLogger().info("[ModelCloud] 镜像源 " + MirrorSpec.hostOf(url)
                            + " 本次任务不可达（" + e.getMessage() + "），后续请求将跳过它。");
                }
                plugin.getLogger().log(Level.FINE, "[ModelCloud] 下载候选失败: " + url + " — " + e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return InstallResult.FAILED;
                }
            }
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException ignored) {
            // 清理失败不影响返回值
        }
        plugin.getLogger().warning("[ModelCloud] 模型下载失败: " + id + "（所有镜像均不可用）");
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

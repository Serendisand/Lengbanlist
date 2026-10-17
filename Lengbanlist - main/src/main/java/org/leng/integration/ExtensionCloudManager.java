package org.leng.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.leng.Lengbanlist;
import org.leng.api.ApiVersion;
import org.leng.extension.ExtensionInstallRecord;
import org.leng.net.DownloadService;
import org.leng.net.DownloadSettings;
import org.leng.net.MirrorChain;
import org.leng.net.MirrorSpec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * 云端扩展市场：拉索引、下载 jar / Lua 扩展。
 *
 * <p>结构照搬 {@link ModelCloudManager}：镜像链 + 24 小时索引缓存 + sha256 校验。
 * 区别只在产物类型：
 *
 * <ul>
 *   <li>{@code jar}：下载到 {@code plugins/<扩展名>.jar}，<b>重启服务器后生效</b>；</li>
 *   <li>{@code lua}：下载到 {@code plugins/Lengbanlist/extensions/<扩展名>.lua}，
 *       配置落到同名子目录，下载完成后由命令层触发 {@code LuaLoader.reload()}。</li>
 * </ul>
 *
 * <p>安全边界：sha256 + HTTPS 只保证"产物没被篡改"，不保证扩展本身没有恶意行为；
 * jar 会额外检查 {@code plugin.yml} 里是否声明了 {@code depend: [Lengbanlist]}，
 * 至少挡住"随便一个 jar 被塞进来"的低级错误。
 */
public final class ExtensionCloudManager {

    private static final String DEFAULT_REPO = "Serendisand/Lengbanlist-Extensions";
    private static final String DEFAULT_BRANCH = "main";
    private static final long INDEX_CACHE_TTL_MS = 24L * 60 * 60 * 1000;
    private static final String USER_AGENT = "Lengbanlist-ExtensionCloud/1.0";

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9-]{1,32}");
    private static final Pattern FILE_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,128}\\.(jar|lua)");
    private static final Pattern CONFIG_PATTERN = Pattern.compile("[a-z0-9-]{1,32}/config\\.yml");

    private final Lengbanlist plugin;
    private final DownloadService downloads;
    private final ExtensionInstallRecord installRecord;
    private final AtomicReference<ExtensionIndex> cachedIndex = new AtomicReference<>();
    private volatile long cacheLoadedAt = 0;
    private volatile MirrorChain chain;

    public ExtensionCloudManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.downloads = new DownloadService(DownloadSettings.from(plugin.getConfig(), USER_AGENT));
        this.installRecord = new ExtensionInstallRecord(plugin);
    }

    // ------------------------------------------------------------ 索引模型

    public enum Kind {
        JAR,
        LUA;

        static Kind parse(String raw) {
            if (raw == null) {
                return null;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "jar" -> JAR;
                case "lua" -> LUA;
                default -> null;
            };
        }
    }

    public record ExtensionInfo(String id, Kind kind, String name, String author, String version,
                                String requiredApi, String minCore, String url, String file,
                                String config, String sha256, String description) {

        static ExtensionInfo fromJson(JsonObject obj) {
            Kind kind = Kind.parse(str(obj, "type"));
            String id = str(obj, "id");
            if (kind == null || id == null || !isValidId(id)) {
                return null;
            }
            return new ExtensionInfo(
                    id.toLowerCase(Locale.ROOT),
                    kind,
                    str(obj, "name"),
                    str(obj, "author"),
                    str(obj, "version"),
                    str(obj, "requiredApi"),
                    str(obj, "minCore"),
                    str(obj, "url"),
                    str(obj, "file"),
                    str(obj, "config"),
                    str(obj, "sha256"),
                    str(obj, "description")
            );
        }

        /** 占位版本（0.0.0 / 空）表示"还没发布产物"，不参与安装。 */
        public boolean ready() {
            return version != null && !version.isBlank() && !"0.0.0".equals(version);
        }
    }

    public record ExtensionIndex(int schemaVersion, String updated, List<ExtensionInfo> extensions) {

        static ExtensionIndex fromJson(JsonObject obj) {
            int schema = obj.has("schemaVersion") ? obj.get("schemaVersion").getAsInt() : 1;
            String updated = str(obj, "updated");
            List<ExtensionInfo> extensions = new ArrayList<>();
            if (obj.has("extensions") && obj.get("extensions").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("extensions");
                for (JsonElement el : arr) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    ExtensionInfo info = ExtensionInfo.fromJson(el.getAsJsonObject());
                    if (info != null) {
                        extensions.add(info);
                    }
                }
            }
            return new ExtensionIndex(schema, updated, extensions);
        }
    }

    // ------------------------------------------------------------ 结果

    public enum InstallResult {
        INSTALLED,
        ALREADY_INSTALLED,
        NOT_READY,
        NOT_FOUND,
        INCOMPATIBLE,
        FAILED
    }

    public record InstallOutcome(InstallResult result, Kind kind, String target, String detail) {
        public boolean installed() {
            return result == InstallResult.INSTALLED;
        }
    }

    public record BatchOutcome(int installed, int skipped, int failed, boolean luaInstalled, String detail) {
    }

    // ------------------------------------------------------------ 配置与镜像

    public boolean enabled() {
        return plugin.getConfig().getBoolean("extensions-cloud.enabled", true);
    }

    public String repo() {
        String repo = plugin.getConfig().getString("extensions-cloud.repo", DEFAULT_REPO);
        if (repo == null || !repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            repo = DEFAULT_REPO;
        }
        return repo;
    }

    public String branch() {
        String branch = plugin.getConfig().getString("extensions-cloud.branch", DEFAULT_BRANCH);
        if (branch == null || !branch.matches("[A-Za-z0-9_.-]+")) {
            branch = DEFAULT_BRANCH;
        }
        return branch;
    }

    public List<String> mirrors() {
        List<String> unified = DownloadSettings.mirrors(plugin.getConfig(), "extensions")
                .stream().map(MirrorSpec::url).toList();
        if (!unified.isEmpty()) {
            return unified.stream().filter(DownloadService::isAllowedUrl).toList();
        }
        List<String> list = plugin.getConfig().getStringList("extensions-cloud.mirrors");
        if (list != null && !list.isEmpty()) {
            return list.stream().filter(DownloadService::isAllowedUrl).toList();
        }
        String baseRaw = "https://raw.githubusercontent.com/" + repo() + "/" + branch() + "/index.json";
        return List.of(
                baseRaw,
                "https://cdn.jsdelivr.net/gh/" + repo() + "@" + branch() + "/index.json",
                "https://gh-proxy.com/" + baseRaw,
                "https://mirror.ghproxy.com/" + baseRaw
        );
    }

    private MirrorChain beginTask() {
        MirrorChain fresh = new MirrorChain(toSpecs(mirrors()));
        this.chain = fresh;
        return fresh;
    }

    private MirrorChain chain() {
        MirrorChain current = chain;
        return current != null ? current : beginTask();
    }

    private static List<MirrorSpec> toSpecs(List<String> urls) {
        List<MirrorSpec> specs = new ArrayList<>(urls.size());
        for (String url : urls) {
            if (DownloadService.isAllowedUrl(url)) {
                specs.add(MirrorSpec.custom(url));
            }
        }
        return specs;
    }

    // ------------------------------------------------------------ 索引读取

    public Optional<ExtensionIndex> fetchIndex() {
        MirrorChain task = beginTask();
        for (String url : task.filterUnreachable(mirrors())) {
            try {
                String body = downloads.get(url, "application/json");
                ExtensionIndex index = ExtensionIndex.fromJson(JsonParser.parseString(body).getAsJsonObject());
                cachedIndex.set(index);
                cacheLoadedAt = System.currentTimeMillis();
                writeIndexCache(body);
                return Optional.of(index);
            } catch (IOException | InterruptedException e) {
                if (task.noteFailure(url, e)) {
                    plugin.getLogger().info("[ExtCloud] 镜像源 " + MirrorSpec.hostOf(url)
                            + " 本次任务不可达（" + e.getMessage() + "），后续请求将跳过它。");
                }
                plugin.getLogger().log(Level.WARNING, "[ExtCloud] 镜像拉取失败: " + url + " — " + e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[ExtCloud] 索引解析失败: " + url + " — " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    public CompletableFuture<Optional<ExtensionIndex>> fetchIndexAsync() {
        return CompletableFuture.supplyAsync(this::fetchIndex);
    }

    public Optional<ExtensionIndex> cachedIndexOnly() {
        ExtensionIndex mem = cachedIndex.get();
        if (mem != null) {
            return Optional.of(mem);
        }
        return readIndexCache();
    }

    public Optional<ExtensionIndex> getIndex() {
        ExtensionIndex mem = cachedIndex.get();
        if (mem != null && System.currentTimeMillis() - cacheLoadedAt < INDEX_CACHE_TTL_MS) {
            return Optional.of(mem);
        }
        Optional<ExtensionIndex> disk = readIndexCache();
        if (disk.isPresent() && cacheFileFresh()) {
            cachedIndex.set(disk.get());
            cacheLoadedAt = System.currentTimeMillis();
            return disk;
        }
        Optional<ExtensionIndex> fetched = fetchIndex();
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

    public Optional<ExtensionInfo> findInIndex(String id) {
        if (!isValidId(id)) {
            return Optional.empty();
        }
        return getIndex().flatMap(index -> index.extensions().stream()
                .filter(info -> info.id().equalsIgnoreCase(id))
                .findFirst());
    }

    private Path cacheFile() {
        return plugin.getDataFolder().toPath().resolve("extensions/.cache/index.json");
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

    private void writeIndexCache(String body) {
        try {
            Path p = cacheFile();
            Files.createDirectories(p.getParent());
            Files.writeString(p, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[ExtCloud] 写入索引缓存失败", e);
        }
    }

    private Optional<ExtensionIndex> readIndexCache() {
        Path p = cacheFile();
        if (!Files.exists(p)) {
            return Optional.empty();
        }
        try {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            return Optional.of(ExtensionIndex.fromJson(JsonParser.parseString(body).getAsJsonObject()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[ExtCloud] 读取索引缓存失败", e);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------ 安装

    public Path pluginsDir() {
        return plugin.getDataFolder().toPath().getParent();
    }

    public Path extensionsDir() {
        return plugin.getDataFolder().toPath().resolve("extensions");
    }

    public Path targetOf(ExtensionInfo info) {
        return info.kind() == Kind.JAR
                ? pluginsDir().resolve(info.id() + ".jar")
                : extensionsDir().resolve(info.id() + ".lua");
    }

    public boolean isInstalled(String id) {
        return findInIndex(id).map(this::isInstalled).orElse(false);
    }

    public boolean isInstalled(ExtensionInfo info) {
        return Files.isRegularFile(targetOf(info));
    }

    public InstallOutcome install(String id) {
        if (!isValidId(id)) {
            return new InstallOutcome(InstallResult.NOT_FOUND, null, null, "扩展 id 非法");
        }
        beginTask();
        Optional<ExtensionInfo> found = findInIndex(id);
        if (found.isEmpty()) {
            return new InstallOutcome(InstallResult.NOT_FOUND, null, null, "云端索引里没有这个扩展");
        }
        ExtensionInfo info = found.get();
        if (!info.ready()) {
            return new InstallOutcome(InstallResult.NOT_READY, info.kind(), null, "云端尚未上架（占位版本）");
        }
        String incompatibility = incompatibility(info);
        if (incompatibility != null) {
            return new InstallOutcome(InstallResult.INCOMPATIBLE, info.kind(), null, incompatibility);
        }
        boolean installed = isInstalled(info);
        if (installed && !isManaged(info)) {
            return new InstallOutcome(InstallResult.FAILED, info.kind(), targetOf(info).toString(),
                    "已存在同名文件但不是市场安装的，为避免覆盖你的改动已跳过；如需更新请先手动删除");
        }
        if (installed && isCurrent(info)) {
            return new InstallOutcome(InstallResult.ALREADY_INSTALLED, info.kind(), targetOf(info).toString(),
                    "本地已是 " + info.version());
        }
        return info.kind() == Kind.JAR ? installJar(info) : installLua(info);
    }

    public BatchOutcome installAll() {
        beginTask();
        Optional<ExtensionIndex> index = getIndex();
        if (index.isEmpty()) {
            return new BatchOutcome(0, 0, 0, false, "云端索引不可用");
        }
        int installed = 0;
        int skipped = 0;
        int failed = 0;
        boolean luaInstalled = false;
        for (ExtensionInfo info : index.get().extensions()) {
            if (!info.ready()) {
                skipped++;
                continue;
            }
            InstallOutcome outcome = install(info.id());
            switch (outcome.result()) {
                case INSTALLED -> {
                    installed++;
                    luaInstalled |= outcome.kind() == Kind.LUA;
                }
                case ALREADY_INSTALLED -> skipped++;
                default -> failed++;
            }
        }
        return new BatchOutcome(installed, skipped, failed, luaInstalled, "");
    }

    private String incompatibility(ExtensionInfo info) {
        String coreVersion = plugin.getPluginVersion();
        if (info.requiredApi() != null && !info.requiredApi().isBlank()
                && !ApiVersion.satisfies(info.requiredApi(), coreVersion)) {
            return "需要契约 " + info.requiredApi() + "，当前核心 " + coreVersion;
        }
        if (info.minCore() != null && !info.minCore().isBlank()
                && !ApiVersion.satisfies(info.minCore(), coreVersion)) {
            return "需要核心 " + info.minCore() + " 及以上，当前 " + coreVersion;
        }
        return null;
    }

    private InstallOutcome installJar(ExtensionInfo info) {
        Path root = pluginsDir().toAbsolutePath().normalize();
        Path target = targetOf(info).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            return new InstallOutcome(InstallResult.FAILED, Kind.JAR, null, "写入路径越界，已拒绝");
        }
        if (blank(info.sha256())) {
            return new InstallOutcome(InstallResult.FAILED, Kind.JAR, null,
                    "索引没有提供 sha256，出于安全考虑拒绝安装");
        }
        String file = validJarFile(info) ? info.file() : info.id() + ".jar";
        List<String> urls = candidates(info.url(), file);
        if (urls.isEmpty()) {
            return new InstallOutcome(InstallResult.FAILED, Kind.JAR, null, "索引没有可用的下载地址");
        }

        Path staged = target.resolveSibling(target.getFileName() + ".download");
        MirrorChain task = chain();
        for (String url : task.filterUnreachable(urls)) {
            try {
                DownloadService.DownloadResult result =
                        downloads.downloadToFile(url, staged, info.sha256(), "application/java-archive");
                if (!result.ok()) {
                    plugin.getLogger().warning("[ExtCloud] 扩展 " + info.id() + " 校验未通过 (" + url + "): " + result.error());
                    continue;
                }
                String problem = validateJar(staged);
                if (problem != null) {
                    Files.deleteIfExists(staged);
                    return new InstallOutcome(InstallResult.FAILED, Kind.JAR, null, problem);
                }
                try {
                    Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException locked) {
                    // Windows 上正在使用中的 jar 会被锁住；另存一份，让管理员停服后改名
                    Path pending = target.resolveSibling(info.id() + ".jar.new");
                    Files.move(staged, pending, StandardCopyOption.REPLACE_EXISTING);
                    return new InstallOutcome(InstallResult.INSTALLED, Kind.JAR, pending.toString(),
                            "旧 jar 正被占用，已下载为 " + pending.getFileName()
                                    + "，请停服后改名替换并重启");
                }
                recordInstalled(info, result.sha256());
                plugin.getLogger().info("[ExtCloud] 扩展已安装: " + info.id() + " v" + info.version()
                        + " -> " + target + " (来源 " + url + ")");
                return new InstallOutcome(InstallResult.INSTALLED, Kind.JAR, target.toString(), "");
            } catch (IOException | InterruptedException e) {
                noteFailure(task, url, e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        safeDelete(staged);
        return new InstallOutcome(InstallResult.FAILED, Kind.JAR, null, "所有镜像均不可用");
    }

    private InstallOutcome installLua(ExtensionInfo info) {
        Path root = extensionsDir().toAbsolutePath().normalize();
        Path target = targetOf(info).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            return new InstallOutcome(InstallResult.FAILED, Kind.LUA, null, "写入路径越界，已拒绝");
        }
        if (blank(info.sha256())) {
            return new InstallOutcome(InstallResult.FAILED, Kind.LUA, null,
                    "索引没有提供 sha256，出于安全考虑拒绝安装");
        }
        String file = validLuaFile(info) ? info.file() : info.id() + ".lua";
        List<String> urls = candidates(info.url(), file);
        if (urls.isEmpty()) {
            return new InstallOutcome(InstallResult.FAILED, Kind.LUA, null, "索引没有可用的下载地址");
        }

        Path staged = target.resolveSibling(target.getFileName() + ".download");
        MirrorChain task = chain();
        for (String url : task.filterUnreachable(urls)) {
            try {
                DownloadService.DownloadResult result =
                        downloads.downloadToFile(url, staged, info.sha256(), "text/plain");
                if (!result.ok()) {
                    plugin.getLogger().warning("[ExtCloud] 扩展 " + info.id() + " 校验未通过 (" + url + "): " + result.error());
                    continue;
                }
                String body = Files.readString(staged, StandardCharsets.UTF_8);
                if (body.isBlank() || !body.contains("leng.")) {
                    Files.deleteIfExists(staged);
                    return new InstallOutcome(InstallResult.FAILED, Kind.LUA, null,
                            "下载内容不像 Lua 扩展脚本（缺少 leng.* 调用）");
                }
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                installLuaConfig(info);
                recordInstalled(info, result.sha256());
                plugin.getLogger().info("[ExtCloud] Lua 扩展已安装: " + info.id() + " v" + info.version()
                        + " -> " + target + " (来源 " + url + ")");
                return new InstallOutcome(InstallResult.INSTALLED, Kind.LUA, target.toString(), "");
            } catch (IOException | InterruptedException e) {
                noteFailure(task, url, e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        safeDelete(staged);
        return new InstallOutcome(InstallResult.FAILED, Kind.LUA, null, "所有镜像均不可用");
    }

    /** 默认配置只在本地还没有时释放；用户改过的配置绝不能被市场覆盖。 */
    private void installLuaConfig(ExtensionInfo info) {
        if (blank(info.config()) || !CONFIG_PATTERN.matcher(info.config()).matches()) {
            return;
        }
        Path dir = extensionsDir().toAbsolutePath().normalize();
        Path target = dir.resolve(info.config()).normalize();
        if (!target.startsWith(dir) || Files.exists(target)) {
            return;
        }
        Path staged = target.resolveSibling(target.getFileName() + ".download");
        for (String url : candidates(null, info.config())) {
            try {
                DownloadService.DownloadResult result = downloads.downloadToFile(url, staged, null, "text/yaml");
                if (!result.ok() || Files.size(staged) == 0) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        safeDelete(staged);
    }

    // ------------------------------------------------------------ 安装记录

    private void recordInstalled(ExtensionInfo info, String sha256) {
        installRecord.put(info.id(), info.version(), info.kind().name().toLowerCase(Locale.ROOT), sha256);
    }

    /**
     * 这个文件是不是"市场装的、且没被用户改过"。
     *
     * <p>只认安装记录里的 sha256：版本不同可以更新，但内容对不上就说明用户动过手，
     * 宁可拒绝覆盖，也不能把别人的修改冲掉。
     */
    private boolean isManaged(ExtensionInfo info) {
        Optional<ExtensionInstallRecord.Entry> entry = installRecord.get(info.id());
        if (entry.isEmpty() || blank(entry.get().sha256())) {
            return false;
        }
        Path file = targetOf(info);
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            return entry.get().sha256().equalsIgnoreCase(DownloadService.sha256Hex(file));
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isCurrent(ExtensionInfo info) {
        return installRecord.get(info.id())
                .map(entry -> info.version() != null && info.version().equals(entry.version()))
                .orElse(false);
    }

    // ------------------------------------------------------------ 下载地址

    private List<String> candidates(String explicitUrl, String relativePath) {
        List<String> result = new ArrayList<>();
        if (!blank(explicitUrl) && DownloadService.isAllowedUrl(explicitUrl)) {
            result.add(explicitUrl.trim());
        }
        for (String indexUrl : mirrors()) {
            String base = indexUrl.endsWith("/index.json")
                    ? indexUrl.substring(0, indexUrl.length() - "index.json".length())
                    : (indexUrl.endsWith("/") ? indexUrl : indexUrl + "/");
            String candidate = base + relativePath;
            if (DownloadService.isAllowedUrl(candidate) && !result.contains(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private void noteFailure(MirrorChain task, String url, Exception error) {
        if (task.noteFailure(url, error)) {
            plugin.getLogger().info("[ExtCloud] 镜像源 " + MirrorSpec.hostOf(url)
                    + " 本次任务不可达（" + error.getMessage() + "），后续请求将跳过它。");
        }
        plugin.getLogger().log(Level.FINE, "[ExtCloud] 下载候选失败: " + url + " — " + error.getMessage());
    }

    // ------------------------------------------------------------ 校验

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    private static boolean validJarFile(ExtensionInfo info) {
        return info.file() != null && FILE_PATTERN.matcher(info.file()).matches()
                && info.file().endsWith(".jar");
    }

    private static boolean validLuaFile(ExtensionInfo info) {
        return info.file() != null && FILE_PATTERN.matcher(info.file()).matches()
                && info.file().endsWith(".lua");
    }

    private String validateJar(Path file) {
        try (JarFile jar = new JarFile(file.toFile())) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null) {
                return "下载内容不是有效的 Bukkit 插件（缺少 plugin.yml）";
            }
            String yml;
            try (InputStream in = jar.getInputStream(entry)) {
                yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!yml.contains("main:")) {
                return "plugin.yml 缺少 main 入口";
            }
            if (!yml.matches("(?s).*depend\\s*:.*Lengbanlist.*")) {
                return "不是 Lengbanlist 扩展（plugin.yml 缺少 depend: [Lengbanlist]）";
            }
            return null;
        } catch (IOException e) {
            return "无法读取 jar: " + e.getMessage();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不影响结果
        }
    }

    private static String str(JsonObject obj, String key) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull()
                ? obj.get(key).getAsString()
                : null;
    }
}

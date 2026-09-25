package org.leng.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.leng.Lengbanlist;
import org.leng.utils.HttpHelper;
import org.leng.utils.SchedulerUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class ThemeManager {

    private static final String CFG_PREFIX = "web.theme";

    public static final Set<String> ALL_BUTTONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ban", "unban", "mute", "unmute", "warn", "report",
            "audit", "player", "ip", "history", "alts", "broadcast"
    )));

    public static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    public static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("png", "jpg", "jpeg", "webp", "gif");

    private static final String BING_API = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=";
    private static final String BING_FILE_PREFIX = "bing-";
    private static final String WALLPAPER_USER_AGENT = "Lengbanlist-BingWallpaper/1.0";
    private static final int BING_CONNECT_TIMEOUT_MS = 5000;
    private static final int BING_READ_TIMEOUT_MS = 10000;
    private static final long BING_REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000;
    private static final long MAX_BING_BYTES = 12L * 1024 * 1024;

    private final Lengbanlist plugin;
    private final File webAssetsDir;
    private final Logger logger;

    private volatile String bingFileName = "";
    private volatile long bingFetchedAt;
    private final java.util.concurrent.atomic.AtomicBoolean bingFetching = new java.util.concurrent.atomic.AtomicBoolean();

    private String backgroundType = "default";
    private String backgroundUrl = "";
    private String backgroundFile = "";
    private Set<String> hiddenButtons = new HashSet<>();

    public ThemeManager(Lengbanlist plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.webAssetsDir = new File(plugin.getDataFolder(), "web-assets/background");
        if (!webAssetsDir.exists()) {
            webAssetsDir.mkdirs();
        }
        migrateLegacyThemeYml();
        load();
    }

    private void migrateLegacyThemeYml() {
        File legacy = new File(plugin.getDataFolder(), "theme.yml");
        if (!legacy.exists()) {
            return;
        }
        try {
            if (!plugin.getConfig().contains(CFG_PREFIX + ".background-type")) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy);
                plugin.getConfig().set(CFG_PREFIX + ".background-type", yaml.getString("background-type", "default"));
                plugin.getConfig().set(CFG_PREFIX + ".background-url", yaml.getString("background-url", ""));
                plugin.getConfig().set(CFG_PREFIX + ".background-file", yaml.getString("background-file", ""));
                plugin.getConfig().set(CFG_PREFIX + ".hidden-buttons", yaml.getStringList("hidden-buttons"));
                plugin.saveConfig();
            }
            if (!legacy.delete()) {
                legacy.deleteOnExit();
            }
            logger.info("theme.yml 已迁移到 config.yml 的 " + CFG_PREFIX + " 节（旧文件已移除）");
        } catch (Exception e) {
            logger.warning("theme.yml 迁移失败（将仍读取旧文件）: " + e.getMessage());
        }
    }

    public File getWebAssetsDir() {
        return webAssetsDir;
    }

    public String getBingBackgroundUrl() {
        String name = bingFileName;
        return name.isEmpty() ? "" : "/api/theme/file/" + name;
    }

    public boolean isBingBackgroundEnabled() {
        return plugin.getConfig().getBoolean("web.background.bing-enabled", true);
    }

    public boolean isBingBackgroundStale() {
        String name = bingFileName;
        return name.isEmpty() || System.currentTimeMillis() - bingFetchedAt > BING_REFRESH_INTERVAL_MS;
    }

    public void refreshBingBackgroundAsync() {
        if (!bingFetching.compareAndSet(false, true)) {
            return;
        }
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                refreshBingBackground();
            } catch (Throwable t) {
                logger.warning("必应壁纸刷新失败: " + t.getMessage());
            } finally {
                bingFetching.set(false);
            }
        });
    }

    void refreshBingBackground() {
        adoptCachedBingBackground();
        if (!isBingBackgroundEnabled() || !isBingBackgroundStale()) {
            return;
        }
        String path = fetchBingImagePath();
        if (path.isEmpty()) {
            logger.warning("必应壁纸接口未返回可用地址，沿用现有背景");
            return;
        }
        String url = path.startsWith("http") ? path : "https://www.bing.com" + path;
        byte[] data = downloadBingImage(url);
        String extension = imageExtension(data);
        if (extension.isEmpty()) {
            logger.warning("必应壁纸下载内容不是有效图片，沿用现有背景");
            return;
        }
        String name = BING_FILE_PREFIX + dayStamp() + extension;
        try {
            Files.write(Paths.get(webAssetsDir.getAbsolutePath(), name), data);
        } catch (IOException e) {
            logger.warning("必应壁纸写入失败: " + e.getMessage());
            return;
        }
        bingFileName = name;
        bingFetchedAt = System.currentTimeMillis();
        cleanupBingFiles(name);
        logger.info("必应壁纸已缓存到 web-assets/background/" + name);
    }

    private void adoptCachedBingBackground() {
        File[] files = webAssetsDir.listFiles((dir, n) -> n.startsWith(BING_FILE_PREFIX));
        if (files == null || files.length == 0) {
            return;
        }
        File newest = null;
        for (File file : files) {
            if (!file.isFile() || imageExtension(readHead(file)).isEmpty()) {
                continue;
            }
            if (newest == null || file.lastModified() > newest.lastModified()) {
                newest = file;
            }
        }
        if (newest == null) {
            return;
        }
        bingFileName = newest.getName();
        bingFetchedAt = newest.lastModified();
    }

    private byte[] readHead(File file) {
        try (java.io.InputStream in = Files.newInputStream(file.toPath())) {
            byte[] head = new byte[8];
            int read = in.read(head);
            if (read <= 0) {
                return new byte[0];
            }
            byte[] trimmed = new byte[read];
            System.arraycopy(head, 0, trimmed, 0, read);
            return trimmed;
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private String fetchBingImagePath() {
        String market = plugin.getConfig().getString("web.background.bing-market", "zh-CN");
        try (HttpHelper http = new HttpHelper(BING_CONNECT_TIMEOUT_MS, BING_READ_TIMEOUT_MS)) {
            String body = http.get(BING_API + market, WALLPAPER_USER_AGENT, "application/json");
            if (body == null || body.trim().isEmpty()) {
                return "";
            }
            JSONArray images = new JSONObject(body).optJSONArray("images");
            if (images == null || images.isEmpty()) {
                return "";
            }
            JSONObject first = images.optJSONObject(0);
            if (first == null) {
                return "";
            }
            String url = first.optString("url", "");
            return url.startsWith("/") || url.startsWith("http") ? url : "";
        } catch (Exception e) {
            return "";
        }
    }

    private byte[] downloadBingImage(String url) {
        try (HttpHelper http = new HttpHelper(BING_CONNECT_TIMEOUT_MS, BING_READ_TIMEOUT_MS)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            final long[] size = {0L};
            http.download(url, WALLPAPER_USER_AGENT, chunk -> {
                size[0] += chunk.length;
                if (size[0] <= MAX_BING_BYTES) {
                    out.write(chunk, 0, chunk.length);
                }
            }, total -> { });
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String imageExtension(byte[] data) {
        if (data == null || data.length < 4) {
            return "";
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return ".jpg";
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return ".png";
        }
        return "";
    }

    private static String dayStamp() {
        return new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
    }

    private void cleanupBingFiles(String keep) {
        File[] files = webAssetsDir.listFiles((dir, n) -> n.startsWith(BING_FILE_PREFIX) && !n.equals(keep));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    public void load() {
        backgroundType = plugin.getConfig().getString(CFG_PREFIX + ".background-type", "default");
        backgroundUrl = plugin.getConfig().getString(CFG_PREFIX + ".background-url", "");
        backgroundFile = plugin.getConfig().getString(CFG_PREFIX + ".background-file", "");
        hiddenButtons = new HashSet<>(plugin.getConfig().getStringList(CFG_PREFIX + ".hidden-buttons"));
    }

    public void save() {
        plugin.getConfig().set(CFG_PREFIX + ".background-type", backgroundType);
        plugin.getConfig().set(CFG_PREFIX + ".background-url", backgroundUrl);
        plugin.getConfig().set(CFG_PREFIX + ".background-file", backgroundFile);
        plugin.getConfig().set(CFG_PREFIX + ".hidden-buttons", new ArrayList<>(hiddenButtons));
        plugin.saveConfig();
    }

    public String getBackgroundType() { return backgroundType; }

    public String getBackgroundUrl() { return backgroundUrl; }

    public String getBackgroundFile() { return backgroundFile; }

    public void setBackgroundUrl(String url) {
        this.backgroundType = url == null || url.isEmpty() ? "default" : "url";
        this.backgroundUrl = url == null ? "" : url;
        save();
    }

    public void setBackgroundFile(String filename) {
        this.backgroundType = filename == null || filename.isEmpty() ? "default" : "upload";
        this.backgroundFile = filename == null ? "" : filename;
        save();
    }

    public void resetBackground() {
        this.backgroundType = "default";
        this.backgroundUrl = "";
        this.backgroundFile = "";

        File[] files = webAssetsDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && !f.getName().startsWith(BING_FILE_PREFIX)) {
                    if (!f.delete()) {
                        f.deleteOnExit();
                    }
                }
            }
        }
        save();
    }

    public String saveBackgroundUpload(byte[] data, String originalFilename) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("上传内容为空");
        }
        if (data.length > MAX_UPLOAD_BYTES) {
            throw new IOException("文件超过 5MB 上限 (实际 " + data.length + " bytes)");
        }

        String ext = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot > 0 && dot < originalFilename.length() - 1) {
                ext = originalFilename.substring(dot + 1).toLowerCase();
            }
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IOException("不支持的文件扩展名: " + ext + " (允许: " + ALLOWED_EXTENSIONS + ")");
        }

        String safeName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = Paths.get(webAssetsDir.getAbsolutePath(), safeName);
        Files.write(target, data);

        if (!backgroundFile.isEmpty() && !backgroundFile.equals(safeName)) {
            Path old = Paths.get(webAssetsDir.getAbsolutePath(), backgroundFile);
            try { Files.deleteIfExists(old); } catch (IOException ignored) {}
        }

        setBackgroundFile(safeName);
        return safeName;
    }

    public File getBackgroundFileOnDisk() {
        if (backgroundFile.isEmpty()) return null;
        return new File(webAssetsDir, backgroundFile);
    }

    public Set<String> getHiddenButtons() {
        return Collections.unmodifiableSet(hiddenButtons);
    }

    public void setHiddenButtons(Set<String> buttons) {

        Set<String> filtered = new HashSet<>();
        for (String b : buttons) {
            if (ALL_BUTTONS.contains(b)) filtered.add(b);
        }
        this.hiddenButtons = filtered;
        save();
    }

    public boolean isButtonVisible(String buttonId) {
        return !hiddenButtons.contains(buttonId);
    }

    public Set<String> getVisibleButtons() {
        Set<String> visible = new HashSet<>(ALL_BUTTONS);
        visible.removeAll(hiddenButtons);
        return visible;
    }
}

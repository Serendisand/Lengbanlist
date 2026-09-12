package org.leng.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.leng.Lengbanlist;

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

    private final Lengbanlist plugin;
    private final File webAssetsDir;
    private final Logger logger;

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
                if (f.isFile()) {
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

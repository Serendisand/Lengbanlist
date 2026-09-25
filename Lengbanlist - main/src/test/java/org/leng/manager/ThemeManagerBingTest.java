package org.leng.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ThemeManagerBingTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};

    @Mock Lengbanlist plugin;
    @TempDir Path tempDir;

    private YamlConfiguration config;
    private ThemeManager theme;
    private File backgroundDir;

    @BeforeEach
    void setUp() {
        config = new YamlConfiguration();
        config.set("web.background.bing-enabled", false);
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("LengbanlistTest"));

        theme = new ThemeManager(plugin);
        backgroundDir = new File(tempDir.toFile(), "web-assets/background");
    }

    private File writeBingFile(String name, byte[] content, long modifiedAt) throws Exception {
        File file = new File(backgroundDir, name);
        Files.write(file.toPath(), content);
        assertTrue(file.setLastModified(modifiedAt));
        return file;
    }

    @Test
    void adoptsCachedWallpaperFromDisk() throws Exception {
        writeBingFile("bing-20260925.jpg", JPEG, System.currentTimeMillis());

        theme.refreshBingBackground();

        assertEquals("/api/theme/file/bing-20260925.jpg", theme.getBingBackgroundUrl());
        assertFalse(theme.isBingBackgroundStale(), "刚写入的缓存不应被判为过期");
    }

    @Test
    void ignoresCacheThatIsNotAnImage() throws Exception {
        writeBingFile("bing-20260925.jpg", "not an image".getBytes("UTF-8"), System.currentTimeMillis());

        theme.refreshBingBackground();

        assertEquals("", theme.getBingBackgroundUrl(), "非图片内容不能被当成壁纸下发");
        assertTrue(theme.isBingBackgroundStale());
    }

    @Test
    void picksTheNewestCacheFile() throws Exception {
        long now = System.currentTimeMillis();
        writeBingFile("bing-20260923.jpg", JPEG, now - 172_800_000L);
        writeBingFile("bing-20260925.jpg", JPEG, now - 1_000L);

        theme.refreshBingBackground();

        assertEquals("/api/theme/file/bing-20260925.jpg", theme.getBingBackgroundUrl());
    }

    @Test
    void missingCacheReportsStaleAndNoUrl() {
        theme.refreshBingBackground();

        assertEquals("", theme.getBingBackgroundUrl());
        assertTrue(theme.isBingBackgroundStale());
    }

    @Test
    void resetBackgroundKeepsBingCacheButRemovesUploads() throws Exception {
        writeBingFile("bing-20260925.jpg", JPEG, System.currentTimeMillis());
        File upload = new File(backgroundDir, "abc123.png");
        Files.write(upload.toPath(), JPEG);

        theme.resetBackground();

        assertTrue(new File(backgroundDir, "bing-20260925.jpg").exists(), "重置背景不应删掉壁纸缓存");
        assertFalse(upload.exists(), "重置背景应清掉自己上传的图");
        assertEquals("default", theme.getBackgroundType());
    }

    @Test
    void servedUrlSwitchHonoursBackgroundType() throws Exception {
        writeBingFile("bing-20260925.jpg", JPEG, System.currentTimeMillis());
        theme.refreshBingBackground();

        theme.setBackgroundUrl("https://example.com/a.jpg");
        assertEquals("url", theme.getBackgroundType());
        assertEquals("https://example.com/a.jpg", theme.getBackgroundUrl());

        theme.setBackgroundFile("");
        assertEquals("default", theme.getBackgroundType());
        assertEquals("/api/theme/file/bing-20260925.jpg", theme.getBingBackgroundUrl());
    }
}

package org.leng.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class AutoUpdateManagerDownloadTest {

    @TempDir
    Path tempDir;

    private File writeJar(String pluginYml, String manifestMainClass) throws IOException {
        File jar = tempDir.resolve("test.jar").toFile();
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jar))) {
            if (manifestMainClass != null) {
                out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                out.write(("Manifest-Version: 1.0\r\n"
                        + "Main-Class: " + manifestMainClass + "\r\n\r\n").getBytes("UTF-8"));
                out.closeEntry();
            }
            if (pluginYml != null) {
                out.putNextEntry(new ZipEntry("plugin.yml"));
                out.write(pluginYml.getBytes("UTF-8"));
                out.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void acceptsOfficialStyleJarWithoutManifestMainClass() throws Exception {

        File jar = writeJar("name: Lengbanlist\nmain: org.leng.Lengbanlist\nversion: 1.9.9\n", null);
        assertDoesNotThrow(() -> AutoUpdateManager.validatePluginJar(jar));
    }

    @Test
    void rejectsJarMissingPluginYml() throws Exception {
        File jar = writeJar(null, null);
        IOException e = assertThrows(IOException.class, () -> AutoUpdateManager.validatePluginJar(jar));
        assertTrue(e.getMessage().contains("plugin.yml"));
    }

    @Test
    void rejectsJarWithWrongMainClassInPluginYml() throws Exception {
        File jar = writeJar("name: Other\nmain: com.evil.Main\nversion: 1.0\n", null);
        IOException e = assertThrows(IOException.class, () -> AutoUpdateManager.validatePluginJar(jar));
        assertTrue(e.getMessage().contains("plugin.yml 主类"));
    }

    @Test
    void rejectsJarWithConflictingManifestMainClass() throws Exception {

        File jar = writeJar("name: Lengbanlist\nmain: org.leng.Lengbanlist\nversion: 1.9.9\n", "com.evil.Injected");
        IOException e = assertThrows(IOException.class, () -> AutoUpdateManager.validatePluginJar(jar));
        assertTrue(e.getMessage().contains("冲突的主类"));
    }
}

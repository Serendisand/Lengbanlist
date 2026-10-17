package org.leng.extension.lua;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;
import org.leng.extension.ExtensionInstallRecord;
import org.leng.net.DownloadService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

/**
 * 随核心释放的官方 Lua 扩展要登记成"市场安装"，否则市场会把它们当成用户手放的文件
 * 而拒绝更新。
 */
@ExtendWith(MockitoExtension.class)
class LuaBundledInstallRecordTest {

    @Mock Lengbanlist plugin;
    @TempDir Path tmp;

    @Test
    void bundledScriptsAreRegisteredAsMarketInstalled() throws Exception {
        Path dataFolder = tmp.resolve("Lengbanlist");
        Path root = dataFolder.resolve("extensions");
        lenient().when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        lenient().when(plugin.getLogger()).thenReturn(Logger.getGlobal());
        // 把插件里的默认资源真的释放到临时目录
        doAnswer(invocation -> {
            String path = invocation.getArgument(0);
            Path target = dataFolder.resolve(path);
            Files.createDirectories(target.getParent());
            try (InputStream in = getClass().getResourceAsStream("/" + path)) {
                if (in != null) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return null;
        }).when(plugin).saveResource(anyString(), eq(false));

        new LuaLoader(plugin, null).ensureDefaults(root.toFile());

        ExtensionInstallRecord record = new ExtensionInstallRecord(plugin);
        ExtensionInstallRecord.Entry tp = record.get("tp").orElseThrow();
        assertEquals("1.0.0", tp.version());
        assertEquals("lua", tp.kind());
        assertEquals(DownloadService.sha256Hex(root.resolve("tp.lua")), tp.sha256());
        assertTrue(record.get("staffchat").isPresent());
    }
}

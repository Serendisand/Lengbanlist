package org.leng.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.leng.Lengbanlist;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorLogTest {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @TempDir
    Path tempDir;

    private Lengbanlist plugin() {
        Lengbanlist plugin = mock(Lengbanlist.class);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("ErrorLogTest"));
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        return plugin;
    }

    @Test
    void writesThrowableToDatedFile() throws Exception {
        ErrorLog.record(plugin(), "测试上下文", new IllegalStateException("boom"));

        Path file = tempDir.resolve("error_" + LocalDate.now().format(FILE_DATE) + ".txt");
        assertTrue(Files.exists(file), "错误文件应被创建");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("测试上下文"), "应记录上下文");
        assertTrue(content.contains("IllegalStateException"), "应记录异常类型");
        assertTrue(content.contains("boom"), "应记录异常信息");
        assertTrue(content.contains("ErrorLogTest"), "应包含堆栈");
    }

    @Test
    void appendsInsteadOfOverwriting() throws Exception {
        Lengbanlist plugin = plugin();
        ErrorLog.record(plugin, "第一条", new RuntimeException("one"));
        ErrorLog.record(plugin, "第二条", new RuntimeException("two"));

        Path file = tempDir.resolve("error_" + LocalDate.now().format(FILE_DATE) + ".txt");
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.contains("第一条"));
        assertTrue(content.contains("第二条"));
    }

    @Test
    void removesFilesOlderThanRetention() throws Exception {
        Path old = tempDir.resolve("error_" + LocalDate.now().minusDays(30).format(FILE_DATE) + ".txt");
        Files.writeString(old, "old", StandardCharsets.UTF_8);

        ErrorLog.record(plugin(), "触发清理", new RuntimeException("x"));

        assertTrue(Files.notExists(old), "过期错误文件应被清理");
        List<Path> remaining;
        try (var stream = Files.list(tempDir)) {
            remaining = stream.filter(p -> p.getFileName().toString().startsWith("error_")).toList();
        }
        assertEquals(1, remaining.size(), "只应保留当天的错误文件");
    }

    @Test
    void handlesMissingPluginGracefully() {
        ErrorLog.record(null, "空插件", new RuntimeException("x"));
        ErrorLog.record(plugin(), "空异常", (Throwable) null);
    }
}

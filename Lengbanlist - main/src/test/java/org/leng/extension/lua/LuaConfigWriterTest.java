package org.leng.extension.lua;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开关写盘。
 *
 * <p>这里钉的是"写扩展自己的 config.yml 时不能伤到别的东西"：注释、其它键、缩进的同名键、
 * 原文件的换行风格都得原样留着。丢掉注释正是当初 {@code extensions.yml} 那版设计被回退的
 * 原因之一，不能在新路径上重犯。
 */
class LuaConfigWriterTest {

    @TempDir
    Path tempDir;

    private Path config;

    private String write(String content, boolean enabled) throws Exception {
        config = tempDir.resolve("config.yml");
        Files.writeString(config, content, StandardCharsets.UTF_8);
        LuaConfigWriter.writeEnabled(config.toFile(), enabled);
        return Files.readString(config, StandardCharsets.UTF_8);
    }

    @Test
    void rewritesTheSwitchAndKeepsCommentsAndOtherKeys() throws Exception {
        String text = write("# 顶部说明\nenabled: true  # 关掉就停用\nother: 1\n", false);

        assertTrue(text.contains("# 顶部说明"), "注释不能被抹掉");
        assertTrue(text.contains("enabled: false  # 关掉就停用"), "行尾注释要跟着值一起留下：" + text);
        assertTrue(text.contains("other: 1"));
    }

    @Test
    void appendsTheSwitchWhenTheFileHasNone() throws Exception {
        String text = write("# 只有注释\nx: 1\n", false);

        assertTrue(text.contains("enabled: false"), text);
        assertTrue(text.contains("x: 1"));
    }

    @Test
    void nestedSwitchesBelongToSomeoneElseAndAreLeftAlone() throws Exception {
        String text = write("nested:\n  enabled: true\n", false);

        assertTrue(text.contains("  enabled: true"), "缩进的 enabled 属于别的配置段：" + text);
        assertTrue(text.contains("\nenabled: false"), "顶层开关要另起一行：" + text);
    }

    @Test
    void createsTheFileWhenMissing() throws Exception {
        config = tempDir.resolve("config.yml");

        LuaConfigWriter.writeEnabled(config.toFile(), true);

        assertEquals("enabled: true\n", Files.readString(config, StandardCharsets.UTF_8));
    }

    @Test
    void keepsCrlfLineEndings() throws Exception {
        String text = write("enabled: true\r\nother: 1\r\n", false);

        assertTrue(text.contains("\r\n"), "原来用 CRLF 就别改成 LF");
        assertFalse(text.replace("\r\n", "").contains("\n"), "不能混进裸 LF：" + text);
    }

    @Test
    void isIdempotent() throws Exception {
        String first = write("enabled: true\n", false);
        String second = write(first, false);

        assertEquals(first, second);
    }

    @Test
    void writesThroughARealFileHandle() throws Exception {
        config = tempDir.resolve("config.yml");
        Files.writeString(config, "enabled: true\n");

        LuaConfigWriter.writeEnabled(new File(config.toString()), false);

        assertTrue(Files.readString(config).contains("enabled: false"));
    }
}

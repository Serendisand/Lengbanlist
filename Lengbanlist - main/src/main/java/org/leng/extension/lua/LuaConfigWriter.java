package org.leng.extension.lua;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 改写扩展自己的 {@code config.yml} 里的 {@code enabled} 开关。
 *
 * <p>刻意<b>不</b>走 {@code YamlConfiguration.load + save}：那样落盘会抹掉扩展作者写的
 * 全部注释，而注释 API 只有 1.18.2+ 的服务端才有，本插件要兼容 1.17.1。
 * 这里只做一次文本级的行替换——注释、排版、键顺序原样保留。
 *
 * <p>只认<b>顶层</b>的 {@code enabled}：缩进过的键（别人配置段里的）不碰，
 * 否则会把扩展自己的其它配置项写坏。
 */
final class LuaConfigWriter {

    private static final Pattern ENABLED_KEY = Pattern.compile("^enabled\\s*:.*$");

    private LuaConfigWriter() {
    }

    /**
     * 把开关写进配置文件；文件不存在就建一个只含这一行的。
     *
     * @throws IOException 读写失败，调用方据此给管理员一条可读提示
     */
    static void writeEnabled(File file, boolean enabled) throws IOException {
        String text = file.isFile() ? Files.readString(file.toPath(), StandardCharsets.UTF_8) : "";
        String separator = text.contains("\r\n") ? "\r\n" : "\n";
        String plain = text.replace("\r\n", "\n");

        List<String> lines = new ArrayList<>(Arrays.asList(plain.split("\n", -1)));
        // split 的末尾空串来自结尾换行，写回时统一补，不当作一行内容
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        if (lines.isEmpty()) {
            Files.writeString(file.toPath(), "enabled: " + enabled + separator, StandardCharsets.UTF_8);
            return;
        }

        int index = indexOfTopLevelEnabled(lines);
        if (index < 0) {
            lines.add("enabled: " + enabled);
        } else {
            lines.set(index, "enabled: " + enabled + commentTail(lines.get(index)));
        }
        Files.writeString(file.toPath(), String.join(separator, lines) + separator, StandardCharsets.UTF_8);
    }

    /** 顶层（不缩进）的 enabled 行，忽略注释行。找不到返回 -1。 */
    private static int indexOfTopLevelEnabled(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty() || Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            if (ENABLED_KEY.matcher(line).matches()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 保留原行尾的注释，连同值与注释之间的空格。
     *
     * <p>连空格一起留是有意的：管理员改完开关，文件应该跟他写的一模一样，
     * 只有那一个 true/false 变了——顺手重排格式会让人以为自己碰错了文件。
     */
    private static String commentTail(String line) {
        int hash = line.indexOf('#');
        if (hash < 0) {
            return "";
        }
        int start = hash;
        while (start > 0 && Character.isWhitespace(line.charAt(start - 1))) {
            start--;
        }
        return line.substring(start).stripTrailing();
    }
}

package org.leng.utils;

import org.leng.Lengbanlist;

import java.io.BufferedWriter;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ErrorLog {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int KEEP_DAYS = 7;

    private static final Object LOCK = new Object();

    private ErrorLog() {
    }

    public static void record(Lengbanlist plugin, String context, Throwable error) {
        if (plugin == null) {
            return;
        }
        String detail = error == null ? "" : buildTrace(error);
        String brief = error == null ? "" : briefMessage(error);
        append(plugin, context, detail);
        plugin.getLogger().warning(context + (brief.isEmpty() ? "" : "：" + brief)
                + "（详细堆栈见 " + fileName(plugin) + "）");
    }

    public static void record(Lengbanlist plugin, String context, String detail) {
        if (plugin == null) {
            return;
        }
        append(plugin, context, detail == null ? "" : detail);
        plugin.getLogger().warning(context + "（详情见 " + fileName(plugin) + "）");
    }

    public static String fileName(Lengbanlist plugin) {
        return "plugins/" + plugin.getDataFolder().getName() + "/error_" + LocalDate.now().format(FILE_DATE) + ".txt";
    }

    private static void append(Lengbanlist plugin, String context, String detail) {
        synchronized (LOCK) {
            try {
                File folder = plugin.getDataFolder();
                if (!folder.exists() && !folder.mkdirs()) {
                    return;
                }
                cleanupOldFiles(folder);
                Path file = new File(folder, "error_" + LocalDate.now().format(FILE_DATE) + ".txt").toPath();
                try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    writer.write("[" + LocalDateTime.now().format(STAMP) + "] " + context);
                    writer.newLine();
                    if (detail != null && !detail.isEmpty()) {
                        writer.write(detail);
                        writer.newLine();
                    }
                    writer.write("------------------------------------------------------------");
                    writer.newLine();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void cleanupOldFiles(File folder) {
        long today = LocalDate.now().toEpochDay();
        File[] files = folder.listFiles((dir, name) -> name.startsWith("error_") && name.endsWith(".txt"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            try {
                LocalDate date = LocalDate.parse(name.substring("error_".length(), name.length() - ".txt".length()), FILE_DATE);
                if (today - date.toEpochDay() > KEEP_DAYS) {
                    Files.deleteIfExists(file.toPath());
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String buildTrace(Throwable error) {
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String briefMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String type = root.getClass().getSimpleName();
        return message == null || message.isEmpty() ? type : type + ": " + message;
    }
}

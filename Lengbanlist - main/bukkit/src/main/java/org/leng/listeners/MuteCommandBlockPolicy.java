package org.leng.listeners;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class MuteCommandBlockPolicy {
    private static final String CONFIG_PATH = "mute-blocked-commands";
    private static final String STRIP_NAMESPACE_PATH = "mute-command-block-strip-namespace";

    private MuteCommandBlockPolicy() {
    }

    /**
     * 解析被屏蔽的命令列表。
     * 若 chatconfig.yml 未配置但旧 config.yml 中有配置，则由 migrateLegacyConfig 处理迁移。
     * @return 被屏蔽的命令列表
     */
    static List<String> resolveBlockedCommands(FileConfiguration chatConfig,
                                                 FileConfiguration legacyConfig) {
        if (chatConfig != null && chatConfig.contains(CONFIG_PATH)) {
            return chatConfig.getStringList(CONFIG_PATH);
        }
        if (legacyConfig != null) {
            return legacyConfig.getStringList(CONFIG_PATH);
        }
        return Collections.emptyList();
    }

    /**
     * 若 chatconfig.yml 未配置 mute-blocked-commands 但旧 config.yml 中有配置，则自动迁移写入 chatconfig.yml。
     * @return 是否发生了迁移
     */
    static boolean migrateLegacyConfig(FileConfiguration chatConfig, FileConfiguration legacyConfig, File chatConfigFile) {
        if (chatConfig == null || legacyConfig == null || chatConfigFile == null) {
            return false;
        }
        if (chatConfig.contains(CONFIG_PATH) || !legacyConfig.contains(CONFIG_PATH)) {
            return false;
        }
        List<String> legacy = legacyConfig.getStringList(CONFIG_PATH);
        if (legacy.isEmpty()) {
            return false;
        }
        chatConfig.set(CONFIG_PATH, legacy);
        try {
            chatConfig.save(chatConfigFile);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static boolean isBlocked(String commandMessage, List<String> blockedCommands, boolean stripNamespace) {
        String commandName = rootCommand(commandMessage, stripNamespace);
        if (commandName.isEmpty() || blockedCommands == null || blockedCommands.isEmpty()) {
            return false;
        }

        for (String blockedCommand : blockedCommands) {
            if (commandName.equals(configuredCommand(blockedCommand, stripNamespace))) {
                return true;
            }
        }
        return false;
    }

    private static String rootCommand(String commandMessage, boolean stripNamespace) {
        if (commandMessage == null) {
            return "";
        }
        String trimmed = commandMessage.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String root = trimmed.split("\\s+", 2)[0];
        root = stripLeadingSlash(root);
        if (stripNamespace) {
            int namespaceSeparator = root.lastIndexOf(':');
            if (namespaceSeparator >= 0) {
                root = root.substring(namespaceSeparator + 1);
            }
        }
        return root.toLowerCase(Locale.ROOT);
    }

    private static String configuredCommand(String configuredCommand, boolean stripNamespace) {
        if (configuredCommand == null) {
            return "";
        }
        String normalized = configuredCommand.trim();
        if (normalized.isEmpty() || containsWhitespace(normalized)) {
            return "";
        }
        normalized = stripLeadingSlash(normalized);
        if (stripNamespace) {
            int namespaceSeparator = normalized.lastIndexOf(':');
            if (namespaceSeparator >= 0) {
                normalized = normalized.substring(namespaceSeparator + 1);
            }
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}

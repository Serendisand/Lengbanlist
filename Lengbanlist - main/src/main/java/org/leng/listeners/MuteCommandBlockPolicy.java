package org.leng.listeners;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class MuteCommandBlockPolicy {
    private static final String CONFIG_PATH = "mute-blocked-commands";

    private MuteCommandBlockPolicy() {
    }

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

    static boolean isBlocked(String commandMessage, List<String> blockedCommands) {
        String commandName = rootCommand(commandMessage);
        if (commandName.isEmpty() || blockedCommands == null || blockedCommands.isEmpty()) {
            return false;
        }

        for (String blockedCommand : blockedCommands) {
            if (commandName.equals(configuredCommand(blockedCommand))) {
                return true;
            }
        }
        return false;
    }

    private static String rootCommand(String commandMessage) {
        if (commandMessage == null) {
            return "";
        }
        String trimmed = commandMessage.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String root = trimmed.split("\\s+", 2)[0];
        root = stripLeadingSlash(root);
        int namespaceSeparator = root.lastIndexOf(':');
        if (namespaceSeparator >= 0) {
            root = root.substring(namespaceSeparator + 1);
        }
        return root.toLowerCase(Locale.ROOT);
    }

    private static String configuredCommand(String configuredCommand) {
        if (configuredCommand == null) {
            return "";
        }
        String normalized = configuredCommand.trim();
        if (normalized.isEmpty() || containsWhitespace(normalized)) {
            return "";
        }
        normalized = stripLeadingSlash(normalized);
        int namespaceSeparator = normalized.lastIndexOf(':');
        if (namespaceSeparator >= 0) {
            normalized = normalized.substring(namespaceSeparator + 1);
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

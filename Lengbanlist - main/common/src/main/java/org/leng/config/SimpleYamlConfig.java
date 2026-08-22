package org.leng.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SimpleYamlConfig {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public static SimpleYamlConfig load(InputStream stream) throws IOException {
        SimpleYamlConfig config = new SimpleYamlConfig();
        config.loadFrom(stream);
        return config;
    }

    private void loadFrom(InputStream stream) throws IOException {
        List<String> path = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String noComment = stripComment(line);
                if (noComment.trim().isEmpty()) continue;
                int indent = countIndent(noComment) / 2;
                String trimmed = noComment.trim();
                if (trimmed.startsWith("- ") && !path.isEmpty()) {
                    while (path.size() > indent) path.remove(path.size() - 1);
                    String item = trimmed.substring(2).trim();
                    if ((item.startsWith("\"") && item.endsWith("\"")) || (item.startsWith("'") && item.endsWith("'"))) {
                        item = item.substring(1, item.length() - 1);
                    }
                    String listPath = String.join(".", path);
                    Object existing = values.get(listPath);
                    List<String> list;
                    if (existing instanceof List) {
                        list = (List<String>) existing;
                    } else {
                        list = new ArrayList<>();
                        values.put(listPath, list);
                    }
                    list.add(item);
                    continue;
                }
                if (!trimmed.contains(":")) continue;
                while (path.size() > indent) path.remove(path.size() - 1);
                String key = trimmed.substring(0, trimmed.indexOf(':')).trim();
                String rawValue = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (rawValue.isEmpty()) {
                    path.add(key);
                    continue;
                }
                List<String> full = new ArrayList<>(path);
                full.add(key);
                values.put(String.join(".", full), parseValue(rawValue));
            }
        }
    }

    private String stripComment(String line) {
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
            }
            if (c == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private int countIndent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') count++;
        return count;
    }

    private Object parseValue(String raw) {
        if (raw.startsWith("[") && raw.endsWith("]")) {
            List<String> list = new ArrayList<>();
            String content = raw.substring(1, raw.length() - 1).trim();
            if (!content.isEmpty()) {
                for (String item : splitInlineList(content)) {
                    String value = item.trim();
                    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    list.add(value);
                }
            }
            return list;
        }
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            return raw.substring(1, raw.length() - 1);
        }
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
        }
        return raw;
    }

    private List<String> splitInlineList(String content) {
        List<String> values = new ArrayList<>();
        boolean quoted = false;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            if ((current == '\'' || current == '\"') && (i == 0 || content.charAt(i - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = current;
                } else if (quote == current) {
                    quoted = false;
                }
            } else if (current == ',' && !quoted) {
                values.add(content.substring(start, i));
                start = i + 1;
            }
        }
        values.add(content.substring(start));
        return values;
    }

    public String getString(String path, String def) {
        Object value = values.get(path);
        return value == null ? def : String.valueOf(value);
    }

    public int getInt(String path, int def) {
        Object value = values.get(path);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = values.get(path);
        return value == null ? def : Boolean.parseBoolean(String.valueOf(value));
    }

    public List<String> getStringList(String path) {
        Object value = values.get(path);
        if (value instanceof List) return (List<String>) value;
        return Collections.emptyList();
    }

    public boolean isConfigurationSection(String path) {
        String prefix = path + ".";
        for (String key : values.keySet()) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }

    public List<String> getConfigurationSectionKeys(String path) {
        String prefix = path + ".";
        List<String> keys = new ArrayList<>();
        for (String key : values.keySet()) {
            if (key.startsWith(prefix)) {
                String rest = key.substring(prefix.length());
                String first = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
                if (!keys.contains(first)) keys.add(first);
            }
        }
        return keys;
    }

    public Object getObject(String path) {
        return values.get(path);
    }

    public void set(String path, Object value) {
        values.put(path, value);
    }

    /** 返回扁平化的键值映射（点分路径 → 值），供 CustomModel 使用。 */
    public Map<String, Object> getFlatMap() {
        return values;
    }
}

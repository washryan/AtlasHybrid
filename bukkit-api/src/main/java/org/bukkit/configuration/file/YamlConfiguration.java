package org.bukkit.configuration.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YamlConfiguration extends FileConfiguration {
    private YamlConfiguration(Map<String, Object> values) {
        super(values);
    }

    public static YamlConfiguration loadConfiguration(Path path) {
        if (!Files.isRegularFile(path)) {
            return new YamlConfiguration(Map.of());
        }
        try {
            return new YamlConfiguration(parseFlat(Files.readAllLines(path, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read configuration " + path, exception);
        }
    }

    static Map<String, Object> parseFlat(List<String> lines) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("-")) continue;
            int colon = line.indexOf(':');
            if (colon < 1) continue;
            String key = line.substring(0, colon).strip();
            String text = line.substring(colon + 1).strip();
            if (text.isEmpty()) continue;
            values.put(key, scalar(text));
        }
        return values;
    }

    private static Object scalar(String text) {
        String value = stripQuotes(text);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return value; }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

package org.bukkit.configuration.file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class YamlConfiguration extends FileConfiguration {
    private YamlConfiguration(Map<String, Object> values) {
        super(values);
    }

    public static YamlConfiguration loadConfiguration(Path path) {
        if (!Files.isRegularFile(path)) return new YamlConfiguration(Map.of());
        try {
            return new YamlConfiguration(parseFlat(Files.readAllLines(path, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read configuration " + path, exception);
        }
    }

    public static void saveConfiguration(FileConfiguration configuration, Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, serialize(configuration.getValues(true)), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save configuration " + path, exception);
        }
    }

    static Map<String, Object> parseFlat(List<String> lines) {
        Map<String, Object> values = new LinkedHashMap<>();
        String section = null;
        Map<String, Object> sectionValues = null;
        List<String> sectionList = null;

        for (String raw : lines) {
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;
            int indent = leadingSpaces(raw);
            String line = raw.strip();

            if (indent == 0) {
                finishSection(values, section, sectionValues, sectionList);
                section = null;
                sectionValues = null;
                sectionList = null;
                int colon = line.indexOf(':');
                if (colon < 1) continue;
                String key = line.substring(0, colon).strip();
                String text = line.substring(colon + 1).strip();
                if (text.isEmpty()) {
                    section = key;
                    sectionValues = new LinkedHashMap<>();
                    sectionList = new ArrayList<>();
                } else {
                    values.put(key, scalar(text));
                }
            } else if (section != null && line.startsWith("-")) {
                sectionList.add(String.valueOf(scalar(line.substring(1).strip())));
            } else if (section != null) {
                int colon = line.indexOf(':');
                if (colon < 1) continue;
                sectionValues.put(line.substring(0, colon).strip(), scalar(line.substring(colon + 1).strip()));
            }
        }
        finishSection(values, section, sectionValues, sectionList);
        return values;
    }

    private static void finishSection(
        Map<String, Object> values,
        String section,
        Map<String, Object> sectionValues,
        List<String> sectionList
    ) {
        if (section == null) return;
        if (!sectionList.isEmpty()) {
            values.put(section, List.copyOf(sectionList));
            return;
        }
        if ("org.bukkit.Location".equals(sectionValues.get("=="))) {
            String worldName = String.valueOf(sectionValues.get("world"));
            World world = Bukkit.getWorld(worldName);
            values.put(section, new Location(
                world,
                number(sectionValues.get("x")),
                number(sectionValues.get("y")),
                number(sectionValues.get("z")),
                (float) number(sectionValues.get("yaw")),
                (float) number(sectionValues.get("pitch"))
            ));
        }
    }

    private static String serialize(Map<String, Object> values) {
        StringBuilder yaml = new StringBuilder();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Location location) {
                if (location.getWorld() == null) throw new IllegalStateException("Cannot serialize a location without a world: " + entry.getKey());
                yaml.append(entry.getKey()).append(":\n")
                    .append("  ==: org.bukkit.Location\n")
                    .append("  world: ").append(quote(location.getWorld().getName())).append('\n')
                    .append("  x: ").append(location.getX()).append('\n')
                    .append("  y: ").append(location.getY()).append('\n')
                    .append("  z: ").append(location.getZ()).append('\n')
                    .append("  yaw: ").append(location.getYaw()).append('\n')
                    .append("  pitch: ").append(location.getPitch()).append('\n');
            } else if (value instanceof List<?> list) {
                yaml.append(entry.getKey()).append(":\n");
                for (Object item : list) yaml.append("  - ").append(quote(String.valueOf(item))).append('\n');
            } else {
                yaml.append(entry.getKey()).append(": ").append(formatScalar(value)).append('\n');
            }
        }
        return yaml.toString();
    }

    private static Object scalar(String text) {
        String value = stripQuotes(text);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { }
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) { return value; }
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException exception) { throw new IllegalStateException("Invalid location number: " + value, exception); }
    }

    private static String formatScalar(Object value) {
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) return value.substring(1, value.length() - 1);
        return value;
    }

    private static int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') count++;
        return count;
    }
}

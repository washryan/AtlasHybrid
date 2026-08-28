package org.bukkit.configuration.file;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class YamlConfiguration extends FileConfiguration {
    private static final int MAX_INPUT_CHARS = 4 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_NODES = 100_000;

    public YamlConfiguration() { this(new LinkedHashMap<>()); }
    private YamlConfiguration(Map<String, Object> values) { super(values); }

    public static YamlConfiguration loadConfiguration(File file) {
        Objects.requireNonNull(file, "file");
        if (!file.isFile()) return new YamlConfiguration();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (IOException | IllegalArgumentException exception) {
            logLoadFailure("Cannot load " + file, exception);
            return new YamlConfiguration();
        }
    }

    public static YamlConfiguration loadConfiguration(Reader reader) {
        Objects.requireNonNull(reader, "reader");
        try {
            return load(reader);
        } catch (IOException | IllegalArgumentException exception) {
            logLoadFailure("Cannot load configuration from stream", exception);
            return new YamlConfiguration();
        }
    }

    public static YamlConfiguration loadConfiguration(Path path) {
        Objects.requireNonNull(path, "path");
        return loadConfiguration(path.toFile());
    }

    public static void saveConfiguration(FileConfiguration configuration, Path path) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(path, "path");
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, serialize(configuration.atlasValues()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save configuration " + path, exception);
        }
    }

    private static YamlConfiguration load(Reader source) throws IOException {
        List<Line> lines = readLines(source);
        if (lines.isEmpty()) return new YamlConfiguration();
        Parser parser = new Parser(lines);
        Object parsed = parser.parseBlock(lines.get(0).indent(), 0);
        if (!(parsed instanceof Map<?, ?> map) || parser.index != lines.size()) {
            throw new IllegalArgumentException("YAML root must be a mapping");
        }
        return new YamlConfiguration(convertLocations(castMap(map)));
    }

    private static List<Line> readLines(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        List<Line> lines = new ArrayList<>();
        int totalChars = 0;
        String raw;
        int number = 0;
        while ((raw = reader.readLine()) != null) {
            number++;
            totalChars += raw.length() + 1;
            if (totalChars > MAX_INPUT_CHARS) throw new IllegalArgumentException("YAML input exceeds safe size limit");
            int indent = indentation(raw, number);
            String content = stripComment(raw.substring(indent)).stripTrailing();
            if (!content.isBlank()) lines.add(new Line(indent, content, number));
        }
        return lines;
    }

    private static int indentation(String raw, int line) {
        int indent = 0;
        while (indent < raw.length() && raw.charAt(indent) == ' ') indent++;
        if (indent < raw.length() && raw.charAt(indent) == '\t') {
            throw new IllegalArgumentException("Tabs are not allowed for YAML indentation at line " + line);
        }
        return indent;
    }

    private static String stripComment(String text) {
        boolean single = false;
        boolean doubled = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (escaped) { escaped = false; continue; }
            if (character == '\\' && doubled) { escaped = true; continue; }
            if (character == '\'' && !doubled) single = !single;
            else if (character == '"' && !single) doubled = !doubled;
            else if (character == '#' && !single && !doubled && (index == 0 || Character.isWhitespace(text.charAt(index - 1)))) return text.substring(0, index);
        }
        if (single || doubled) throw new IllegalArgumentException("Unterminated quoted scalar");
        return text;
    }

    private static Map<String, Object> convertLocations(Map<String, Object> source) {
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> child = convertLocations(castMap(map));
                value = "org.bukkit.Location".equals(child.get("==")) ? location(child) : child;
            } else if (value instanceof List<?> list) value = new ArrayList<>(list);
            converted.put(entry.getKey(), value);
        }
        return converted;
    }

    private static Location location(Map<String, Object> values) {
        World world = Bukkit.getWorld(String.valueOf(values.get("world")));
        return new Location(world, number(values.get("x")), number(values.get("y")), number(values.get("z")),
            (float) number(values.get("yaw")), (float) number(values.get("pitch")));
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        throw new IllegalArgumentException("Invalid location number: " + value);
    }

    private static String serialize(Map<String, Object> values) {
        StringBuilder yaml = new StringBuilder();
        appendMap(yaml, values, 0);
        return yaml.toString();
    }

    private static void appendMap(StringBuilder yaml, Map<String, Object> values, int indent) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            indent(yaml, indent).append(entry.getKey()).append(':');
            Object value = entry.getValue();
            if (value instanceof Location location) {
                yaml.append('\n');
                Map<String, Object> serialized = new LinkedHashMap<>();
                serialized.put("==", "org.bukkit.Location");
                serialized.put("world", location.getWorld() == null ? null : location.getWorld().getName());
                serialized.put("x", location.getX());
                serialized.put("y", location.getY());
                serialized.put("z", location.getZ());
                serialized.put("yaw", location.getYaw());
                serialized.put("pitch", location.getPitch());
                appendMap(yaml, serialized, indent + 2);
            } else if (value instanceof Map<?, ?> map) {
                yaml.append('\n');
                appendMap(yaml, castMap(map), indent + 2);
            } else if (value instanceof List<?> list) {
                if (list.isEmpty()) yaml.append(" []\n");
                else {
                    yaml.append('\n');
                    for (Object item : list) indent(yaml, indent + 2).append("- ").append(formatScalar(item)).append('\n');
                }
            } else yaml.append(' ').append(formatScalar(value)).append('\n');
        }
    }

    private static StringBuilder indent(StringBuilder target, int spaces) { return target.append(" ".repeat(spaces)); }

    private static String formatScalar(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return '"' + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static void logLoadFailure(String message, Exception exception) {
        Logger logger;
        try { logger = Bukkit.getLogger(); }
        catch (RuntimeException unavailable) { logger = Logger.getLogger("AtlasHybrid"); }
        logger.log(Level.SEVERE, message, exception);
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> castMap(Object value) { return (Map<String, Object>) value; }
    private record Line(int indent, String content, int number) { }

    private static final class Parser {
        private final List<Line> lines;
        private int index;
        private int nodes;

        private Parser(List<Line> lines) { this.lines = lines; }

        private Object parseBlock(int indent, int depth) {
            if (depth > MAX_DEPTH) throw error("YAML nesting exceeds safe limit");
            return lines.get(index).content().startsWith("-") ? parseList(indent, depth) : parseMap(indent, depth);
        }

        private Map<String, Object> parseMap(int indent, int depth) {
            Map<String, Object> result = new LinkedHashMap<>();
            while (index < lines.size()) {
                Line line = lines.get(index);
                if (line.indent() < indent) break;
                if (line.indent() != indent || line.content().startsWith("-")) throw error("Invalid mapping indentation");
                int colon = mappingColon(line.content());
                if (colon < 1) throw error("Expected mapping entry");
                String key = unquote(line.content().substring(0, colon).strip());
                if (key.isEmpty() || "<<".equals(key) || key.startsWith("!")
                    || key.startsWith("&") || key.startsWith("*")) {
                    throw error("Invalid or unsafe mapping key");
                }
                if (result.containsKey(key)) throw error("Duplicate mapping key: " + key);
                String text = line.content().substring(colon + 1).strip();
                index++;
                Object value = text.isEmpty()
                    ? index < lines.size() && lines.get(index).indent() > indent ? parseBlock(lines.get(index).indent(), depth + 1) : null
                    : scalar(text);
                addNode();
                result.put(key, value);
            }
            return result;
        }

        private List<Object> parseList(int indent, int depth) {
            List<Object> result = new ArrayList<>();
            while (index < lines.size()) {
                Line line = lines.get(index);
                if (line.indent() < indent) break;
                if (line.indent() != indent || !line.content().startsWith("-")) throw error("Invalid list indentation");
                String text = line.content().substring(1).strip();
                index++;
                Object value = text.isEmpty()
                    ? index < lines.size() && lines.get(index).indent() > indent ? parseBlock(lines.get(index).indent(), depth + 1) : null
                    : scalar(text);
                addNode();
                result.add(value);
            }
            return result;
        }

        private Object scalar(String text) {
            if (text.startsWith("!") || text.startsWith("&") || text.startsWith("*")) throw error("YAML tags, anchors and aliases are not supported");
            if ("[]".equals(text)) return new ArrayList<>();
            if ("{}".equals(text)) return new LinkedHashMap<String, Object>();
            if (text.startsWith("[") || text.startsWith("{")) throw error("Unsupported flow collection");
            String value = unquote(text);
            if ("null".equalsIgnoreCase(value) || "~".equals(value)) return null;
            if ("true".equalsIgnoreCase(value)) return true;
            if ("false".equalsIgnoreCase(value)) return false;
            if (value.matches("[-+]?\\d+")) {
                try { return Integer.parseInt(value); }
                catch (NumberFormatException ignored) {
                    try { return Long.parseLong(value); }
                    catch (NumberFormatException overflow) { throw error("Integer is out of range"); }
                }
            }
            if (value.matches("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)(?:[eE][-+]?\\d+)?")) {
                try { return Double.parseDouble(value); }
                catch (NumberFormatException exception) { throw error("Invalid number"); }
            }
            return value;
        }

        private int mappingColon(String text) {
            boolean single = false;
            boolean doubled = false;
            boolean escaped = false;
            for (int position = 0; position < text.length(); position++) {
                char character = text.charAt(position);
                if (escaped) { escaped = false; continue; }
                if (character == '\\' && doubled) { escaped = true; continue; }
                if (character == '\'' && !doubled) single = !single;
                else if (character == '"' && !single) doubled = !doubled;
                else if (character == ':' && !single && !doubled) return position;
            }
            return -1;
        }

        private String unquote(String text) {
            if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                String body = text.substring(1, text.length() - 1);
                return body.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
            }
            if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) return text.substring(1, text.length() - 1).replace("''", "'");
            if (text.startsWith("\"") || text.startsWith("'") || text.endsWith("\"") || text.endsWith("'")) throw error("Unterminated quoted scalar");
            return text;
        }

        private void addNode() { if (++nodes > MAX_NODES) throw error("YAML node count exceeds safe limit"); }
        private IllegalArgumentException error(String message) { int line = index < lines.size() ? lines.get(index).number() : lines.get(lines.size() - 1).number(); return new IllegalArgumentException(message + " at line " + line); }
    }
}

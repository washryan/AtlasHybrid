package org.bukkit.configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MemorySection implements ConfigurationSection {
    private final Map<String, Object> root;
    private final Map<String, Object> values;

    protected MemorySection() { this(new LinkedHashMap<>()); }

    protected MemorySection(Map<String, Object> values) {
        this.root = deepMutableCopy(values);
        this.values = root;
    }

    private MemorySection(Map<String, Object> root, Map<String, Object> values) {
        this.root = root;
        this.values = values;
    }

    @Override public Set<String> getKeys(boolean deep) { Set<String> result = new LinkedHashSet<>(); collectKeys(values, "", deep, result); return Collections.unmodifiableSet(result); }
    @Override public Map<String, Object> getValues(boolean deep) { Map<String, Object> result = new LinkedHashMap<>(); collectValues(values, "", deep, result); return Collections.unmodifiableMap(result); }
    @Override public boolean contains(String path) { return get(path) != null; }
    @Override public boolean isSet(String path) { return contains(path); }

    @Override
    public Object get(String path) {
        Object value = find(path);
        if (value instanceof Map<?, ?> map) return section(castMap(map));
        return copyValue(value);
    }

    @Override public Object get(String path, Object fallback) { Object value = get(path); return value == null ? fallback : value; }

    @Override
    public void set(String path, Object value) {
        String[] parts = path(path);
        Map<String, Object> section = values;
        for (int index = 0; index < parts.length - 1; index++) {
            Object child = section.get(parts[index]);
            if (!(child instanceof Map<?, ?>)) {
                Map<String, Object> created = new LinkedHashMap<>();
                section.put(parts[index], created);
                section = created;
            } else section = castMap(child);
        }
        if (value == null) section.remove(parts[parts.length - 1]);
        else section.put(parts[parts.length - 1], copyValue(value));
    }

    @Override public ConfigurationSection createSection(String path) { set(path, new LinkedHashMap<String, Object>()); return getConfigurationSection(path); }
    @Override public String getString(String path) { return getString(path, null); }
    @Override public String getString(String path, String fallback) { Object value = find(path); return value == null || value instanceof Map<?, ?> || value instanceof List<?> ? fallback : String.valueOf(value); }
    @Override public int getInt(String path) { return getInt(path, 0); }
    @Override public int getInt(String path, int fallback) { Object value = find(path); return value instanceof Number number ? number.intValue() : fallback; }
    @Override public boolean getBoolean(String path) { return getBoolean(path, false); }
    @Override public boolean getBoolean(String path, boolean fallback) { Object value = find(path); return value instanceof Boolean bool ? bool : fallback; }
    @Override public double getDouble(String path) { return getDouble(path, 0.0D); }
    @Override public double getDouble(String path, double fallback) { Object value = find(path); return value instanceof Number number ? number.doubleValue() : fallback; }

    @Override
    public List<String> getStringList(String path) {
        Object value = find(path);
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) if (item instanceof String || item instanceof Character || item instanceof Number || item instanceof Boolean) result.add(String.valueOf(item));
        return result;
    }

    @Override public ConfigurationSection getConfigurationSection(String path) { Object value = find(path); return value instanceof Map<?, ?> map ? section(castMap(map)) : null; }
    protected final Map<String, Object> rawValues() { return root; }
    private MemorySection section(Map<String, Object> map) { return new MemorySection(root, map); }

    private Object find(String requestedPath) {
        String[] parts = path(requestedPath);
        Object current = values;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    private static String[] path(String value) {
        Objects.requireNonNull(value, "path");
        if (value.isEmpty()) throw new IllegalArgumentException("Path cannot be empty");
        String[] parts = value.split("\\.", -1);
        for (String part : parts) if (part.isEmpty()) throw new IllegalArgumentException("Invalid path: " + value);
        return parts;
    }

    private static void collectKeys(Map<String, Object> source, String prefix, boolean deep, Set<String> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            target.add(key);
            if (deep && entry.getValue() instanceof Map<?, ?> map) collectKeys(castMap(map), key, true, target);
        }
    }

    private static void collectValues(Map<String, Object> source, String prefix, boolean deep, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            target.put(key, value instanceof Map<?, ?> map ? new MemorySection(castMap(map)) : copyValue(value));
            if (deep && value instanceof Map<?, ?> map) collectValues(castMap(map), key, true, target);
        }
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> castMap(Object value) { return (Map<String, Object>) value; }

    private static Map<String, Object> deepMutableCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) copy.put(entry.getKey(), copyValue(entry.getValue()));
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) return deepMutableCopy(castMap(map));
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) copy.add(copyValue(item));
            return copy;
        }
        return value;
    }
}

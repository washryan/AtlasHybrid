package org.bukkit.configuration.file;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;

public class FileConfiguration {
    private final Map<String, Object> values;

    protected FileConfiguration(Map<String, Object> values) {
        this.values = new LinkedHashMap<>(values);
    }

    public Object get(String path) { return values.get(path); }
    public String getString(String path) { Object value = get(path); return value == null ? null : String.valueOf(value); }
    public String getString(String path, String fallback) { String value = getString(path); return value == null ? fallback : value; }
    public boolean getBoolean(String path) { return getBoolean(path, false); }
    public boolean getBoolean(String path, boolean fallback) { Object value = get(path); return value instanceof Boolean b ? b : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }
    public int getInt(String path, int fallback) { Object value = get(path); if (value instanceof Number n) return n.intValue(); try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ignored) { return fallback; } }
    public void set(String path, Object value) { if (value == null) values.remove(path); else values.put(path, value); }
    public boolean contains(String path) { return values.containsKey(path); }
    public boolean isSet(String path) { return contains(path); }
    public List<String> getStringList(String path) {
        Object value = get(path);
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) if (item != null) result.add(String.valueOf(item));
        return result;
    }
    public Location getLocation(String path) {
        Object value = get(path);
        return value instanceof Location location ? location.clone() : null;
    }
    public Map<String, Object> getValues(boolean deep) { return Collections.unmodifiableMap(values); }
}

package org.bukkit.configuration.file;

import java.util.Map;
import org.bukkit.Location;
import org.bukkit.configuration.MemoryConfiguration;

public class FileConfiguration extends MemoryConfiguration {
    protected FileConfiguration(Map<String, Object> values) { super(values); }

    final Map<String, Object> atlasValues() { return rawValues(); }

    public Location getLocation(String path) {
        Object value = get(path);
        return value instanceof Location location ? location.clone() : null;
    }
}

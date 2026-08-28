package org.bukkit.configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ConfigurationSection {
    Set<String> getKeys(boolean deep);
    Map<String, Object> getValues(boolean deep);
    boolean contains(String path);
    boolean isSet(String path);
    Object get(String path);
    Object get(String path, Object fallback);
    void set(String path, Object value);
    ConfigurationSection createSection(String path);
    String getString(String path);
    String getString(String path, String fallback);
    int getInt(String path);
    int getInt(String path, int fallback);
    boolean getBoolean(String path);
    boolean getBoolean(String path, boolean fallback);
    double getDouble(String path);
    double getDouble(String path, double fallback);
    List<String> getStringList(String path);
    ConfigurationSection getConfigurationSection(String path);
}

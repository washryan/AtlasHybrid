package org.bukkit.plugin;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;

public interface Plugin {
    String getName();

    PluginDescriptionFile getDescription();

    Server getServer();

    Logger getLogger();

    File getDataFolder();

    FileConfiguration getConfig();

    void reloadConfig();

    void saveDefaultConfig();

    boolean isEnabled();

    void onLoad();

    void onEnable();

    void onDisable();
}

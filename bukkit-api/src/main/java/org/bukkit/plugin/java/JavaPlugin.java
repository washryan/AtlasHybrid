package org.bukkit.plugin.java;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

public abstract class JavaPlugin implements Plugin {
    private Server server;
    private PluginDescriptionFile description;
    private File dataFolder;
    private Logger logger;
    private FileConfiguration config;
    private boolean enabled;
    private boolean initialized;

    public final synchronized void atlasInitialize(
        Server server,
        PluginDescriptionFile description,
        File dataFolder,
        Logger logger
    ) {
        if (initialized) {
            throw new IllegalStateException("Plugin is already initialized");
        }
        this.server = Objects.requireNonNull(server, "server");
        this.description = Objects.requireNonNull(description, "description");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.initialized = true;
    }

    public final synchronized void atlasSetEnabled(boolean value) {
        requireInitialized();
        if (enabled == value) {
            return;
        }
        if (value) {
            enabled = true;
            try {
                onEnable();
            } catch (Throwable throwable) {
                enabled = false;
                throw throwable;
            }
        } else {
            try {
                onDisable();
            } finally {
                enabled = false;
            }
        }
    }

    @Override public void onLoad() { }
    @Override public void onEnable() { }
    @Override public void onDisable() { }

    @Override public final String getName() { return getDescription().getName(); }
    @Override public final PluginDescriptionFile getDescription() { requireInitialized(); return description; }
    @Override public final Server getServer() { requireInitialized(); return server; }
    @Override public final Logger getLogger() { requireInitialized(); return logger; }
    @Override public final File getDataFolder() { requireInitialized(); return dataFolder; }
    @Override public final boolean isEnabled() { return enabled; }

    public final PluginCommand getCommand(String name) {
        PluginCommand command = getServer().getPluginCommand(name);
        return command != null && command.getPlugin() == this ? command : null;
    }

    @Override
    public final synchronized FileConfiguration getConfig() {
        requireInitialized();
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    @Override
    public final synchronized void reloadConfig() {
        requireInitialized();
        config = YamlConfiguration.loadConfiguration(dataFolder.toPath().resolve("config.yml"));
    }

    @Override
    public final void saveDefaultConfig() {
        requireInitialized();
        File target = new File(dataFolder, "config.yml");
        if (target.isFile()) {
            return;
        }
        if (!dataFolder.isDirectory() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Cannot create plugin data directory " + dataFolder);
        }
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (input != null) {
                Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save default config for " + getName(), exception);
        }
    }

    private void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Plugin has not been initialized by AtlasHybrid");
        }
    }
}

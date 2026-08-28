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
    private boolean bootstrapBound;

    protected JavaPlugin() {
        JavaPluginBootstrap.Context context = JavaPluginBootstrap.findFor(getClass().getClassLoader());
        if (context != null) {
            bind(context.server(), context.description(), context.dataFolder(), context.logger());
            bootstrapBound = true;
        }
    }

    public final synchronized void atlasInitialize(
        Server server,
        PluginDescriptionFile description,
        File dataFolder,
        Logger logger
    ) {
        if (initialized) {
            throw new IllegalStateException("Plugin is already initialized");
        }
        if (bootstrapBound) {
            if (this.server != server || this.description != description
                || !this.dataFolder.equals(dataFolder) || this.logger != logger) {
                throw new IllegalStateException("Final plugin context does not match its bootstrap context");
            }
        } else {
            bind(server, description, dataFolder, logger);
        }
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
    @Override public final PluginDescriptionFile getDescription() { requireContext("JavaPlugin#getDescription"); return description; }
    @Override public final Server getServer() { requireContext("JavaPlugin#getServer"); return server; }
    @Override public final Logger getLogger() { requireContext("JavaPlugin#getLogger"); return logger; }
    @Override public final File getDataFolder() { requireContext("JavaPlugin#getDataFolder"); return dataFolder; }
    @Override public final boolean isEnabled() { return enabled; }

    public final PluginCommand getCommand(String name) {
        requireInitialized("JavaPlugin#getCommand");
        PluginCommand command = getServer().getPluginCommand(name);
        return command != null && command.getPlugin() == this ? command : null;
    }

    @Override
    public final synchronized FileConfiguration getConfig() {
        requireContext("JavaPlugin#getConfig");
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    @Override
    public final synchronized void reloadConfig() {
        requireContext("JavaPlugin#reloadConfig");
        config = YamlConfiguration.loadConfiguration(dataFolder.toPath().resolve("config.yml"));
    }

    @Override
    public final void saveDefaultConfig() {
        requireContext("JavaPlugin#saveDefaultConfig");
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

    public final synchronized void saveConfig() {
        requireContext("JavaPlugin#saveConfig");
        if (config == null) return;
        YamlConfiguration.saveConfiguration(config, dataFolder.toPath().resolve("config.yml"));
    }

    private void bind(Server server, PluginDescriptionFile description, File dataFolder, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.description = Objects.requireNonNull(description, "description");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    private void requireContext(String api) {
        if (!initialized && !bootstrapBound) {
            throw new PluginBootstrapPhaseException(api, "CONSTRUCTION");
        }
    }

    private void requireInitialized() {
        requireInitialized("JavaPlugin lifecycle");
    }

    private void requireInitialized(String api) {
        if (!initialized) {
            throw new PluginBootstrapPhaseException(api, "CONSTRUCTION");
        }
    }
}

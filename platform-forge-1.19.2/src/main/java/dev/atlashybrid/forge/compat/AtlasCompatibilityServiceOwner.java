package dev.atlashybrid.forge.compat;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

/**
 * Atlas-owned identity used only to own optional compatibility services.
 * It is deliberately not registered with the Bukkit plugin manager.
 */
public final class AtlasCompatibilityServiceOwner implements Plugin, AutoCloseable {
    private static final PluginDescriptionFile DESCRIPTION = new PluginDescriptionFile(
        "AtlasHybridCompatibility",
        "0.1.0-alpha",
        AtlasCompatibilityServiceOwner.class.getName(),
        "1.19",
        "Internal owner for AtlasHybrid compatibility services.",
        List.of("AtlasHybrid"),
        List.of(),
        List.of(),
        Set.of()
    );

    private final Server server;
    private final Logger logger;
    private final File dataFolder;
    private final FileConfiguration config = new YamlConfiguration();
    private volatile boolean enabled = true;

    public AtlasCompatibilityServiceOwner(Server server, Logger logger, File dataFolder) {
        this.server = java.util.Objects.requireNonNull(server, "server");
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
        this.dataFolder = java.util.Objects.requireNonNull(dataFolder, "dataFolder");
    }

    @Override public String getName() { return DESCRIPTION.getName(); }
    @Override public PluginDescriptionFile getDescription() { return DESCRIPTION; }
    @Override public Server getServer() { return server; }
    @Override public Logger getLogger() { return logger; }
    @Override public File getDataFolder() { return dataFolder; }
    @Override public FileConfiguration getConfig() { return config; }
    @Override public void reloadConfig() { }
    @Override public void saveDefaultConfig() { }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void onLoad() { }
    @Override public void onEnable() { }
    @Override public void onDisable() { enabled = false; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { return false; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return List.of(); }
    @Override public void close() { onDisable(); }
}

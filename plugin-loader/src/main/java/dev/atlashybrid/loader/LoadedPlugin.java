package dev.atlashybrid.loader;

import org.bukkit.plugin.java.JavaPlugin;

public record LoadedPlugin(PluginCandidate candidate, JavaPlugin plugin, AtlasPluginClassLoader classLoader) {
}

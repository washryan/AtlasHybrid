package org.bukkit.event.server;

import org.bukkit.plugin.Plugin;

/** Base type for events concerning a plugin lifecycle transition. */
public abstract class PluginEvent extends ServerEvent {
    private final Plugin plugin;

    public PluginEvent(Plugin plugin) {
        this.plugin = plugin;
    }

    public Plugin getPlugin() {
        return plugin;
    }
}

package org.bukkit.event.server;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

/** Called after a plugin has enabled successfully. */
public class PluginEnableEvent extends PluginEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public PluginEnableEvent(Plugin plugin) {
        super(plugin);
    }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

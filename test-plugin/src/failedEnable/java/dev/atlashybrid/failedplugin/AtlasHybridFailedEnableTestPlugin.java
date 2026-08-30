package dev.atlashybrid.failedplugin;

import dev.atlashybrid.runtime.permission.AtlasPermissions;
import dev.atlashybrid.runtime.permission.PermissionProviderPriority;
import java.util.Optional;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class AtlasHybridFailedEnableTestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        Listener listener = new Listener() { };
        getServer().getPluginManager().registerEvent(FailureProbeEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> { }, this, false);
        getServer().getPluginManager().registerEvent(PluginEnableEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> {
                throw new IllegalStateException("Failed plugin received false PluginEnableEvent");
            }, this, false);
        getServer().getPluginManager().registerEvent(PluginDisableEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> {
                throw new IllegalStateException("Failed plugin received false PluginDisableEvent");
            }, this, false);
        getServer().getScheduler().runTaskLater(this, () -> { }, 200);
        getServer().getServicesManager().register(Runnable.class, () -> { }, this, ServicePriority.Normal);
        AtlasPermissions.providers().register(this,
            (subject, permission) -> Optional.empty(), PermissionProviderPriority.NORMAL);
        getServer().getConsoleSender().addAttachment(this, "atlas.failed-enable.integration", true);
        throw new IllegalStateException("Expected AtlasHybrid failed-enable integration probe");
    }

    @Override
    public void onDisable() {
        getLogger().info("[AtlasHybridFailedEnableTestPlugin] onDisable after failed enable");
    }

    public static final class FailureProbeEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }
}

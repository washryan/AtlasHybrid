package org.bukkit.event.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PluginLifecycleEventTest {
    @Test
    void enableEventCarriesRealPluginAndOwnsHandlers() {
        Plugin plugin = plugin();
        PluginEnableEvent event = new PluginEnableEvent(plugin);
        assertSame(plugin, event.getPlugin());
        assertSame(PluginEnableEvent.getHandlerList(), event.getHandlers());
        assertFalse(event instanceof Cancellable);
        assertFalse(event.isAsynchronous());
    }

    @Test
    void disableEventCarriesRealPluginAndOwnsIndependentHandlers() {
        Plugin plugin = plugin();
        PluginDisableEvent event = new PluginDisableEvent(plugin);
        assertSame(plugin, event.getPlugin());
        assertSame(PluginDisableEvent.getHandlerList(), event.getHandlers());
        assertFalse(event instanceof Cancellable);
        assertFalse(event.isAsynchronous());
        assertFalse(event.getHandlers() == PluginEnableEvent.getHandlerList());
        assertSame(HandlerList.class, event.getHandlers().getClass());
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[] { Plugin.class },
            (proxy, method, arguments) -> method.getReturnType() == boolean.class ? false : null);
    }
}

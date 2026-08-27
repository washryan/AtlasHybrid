package dev.atlashybrid.runtime.event;

import dev.atlashybrid.diagnostics.CompatibilityRuntime;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;

public final class AtlasPluginManager implements PluginManager {
    private final Logger logger;
    private final Map<String, Plugin> plugins = new LinkedHashMap<>();

    public AtlasPluginManager(Logger logger) {
        this.logger = logger;
    }

    public synchronized void addPlugin(Plugin plugin) {
        String key = key(plugin.getName());
        if (plugins.putIfAbsent(key, plugin) != null) {
            throw new IllegalStateException("Duplicate plugin " + plugin.getName());
        }
    }

    public synchronized void removePlugin(Plugin plugin) {
        plugins.remove(key(plugin.getName()), plugin);
    }

    @Override
    public void registerEvents(Listener listener, Plugin plugin) {
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("Plugin must be enabled before registering events: " + plugin.getName());
        }
        for (Method method : listener.getClass().getDeclaredMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler == null) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1 || !Event.class.isAssignableFrom(parameters[0])) {
                throw new IllegalArgumentException("@EventHandler method must accept exactly one Event: " + method);
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType = (Class<? extends Event>) parameters[0];
            try {
                Method accessor = eventType.getMethod("getHandlerList");
                Object value = accessor.invoke(null);
                if (!(value instanceof org.bukkit.event.HandlerList list)) {
                    throw new IllegalArgumentException("getHandlerList returned wrong type for " + eventType.getName());
                }
                list.register(new RegisteredListener(listener, method, handler.priority(), plugin, handler.ignoreCancelled()));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException("Event lacks public static getHandlerList(): " + eventType.getName(), exception);
            }
        }
    }

    @Override
    public void callEvent(Event event) {
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
            try (CompatibilityRuntime.Scope ignored = CompatibilityRuntime.enter(listener.getPlugin().getName())) {
                listener.callEvent(event);
            } catch (Throwable throwable) {
                logger.log(Level.SEVERE, "Plugin " + listener.getPlugin().getName() + " failed handling " + event.getEventName(), throwable);
            }
        }
    }

    public void unregisterPlugin(Plugin plugin, List<Class<? extends Event>> knownEvents) {
        for (Class<? extends Event> event : knownEvents) {
            try {
                org.bukkit.event.HandlerList list = (org.bukkit.event.HandlerList) event.getMethod("getHandlerList").invoke(null);
                list.unregister(plugin);
            } catch (ReflectiveOperationException exception) {
                logger.log(Level.WARNING, "Cannot unregister event handlers for " + event.getName(), exception);
            }
        }
        removePlugin(plugin);
    }

    @Override public synchronized Plugin[] getPlugins() { return plugins.values().toArray(Plugin[]::new); }
    @Override public synchronized Plugin getPlugin(String name) { return plugins.get(key(name)); }
    @Override public boolean isPluginEnabled(String name) { Plugin plugin = getPlugin(name); return plugin != null && plugin.isEnabled(); }
    public synchronized List<Plugin> snapshot() { return new ArrayList<>(plugins.values()); }
    private static String key(String value) { return value.toLowerCase(Locale.ROOT); }
}

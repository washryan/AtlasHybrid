package org.bukkit.plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class RegisteredListener {
    private final Listener listener;
    private final Method method;
    private final EventPriority priority;
    private final Plugin plugin;
    private final boolean ignoreCancelled;

    public RegisteredListener(Listener listener, Method method, EventPriority priority, Plugin plugin, boolean ignoreCancelled) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.method = Objects.requireNonNull(method, "method");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.ignoreCancelled = ignoreCancelled;
        method.setAccessible(true);
    }

    public Listener getListener() { return listener; }
    public EventPriority getPriority() { return priority; }
    public Plugin getPlugin() { return plugin; }

    public void callEvent(Event event) throws Throwable {
        if (!plugin.isEnabled()) return;
        if (ignoreCancelled && event instanceof Cancellable cancellable && cancellable.isCancelled()) return;
        try {
            method.invoke(listener, event);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}

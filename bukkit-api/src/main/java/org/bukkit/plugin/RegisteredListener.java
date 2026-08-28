package org.bukkit.plugin;

import java.util.Objects;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class RegisteredListener {
    private final Listener listener;
    private final EventExecutor executor;
    private final EventPriority priority;
    private final Plugin plugin;
    private final boolean ignoreCancelled;

    public RegisteredListener(Listener listener, EventExecutor executor, EventPriority priority, Plugin plugin, boolean ignoreCancelled) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.ignoreCancelled = ignoreCancelled;
    }

    public Listener getListener() { return listener; }
    public EventPriority getPriority() { return priority; }
    public Plugin getPlugin() { return plugin; }
    public boolean isIgnoringCancelled() { return ignoreCancelled; }

    public void callEvent(Event event) throws org.bukkit.event.EventException {
        if (!plugin.isEnabled()) return;
        if (ignoreCancelled && event instanceof Cancellable cancellable && cancellable.isCancelled()) return;
        executor.execute(listener, event);
    }
}

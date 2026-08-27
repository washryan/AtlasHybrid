package org.bukkit.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

public final class HandlerList {
    private final List<RegisteredListener> listeners = new ArrayList<>();
    private volatile RegisteredListener[] baked = new RegisteredListener[0];

    public synchronized void register(RegisteredListener listener) {
        listeners.add(listener);
        listeners.sort(Comparator.comparing(RegisteredListener::getPriority));
        baked = listeners.toArray(RegisteredListener[]::new);
    }

    public synchronized void unregister(Plugin plugin) {
        listeners.removeIf(listener -> listener.getPlugin() == plugin);
        baked = listeners.toArray(RegisteredListener[]::new);
    }

    public RegisteredListener[] getRegisteredListeners() {
        return baked.clone();
    }
}

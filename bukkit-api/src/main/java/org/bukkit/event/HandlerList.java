package org.bukkit.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

public final class HandlerList {
    private static final List<HandlerList> ALL_LISTS = new ArrayList<>();

    private final EnumMap<EventPriority, List<RegisteredListener>> slots = new EnumMap<>(EventPriority.class);
    private volatile RegisteredListener[] baked;

    public HandlerList() {
        for (EventPriority priority : EventPriority.values()) slots.put(priority, new ArrayList<>());
        synchronized (ALL_LISTS) { ALL_LISTS.add(this); }
    }

    public synchronized void register(RegisteredListener listener) {
        List<RegisteredListener> priority = slots.get(listener.getPriority());
        if (priority.contains(listener)) {
            throw new IllegalStateException("Listener is already registered at " + listener.getPriority());
        }
        priority.add(listener);
        baked = null;
    }

    public void registerAll(Collection<RegisteredListener> listeners) {
        listeners.forEach(this::register);
    }

    public synchronized void unregister(RegisteredListener listener) {
        if (slots.get(listener.getPriority()).remove(listener)) baked = null;
    }

    public synchronized void unregister(Plugin plugin) {
        if (slots.values().stream().mapToInt(list -> removePlugin(list, plugin)).sum() > 0) baked = null;
    }

    public synchronized void unregister(org.bukkit.event.Listener listener) {
        if (slots.values().stream().mapToInt(list -> removeListener(list, listener)).sum() > 0) baked = null;
    }

    public synchronized void bake() {
        if (baked != null) return;
        List<RegisteredListener> ordered = new ArrayList<>();
        for (EventPriority priority : EventPriority.values()) ordered.addAll(slots.get(priority));
        baked = ordered.toArray(RegisteredListener[]::new);
    }

    public RegisteredListener[] getRegisteredListeners() {
        if (baked == null) bake();
        return baked.clone();
    }

    public static void bakeAll() {
        for (HandlerList list : getHandlerLists()) list.bake();
    }

    public static void unregisterAll() {
        for (HandlerList list : getHandlerLists()) list.clear();
    }

    public static void unregisterAll(Plugin plugin) {
        for (HandlerList list : getHandlerLists()) list.unregister(plugin);
    }

    public static void unregisterAll(org.bukkit.event.Listener listener) {
        for (HandlerList list : getHandlerLists()) list.unregister(listener);
    }

    public static ArrayList<RegisteredListener> getRegisteredListeners(Plugin plugin) {
        ArrayList<RegisteredListener> result = new ArrayList<>();
        for (HandlerList list : getHandlerLists()) {
            for (RegisteredListener listener : list.getRegisteredListeners()) {
                if (listener.getPlugin().equals(plugin)) result.add(listener);
            }
        }
        return result;
    }

    public static ArrayList<HandlerList> getHandlerLists() {
        synchronized (ALL_LISTS) { return new ArrayList<>(ALL_LISTS); }
    }

    private synchronized void clear() {
        slots.values().forEach(List::clear);
        baked = null;
    }

    private static int removePlugin(List<RegisteredListener> listeners, Plugin plugin) {
        int before = listeners.size();
        listeners.removeIf(listener -> listener.getPlugin().equals(plugin));
        return before - listeners.size();
    }

    private static int removeListener(List<RegisteredListener> listeners, org.bukkit.event.Listener listener) {
        int before = listeners.size();
        listeners.removeIf(registered -> registered.getListener().equals(listener));
        return before - listeners.size();
    }
}

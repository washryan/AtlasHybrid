package org.bukkit.plugin;

import java.util.Objects;

public final class RegisteredServiceProvider<T> implements Comparable<RegisteredServiceProvider<?>> {
    private final Class<T> service;
    private final T provider;
    private final ServicePriority priority;
    private final Plugin plugin;

    public RegisteredServiceProvider(Class<T> service, T provider, ServicePriority priority, Plugin plugin) {
        this.service = Objects.requireNonNull(service, "service");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public Class<T> getService() { return service; }
    public Plugin getPlugin() { return plugin; }
    public T getProvider() { return provider; }
    public ServicePriority getPriority() { return priority; }

    @Override
    public int compareTo(RegisteredServiceProvider<?> other) {
        return other.priority.ordinal() - priority.ordinal();
    }
}

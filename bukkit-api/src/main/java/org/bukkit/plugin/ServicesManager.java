package org.bukkit.plugin;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ServicesManager {
    <T> void register(Class<T> service, T provider, Plugin plugin, ServicePriority priority);
    void unregisterAll(Plugin plugin);
    void unregister(Class<?> service, Object provider);
    void unregister(Object provider);
    <T> T load(Class<T> service);
    <T> RegisteredServiceProvider<T> getRegistration(Class<T> service);
    List<RegisteredServiceProvider<?>> getRegistrations(Plugin plugin);
    <T> Collection<RegisteredServiceProvider<T>> getRegistrations(Class<T> service);
    Set<Class<?>> getKnownServices();
    boolean isProvidedFor(Class<?> service);
}

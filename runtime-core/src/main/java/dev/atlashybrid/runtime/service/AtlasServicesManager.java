package dev.atlashybrid.runtime.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

public final class AtlasServicesManager implements ServicesManager {
    private static final Comparator<Entry<?>> ORDER = Comparator
        .comparing((Entry<?> entry) -> entry.registration().getPriority().ordinal()).reversed()
        .thenComparingLong(Entry::sequence);

    private final AtomicLong sequence = new AtomicLong();
    private volatile List<Entry<?>> entries = List.of();

    @Override
    public synchronized <T> void register(Class<T> service, T provider, Plugin plugin, ServicePriority priority) {
        RegisteredServiceProvider<T> registration = new RegisteredServiceProvider<>(service, provider, priority, plugin);
        ArrayList<Entry<?>> next = new ArrayList<>(entries);
        next.add(new Entry<>(registration, sequence.incrementAndGet()));
        next.sort(ORDER);
        entries = List.copyOf(next);
    }

    @Override public synchronized void unregisterAll(Plugin plugin) { entries = entries.stream().filter(entry -> entry.registration().getPlugin() != plugin).toList(); }
    @Override public synchronized void unregister(Class<?> service, Object provider) { entries = entries.stream().filter(entry -> entry.registration().getService() != service || entry.registration().getProvider() != provider).toList(); }
    @Override public synchronized void unregister(Object provider) { entries = entries.stream().filter(entry -> entry.registration().getProvider() != provider).toList(); }

    @Override
    public <T> T load(Class<T> service) {
        RegisteredServiceProvider<T> registration = getRegistration(service);
        return registration == null ? null : registration.getProvider();
    }

    @Override
    public <T> RegisteredServiceProvider<T> getRegistration(Class<T> service) {
        Objects.requireNonNull(service, "service");
        for (Entry<?> entry : entries) {
            if (entry.registration().getService() == service) return cast(entry.registration());
        }
        return null;
    }

    @Override
    public List<RegisteredServiceProvider<?>> getRegistrations(Plugin plugin) {
        ArrayList<RegisteredServiceProvider<?>> result = new ArrayList<>();
        for (Entry<?> entry : entries) {
            if (entry.registration().getPlugin() == plugin) result.add(entry.registration());
        }
        return List.copyOf(result);
    }

    @Override
    public <T> Collection<RegisteredServiceProvider<T>> getRegistrations(Class<T> service) {
        ArrayList<RegisteredServiceProvider<T>> result = new ArrayList<>();
        for (Entry<?> entry : entries) {
            if (entry.registration().getService() == service) result.add(cast(entry.registration()));
        }
        return List.copyOf(result);
    }

    @Override
    public Set<Class<?>> getKnownServices() {
        LinkedHashSet<Class<?>> result = new LinkedHashSet<>();
        for (Entry<?> entry : entries) result.add(entry.registration().getService());
        return Set.copyOf(result);
    }

    @Override public boolean isProvidedFor(Class<?> service) { return getRegistration(service) != null; }
    public int size() { return entries.size(); }

    @SuppressWarnings("unchecked")
    private static <T> RegisteredServiceProvider<T> cast(RegisteredServiceProvider<?> value) {
        return (RegisteredServiceProvider<T>) value;
    }

    private record Entry<T>(RegisteredServiceProvider<T> registration, long sequence) { }
}

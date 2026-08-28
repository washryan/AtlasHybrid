package dev.atlashybrid.runtime.permission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;

public final class PermissionProviderRegistry {
    private static final Comparator<Registration> ORDER = Comparator
        .comparing((Registration registration) -> registration.priority().ordinal()).reversed()
        .thenComparingLong(Registration::sequence);

    private final Logger logger;
    private final AtomicLong sequence = new AtomicLong();
    private volatile List<Registration> registrations = List.of();

    public PermissionProviderRegistry(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized Registration register(Plugin owner, PermissionProvider provider, PermissionProviderPriority priority) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(priority, "priority");
        Registration registration = new Registration(owner, provider, priority, sequence.incrementAndGet());
        ArrayList<Registration> next = new ArrayList<>(registrations);
        next.add(registration);
        next.sort(ORDER);
        registrations = List.copyOf(next);
        return registration;
    }

    public synchronized void unregister(PermissionProvider provider) {
        registrations = registrations.stream().filter(registration -> registration.provider() != provider).toList();
    }

    public synchronized void unregisterAll(Plugin owner) {
        registrations = registrations.stream().filter(registration -> registration.owner() != owner).toList();
    }

    public Optional<Boolean> query(PermissionSubject subject, String permission) {
        String normalized = Permission.normalize(permission);
        for (Registration registration : registrations) {
            if (!registration.owner().isEnabled()) continue;
            try {
                Optional<Boolean> result = registration.provider().query(subject, normalized);
                if (result != null && result.isPresent()) return result;
            } catch (Throwable throwable) {
                logger.log(Level.SEVERE,
                    "Permission provider owned by " + registration.owner().getName()
                        + " failed querying " + normalized + "; falling back to the next provider/core",
                    throwable);
            }
        }
        return Optional.empty();
    }

    public int size() { return registrations.size(); }
    public List<Registration> registrations() { return registrations; }

    public record Registration(Plugin owner, PermissionProvider provider, PermissionProviderPriority priority, long sequence) {
        public Registration {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(priority, "priority");
        }
    }
}

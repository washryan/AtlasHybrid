package dev.atlashybrid.loader;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/** Registry of explicit compatibility capabilities. Entries are not Bukkit plugins. */
public final class VirtualPluginDependencyRegistry {
    private final Logger logger;
    private final Map<String, VirtualPluginDependency> capabilities = new LinkedHashMap<>();

    public VirtualPluginDependencyRegistry(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized VirtualPluginDependency registerAvailable(
        String compatibilityName,
        Object owner,
        String version,
        String description
    ) {
        VirtualPluginDependency proposed = new VirtualPluginDependency(
            compatibilityName, VirtualDependencyState.AVAILABLE, owner, version, description);
        String key = key(compatibilityName);
        VirtualPluginDependency existing = capabilities.get(key);
        if (existing != null) {
            if (existing.owner() != owner) {
                throw new IllegalStateException("Virtual dependency " + compatibilityName
                    + " is already owned by " + existing.owner());
            }
            return existing;
        }
        capabilities.put(key, proposed);
        logger.info("[AtlasHybrid Virtual Dependency] registered name=" + compatibilityName
            + " version=" + proposed.version());
        return proposed;
    }

    public synchronized boolean unregister(String compatibilityName, Object owner) {
        String key = key(compatibilityName);
        VirtualPluginDependency existing = capabilities.get(key);
        if (existing == null || existing.owner() != owner) return false;
        capabilities.remove(key);
        logger.info("[AtlasHybrid Virtual Dependency] unavailable name=" + existing.compatibilityName());
        return true;
    }

    public synchronized int unregisterAll(Object owner) {
        int before = capabilities.size();
        capabilities.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
        int removed = before - capabilities.size();
        if (removed > 0) logger.info("[AtlasHybrid Virtual Dependency] owner cleanup removed=" + removed);
        return removed;
    }

    public synchronized Optional<VirtualPluginDependency> findAvailable(String compatibilityName) {
        VirtualPluginDependency capability = capabilities.get(key(compatibilityName));
        return capability != null && capability.state() == VirtualDependencyState.AVAILABLE
            ? Optional.of(capability) : Optional.empty();
    }

    public synchronized VirtualDependencyState state(String compatibilityName) {
        return findAvailable(compatibilityName).isPresent()
            ? VirtualDependencyState.AVAILABLE : VirtualDependencyState.UNAVAILABLE;
    }

    public synchronized Collection<VirtualPluginDependency> availableCapabilities() {
        return List.copyOf(capabilities.values());
    }

    public synchronized int size() { return capabilities.size(); }

    static String key(String value) {
        Objects.requireNonNull(value, "compatibilityName");
        return value.toLowerCase(Locale.ROOT);
    }
}

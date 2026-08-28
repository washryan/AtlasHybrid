package dev.atlashybrid.runtime.permission;

import java.util.Objects;

public final class AtlasPermissions {
    private static volatile PermissionProviderRegistry providers;

    private AtlasPermissions() { }

    public static PermissionProviderRegistry providers() {
        PermissionProviderRegistry current = providers;
        if (current == null) throw new IllegalStateException("AtlasHybrid permission providers are not initialized");
        return current;
    }

    public static synchronized void install(PermissionProviderRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        if (providers != null && providers != registry) throw new IllegalStateException("Permission provider registry is already installed");
        providers = registry;
    }

    public static synchronized void clear(PermissionProviderRegistry registry) {
        if (providers == registry) providers = null;
    }
}

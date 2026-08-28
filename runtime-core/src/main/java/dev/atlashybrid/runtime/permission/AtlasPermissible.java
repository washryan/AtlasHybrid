package dev.atlashybrid.runtime.permission;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.ServerOperator;

public final class AtlasPermissible extends PermissibleBase implements AutoCloseable {
    private final AtlasPermissionRegistry registry;
    private final PermissionProviderRegistry providers;
    private final Supplier<PermissionSubject> subject;

    public AtlasPermissible(
        ServerOperator operator,
        AtlasPermissionRegistry registry,
        PermissionProviderRegistry providers,
        Supplier<PermissionSubject> subject
    ) {
        super(operator);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.subject = Objects.requireNonNull(subject, "subject");
        registry.registerSubject(this);
    }

    @Override
    public boolean hasPermission(String name) {
        String normalized = Permission.normalize(name);
        Boolean attachment = attachmentValue(normalized);
        if (attachment != null) return attachment;
        Optional<Boolean> provider = providers.query(subject.get(), normalized);
        return provider.orElseGet(() -> coreValue(normalized));
    }

    @Override
    public void close() {
        closePermissionState();
        registry.unregisterSubject(this);
    }
}

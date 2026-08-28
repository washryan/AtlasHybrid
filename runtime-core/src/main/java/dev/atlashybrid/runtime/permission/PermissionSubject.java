package dev.atlashybrid.runtime.permission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PermissionSubject(String name, UUID uniqueId, Type type, boolean op) {
    public PermissionSubject {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }

    public Optional<UUID> optionalUniqueId() { return Optional.ofNullable(uniqueId); }

    public enum Type { PLAYER, CONSOLE, OTHER }
}

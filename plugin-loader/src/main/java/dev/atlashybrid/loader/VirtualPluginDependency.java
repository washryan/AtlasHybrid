package dev.atlashybrid.loader;

import java.util.Objects;

public record VirtualPluginDependency(
    String compatibilityName,
    VirtualDependencyState state,
    Object owner,
    String version,
    String description
) {
    public VirtualPluginDependency {
        compatibilityName = requireText(compatibilityName, "compatibilityName");
        state = Objects.requireNonNull(state, "state");
        owner = Objects.requireNonNull(owner, "owner");
        version = version == null ? "" : version;
        description = description == null ? "" : description;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}

package org.bukkit;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.plugin.Plugin;

public final class NamespacedKey {
    public static final String MINECRAFT = "minecraft";
    public static final String BUKKIT = "bukkit";

    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9/._-]+");

    private final String namespace;
    private final String key;

    public NamespacedKey(String namespace, String key) {
        this.namespace = validate(namespace, VALID_NAMESPACE, "namespace");
        this.key = validate(key, VALID_KEY, "key");
        if (toString().length() >= 256) {
            throw new IllegalArgumentException("NamespacedKey must be less than 256 characters");
        }
    }

    public NamespacedKey(Plugin plugin, String key) {
        this(Objects.requireNonNull(plugin, "plugin").getName().toLowerCase(Locale.ROOT),
            Objects.requireNonNull(key, "key").toLowerCase(Locale.ROOT));
    }

    public String getNamespace() { return namespace; }
    public String getKey() { return key; }

    public static NamespacedKey minecraft(String key) {
        return new NamespacedKey(MINECRAFT, key);
    }

    public static NamespacedKey fromString(String value) {
        return fromString(value, null);
    }

    public static NamespacedKey fromString(String value, Plugin defaultNamespace) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Input string must not be empty or null");
        }
        String[] parts = value.split(":", -1);
        if (parts.length > 2) return null;
        if (parts.length == 1) {
            if (!VALID_KEY.matcher(parts[0]).matches()) return null;
            return defaultNamespace == null ? minecraft(parts[0]) : new NamespacedKey(defaultNamespace, parts[0]);
        }
        if (!VALID_KEY.matcher(parts[1]).matches()) return null;
        if (parts[0].isEmpty()) {
            return defaultNamespace == null ? minecraft(parts[1]) : new NamespacedKey(defaultNamespace, parts[1]);
        }
        if (!VALID_NAMESPACE.matcher(parts[0]).matches()) return null;
        return new NamespacedKey(parts[0], parts[1]);
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof NamespacedKey namespaced
            && namespace.equals(namespaced.namespace) && key.equals(namespaced.key);
    }

    @Override public int hashCode() {
        return 47 * (47 * 5 + namespace.hashCode()) + key.hashCode();
    }

    @Override public String toString() {
        return namespace + ':' + key;
    }

    private static String validate(String value, Pattern pattern, String component) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + component + ": " + value);
        }
        return value;
    }
}

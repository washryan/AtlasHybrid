package org.bukkit.permissions;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Bukkit;

public class Permission {
    public static final PermissionDefault DEFAULT_PERMISSION = PermissionDefault.OP;

    private final String name;
    private String description;
    private PermissionDefault defaultValue;
    private Map<String, Boolean> childPermissions;

    public Permission(String name) {
        this(name, null, DEFAULT_PERMISSION, null);
    }

    public Permission(String name, String description) {
        this(name, description, DEFAULT_PERMISSION, null);
    }

    public Permission(String name, PermissionDefault defaultValue) {
        this(name, null, defaultValue, null);
    }

    public Permission(String name, String description, PermissionDefault defaultValue) {
        this(name, description, defaultValue, null);
    }

    public Permission(String name, Map<String, Boolean> children) {
        this(name, null, DEFAULT_PERMISSION, children);
    }

    public Permission(String name, String description, Map<String, Boolean> children) {
        this(name, description, DEFAULT_PERMISSION, children);
    }

    public Permission(String name, PermissionDefault defaultValue, Map<String, Boolean> children) {
        this(name, null, defaultValue, children);
    }

    public Permission(String name, String description, PermissionDefault defaultValue, Map<String, Boolean> children) {
        this.name = normalize(name);
        this.description = description == null ? "" : description;
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        setChildren(children);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value == null ? "" : value; }
    public PermissionDefault getDefault() { return defaultValue; }

    public void setDefault(PermissionDefault value) {
        defaultValue = Objects.requireNonNull(value, "value");
        recalculatePermissibles();
    }

    public Map<String, Boolean> getChildren() { return childPermissions; }

    public void setChildren(Map<String, Boolean> children) {
        LinkedHashMap<String, Boolean> normalized = new LinkedHashMap<>();
        if (children != null) {
            children.forEach((key, value) -> normalized.put(normalize(key), Objects.requireNonNull(value, "child value")));
        }
        childPermissions = normalized;
        recalculatePermissiblesIfAvailable();
    }

    public Permission addParent(String name, boolean value) {
        Permission parent = Bukkit.getPluginManager().getPermission(name);
        if (parent == null) {
            parent = new Permission(name);
            Bukkit.getPluginManager().addPermission(parent);
        }
        addParent(parent, value);
        return parent;
    }

    public void addParent(Permission permission, boolean value) {
        permission.getChildren().put(name, value);
        permission.recalculatePermissibles();
    }

    public Set<Permissible> getPermissibles() {
        return Bukkit.getPluginManager().getPermissionSubscriptions(name);
    }

    public void recalculatePermissibles() {
        Bukkit.getPluginManager().recalculatePermissionDefaults(this);
    }

    private void recalculatePermissiblesIfAvailable() {
        try {
            recalculatePermissibles();
        } catch (IllegalStateException ignored) {
            // Permissions may be constructed before Bukkit has installed a server.
        }
    }

    public static String normalize(String value) {
        Objects.requireNonNull(value, "permission name");
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Permission name cannot be empty");
        return normalized;
    }
}

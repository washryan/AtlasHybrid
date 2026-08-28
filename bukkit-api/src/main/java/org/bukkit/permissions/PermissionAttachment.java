package org.bukkit.permissions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.plugin.Plugin;

public class PermissionAttachment {
    private final Plugin plugin;
    private final Permissible permissible;
    private final Map<String, Boolean> permissionValues = new LinkedHashMap<>();
    private PermissionRemovedExecutor removalCallback;

    public PermissionAttachment(Plugin plugin, Permissible permissible) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.permissible = Objects.requireNonNull(permissible, "permissible");
    }

    public Plugin getPlugin() { return plugin; }
    public Permissible getPermissible() { return permissible; }
    public PermissionRemovedExecutor getRemovalCallback() { return removalCallback; }
    public void setRemovalCallback(PermissionRemovedExecutor callback) { removalCallback = callback; }
    public synchronized Map<String, Boolean> getPermissions() { return Collections.unmodifiableMap(new LinkedHashMap<>(permissionValues)); }

    public synchronized void setPermission(String name, boolean value) {
        permissionValues.put(Permission.normalize(name), value);
        permissible.recalculatePermissions();
    }

    public void setPermission(Permission permission, boolean value) {
        setPermission(Objects.requireNonNull(permission, "permission").getName(), value);
    }

    public synchronized void unsetPermission(String name) {
        permissionValues.remove(Permission.normalize(name));
        permissible.recalculatePermissions();
    }

    public void unsetPermission(Permission permission) {
        unsetPermission(Objects.requireNonNull(permission, "permission").getName());
    }

    public boolean remove() {
        try {
            permissible.removeAttachment(this);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    synchronized Map<String, Boolean> snapshotPermissions() {
        return new LinkedHashMap<>(permissionValues);
    }
}

package dev.atlashybrid.runtime.permission;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;

public final class AtlasPermissionRegistry {
    private final Map<String, Permission> permissions = new LinkedHashMap<>();
    private final Map<String, Set<Permissible>> permissionSubscriptions = new LinkedHashMap<>();
    private final Map<Boolean, Set<Permissible>> defaultSubscriptions = new LinkedHashMap<>();
    private final Set<AtlasPermissible> subjects = ConcurrentHashMap.newKeySet();

    public AtlasPermissionRegistry() {
        defaultSubscriptions.put(true, ConcurrentHashMap.newKeySet());
        defaultSubscriptions.put(false, ConcurrentHashMap.newKeySet());
    }

    public synchronized void addPermission(Permission permission) {
        String key = key(permission.getName());
        if (permissions.putIfAbsent(key, permission) != null) {
            throw new IllegalArgumentException("Permission already registered: " + permission.getName());
        }
        recalculateAll();
    }

    public synchronized void removePermission(Permission permission) { removePermission(permission.getName()); }

    public synchronized void removePermission(String name) {
        permissions.remove(key(name));
        recalculateAll();
    }

    public synchronized Permission getPermission(String name) { return permissions.get(key(name)); }

    public synchronized Set<Permission> getDefaultPermissions(boolean op) {
        LinkedHashSet<Permission> result = new LinkedHashSet<>();
        for (Permission permission : permissions.values()) {
            if (permission.getDefault().getValue(op)) result.add(permission);
        }
        return Collections.unmodifiableSet(result);
    }

    public void recalculatePermissionDefaults(Permission permission) { recalculateAll(); }

    public synchronized void subscribeToPermission(String permission, Permissible permissible) {
        permissionSubscriptions.computeIfAbsent(key(permission), ignored -> ConcurrentHashMap.newKeySet()).add(permissible);
    }

    public synchronized void unsubscribeFromPermission(String permission, Permissible permissible) {
        Set<Permissible> subscriptions = permissionSubscriptions.get(key(permission));
        if (subscriptions != null) subscriptions.remove(permissible);
    }

    public synchronized Set<Permissible> getPermissionSubscriptions(String permission) {
        return Set.copyOf(permissionSubscriptions.getOrDefault(key(permission), Set.of()));
    }

    public void subscribeToDefaultPerms(boolean op, Permissible permissible) { defaultSubscriptions.get(op).add(permissible); }
    public void unsubscribeFromDefaultPerms(boolean op, Permissible permissible) { defaultSubscriptions.get(op).remove(permissible); }
    public Set<Permissible> getDefaultPermSubscriptions(boolean op) { return Set.copyOf(defaultSubscriptions.get(op)); }

    public void registerSubject(AtlasPermissible permissible) { subjects.add(permissible); }
    public void unregisterSubject(AtlasPermissible permissible) { subjects.remove(permissible); }

    public void removeAttachments(Plugin plugin) {
        for (AtlasPermissible subject : Set.copyOf(subjects)) subject.removeAttachments(plugin);
    }

    public int subjectCount() { return subjects.size(); }

    private void recalculateAll() {
        for (AtlasPermissible subject : Set.copyOf(subjects)) subject.recalculatePermissions();
    }

    private static String key(String value) { return Permission.normalize(value).toLowerCase(Locale.ROOT); }
}

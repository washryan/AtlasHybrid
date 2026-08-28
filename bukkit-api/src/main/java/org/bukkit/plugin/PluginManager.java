package org.bukkit.plugin;

import java.util.Set;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;

public interface PluginManager {
    void registerEvents(Listener listener, Plugin plugin);

    void callEvent(Event event);

    Plugin[] getPlugins();

    Plugin getPlugin(String name);

    boolean isPluginEnabled(String name);

    void addPermission(Permission permission);
    void removePermission(Permission permission);
    void removePermission(String name);
    Permission getPermission(String name);
    Set<Permission> getDefaultPermissions(boolean op);
    void recalculatePermissionDefaults(Permission permission);
    void subscribeToPermission(String permission, Permissible permissible);
    void unsubscribeFromPermission(String permission, Permissible permissible);
    Set<Permissible> getPermissionSubscriptions(String permission);
    void subscribeToDefaultPerms(boolean op, Permissible permissible);
    void unsubscribeFromDefaultPerms(boolean op, Permissible permissible);
    Set<Permissible> getDefaultPermSubscriptions(boolean op);
}

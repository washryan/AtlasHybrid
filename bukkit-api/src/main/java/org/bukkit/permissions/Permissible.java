package org.bukkit.permissions;

import java.util.Set;
import org.bukkit.plugin.Plugin;

public interface Permissible extends ServerOperator {
    boolean isPermissionSet(String name);
    boolean isPermissionSet(Permission permission);
    boolean hasPermission(String name);
    boolean hasPermission(Permission permission);
    PermissionAttachment addAttachment(Plugin plugin, String name, boolean value);
    PermissionAttachment addAttachment(Plugin plugin);
    PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks);
    PermissionAttachment addAttachment(Plugin plugin, int ticks);
    void removeAttachment(PermissionAttachment attachment);
    void recalculatePermissions();
    Set<PermissionAttachmentInfo> getEffectivePermissions();
}

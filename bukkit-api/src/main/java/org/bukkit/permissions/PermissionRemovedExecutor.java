package org.bukkit.permissions;

@FunctionalInterface
public interface PermissionRemovedExecutor {
    void attachmentRemoved(PermissionAttachment attachment);
}

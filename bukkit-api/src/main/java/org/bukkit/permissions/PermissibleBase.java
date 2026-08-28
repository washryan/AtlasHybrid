package org.bukkit.permissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public class PermissibleBase implements Permissible {
    private final ServerOperator operator;
    private final Object mutationLock = new Object();
    private final List<PermissionAttachment> attachmentEntries = new ArrayList<>();
    private volatile Map<String, PermissionAttachmentInfo> defaultSnapshot = Map.of();
    private volatile Map<String, PermissionAttachmentInfo> attachmentSnapshot = Map.of();
    private volatile Map<String, PermissionAttachmentInfo> effectiveSnapshot = Map.of();
    private volatile Set<String> subscribedPermissions = Set.of();
    private volatile boolean subscribedDefaultOp;
    private volatile boolean subscribedToDefaults;

    public PermissibleBase(ServerOperator operator) {
        this.operator = operator == null ? new ServerOperator() {
            @Override public boolean isOp() { return false; }
            @Override public void setOp(boolean value) { throw new UnsupportedOperationException("Operator state is immutable"); }
        } : operator;
    }

    @Override public boolean isOp() { return operator.isOp(); }

    @Override
    public void setOp(boolean value) {
        if (operator.isOp() == value) return;
        operator.setOp(value);
        recalculatePermissions();
    }

    @Override public boolean isPermissionSet(String name) { return effectiveSnapshot.containsKey(Permission.normalize(name)); }
    @Override public boolean isPermissionSet(Permission permission) { return isPermissionSet(Objects.requireNonNull(permission, "permission").getName()); }

    @Override
    public boolean hasPermission(String name) {
        String normalized = Permission.normalize(name);
        Boolean attached = attachmentValue(normalized);
        if (attached != null) return attached;
        return coreValue(normalized);
    }

    @Override public boolean hasPermission(Permission permission) { return hasPermission(Objects.requireNonNull(permission, "permission").getName()); }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        PermissionAttachment attachment = addAttachment(plugin);
        attachment.setPermission(name, value);
        return attachment;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        PermissionAttachment attachment = new PermissionAttachment(plugin, this);
        synchronized (mutationLock) {
            attachmentEntries.add(attachment);
        }
        recalculatePermissions();
        return attachment;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        PermissionAttachment attachment = addAttachment(plugin, name, value);
        scheduleRemoval(plugin, attachment, ticks);
        return attachment;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        PermissionAttachment attachment = addAttachment(plugin);
        scheduleRemoval(plugin, attachment, ticks);
        return attachment;
    }

    private static void scheduleRemoval(Plugin plugin, PermissionAttachment attachment, int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks cannot be negative");
        plugin.getServer().getScheduler().runTaskLater(plugin, attachment::remove, ticks);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        Objects.requireNonNull(attachment, "attachment");
        boolean removed;
        synchronized (mutationLock) {
            removed = attachmentEntries.remove(attachment);
        }
        if (!removed) throw new IllegalArgumentException("Attachment is not attached to this permissible");
        recalculatePermissions();
        PermissionRemovedExecutor callback = attachment.getRemovalCallback();
        if (callback != null) callback.attachmentRemoved(attachment);
    }

    public void removeAttachments(Plugin plugin) {
        List<PermissionAttachment> removed = new ArrayList<>();
        synchronized (mutationLock) {
            attachmentEntries.removeIf(attachment -> {
                if (attachment.getPlugin() == plugin) {
                    removed.add(attachment);
                    return true;
                }
                return false;
            });
        }
        if (removed.isEmpty()) return;
        recalculatePermissions();
        for (PermissionAttachment attachment : removed) {
            PermissionRemovedExecutor callback = attachment.getRemovalCallback();
            if (callback != null) callback.attachmentRemoved(attachment);
        }
    }

    public void clearPermissions() {
        PluginManager manager = Bukkit.getPluginManager();
        for (String permission : subscribedPermissions) manager.unsubscribeFromPermission(permission, this);
        if (subscribedToDefaults) manager.unsubscribeFromDefaultPerms(subscribedDefaultOp, this);
        subscribedPermissions = Set.of();
        subscribedToDefaults = false;
        defaultSnapshot = Map.of();
        attachmentSnapshot = Map.of();
        effectiveSnapshot = Map.of();
    }

    protected final void closePermissionState() {
        clearPermissions();
        List<PermissionAttachment> removed;
        synchronized (mutationLock) {
            removed = List.copyOf(attachmentEntries);
            attachmentEntries.clear();
        }
        for (PermissionAttachment attachment : removed) {
            PermissionRemovedExecutor callback = attachment.getRemovalCallback();
            if (callback != null) callback.attachmentRemoved(attachment);
        }
    }

    @Override
    public void recalculatePermissions() {
        PluginManager manager = Bukkit.getPluginManager();
        for (String permission : subscribedPermissions) manager.unsubscribeFromPermission(permission, this);
        if (subscribedToDefaults) manager.unsubscribeFromDefaultPerms(subscribedDefaultOp, this);
        Map<String, PermissionAttachmentInfo> defaults = new LinkedHashMap<>();
        Map<String, PermissionAttachmentInfo> attached = new LinkedHashMap<>();
        for (Permission permission : manager.getDefaultPermissions(isOp())) {
            applyPermission(defaults, permission.getName(), true, null, manager, new LinkedHashSet<>());
        }
        List<PermissionAttachment> entries;
        synchronized (mutationLock) {
            entries = List.copyOf(attachmentEntries);
        }
        for (PermissionAttachment attachment : entries) {
            for (Map.Entry<String, Boolean> entry : attachment.snapshotPermissions().entrySet()) {
                applyPermission(attached, entry.getKey(), entry.getValue(), attachment, manager, new LinkedHashSet<>());
            }
        }
        Map<String, PermissionAttachmentInfo> effective = new LinkedHashMap<>(defaults);
        effective.putAll(attached);
        defaultSnapshot = Collections.unmodifiableMap(defaults);
        attachmentSnapshot = Collections.unmodifiableMap(attached);
        effectiveSnapshot = Collections.unmodifiableMap(effective);
        subscribedPermissions = Set.copyOf(effective.keySet());
        subscribedDefaultOp = isOp();
        subscribedToDefaults = true;
        manager.subscribeToDefaultPerms(subscribedDefaultOp, this);
        for (String permission : subscribedPermissions) manager.subscribeToPermission(permission, this);
    }

    private void applyPermission(
        Map<String, PermissionAttachmentInfo> target,
        String name,
        boolean value,
        PermissionAttachment attachment,
        PluginManager manager,
        Set<String> path
    ) {
        String normalized = Permission.normalize(name);
        target.put(normalized, new PermissionAttachmentInfo(this, normalized, attachment, value));
        if (!path.add(normalized)) return;
        Permission permission = manager.getPermission(normalized);
        if (permission != null) {
            for (Map.Entry<String, Boolean> child : permission.getChildren().entrySet()) {
                applyPermission(target, child.getKey(), value == child.getValue(), attachment, manager, path);
            }
        }
        path.remove(normalized);
    }

    protected final Boolean attachmentValue(String normalizedName) {
        PermissionAttachmentInfo info = attachmentSnapshot.get(normalizedName);
        return info == null ? null : info.getValue();
    }

    protected final boolean coreValue(String normalizedName) {
        PermissionAttachmentInfo info = defaultSnapshot.get(normalizedName);
        if (info != null) return info.getValue();
        Permission permission = Bukkit.getPluginManager().getPermission(normalizedName);
        PermissionDefault defaultValue = permission == null ? Permission.DEFAULT_PERMISSION : permission.getDefault();
        return defaultValue.getValue(isOp());
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(effectiveSnapshot.values()));
    }
}

package dev.atlashybrid.forge;

import dev.atlashybrid.runtime.permission.AtlasPermissible;
import dev.atlashybrid.runtime.permission.AtlasPermissionRegistry;
import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.permission.PermissionSubject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.Plugin;

import java.util.Set;

final class ForgeConsoleCommandSender implements ConsoleCommandSender, AutoCloseable {
    private final MinecraftServer server;
    private final AtlasPermissible permissions;

    ForgeConsoleCommandSender(MinecraftServer server, AtlasPermissionRegistry registry, PermissionProviderRegistry providers) {
        this.server = server;
        ServerOperator operator = new ServerOperator() {
            @Override public boolean isOp() { return true; }
            @Override public void setOp(boolean value) {
                if (!value) throw new UnsupportedOperationException("The dedicated server console cannot be de-opped");
            }
        };
        permissions = new AtlasPermissible(operator, registry, providers,
            () -> new PermissionSubject(getName(), null, PermissionSubject.Type.CONSOLE, true));
    }

    void initializePermissions() { permissions.recalculatePermissions(); }
    @Override public String getName() { return "CONSOLE"; }
    @Override public void sendMessage(String message) { server.sendSystemMessage(Component.literal(message)); }
    @Override public boolean isOp() { return permissions.isOp(); }
    @Override public void setOp(boolean value) { permissions.setOp(value); }
    @Override public boolean isPermissionSet(String name) { return permissions.isPermissionSet(name); }
    @Override public boolean isPermissionSet(Permission permission) { return permissions.isPermissionSet(permission); }
    @Override public boolean hasPermission(String name) { return permissions.hasPermission(name); }
    @Override public boolean hasPermission(Permission permission) { return permissions.hasPermission(permission); }
    @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return permissions.addAttachment(plugin, name, value); }
    @Override public PermissionAttachment addAttachment(Plugin plugin) { return permissions.addAttachment(plugin); }
    @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return permissions.addAttachment(plugin, name, value, ticks); }
    @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return permissions.addAttachment(plugin, ticks); }
    @Override public void removeAttachment(PermissionAttachment attachment) { permissions.removeAttachment(attachment); }
    @Override public void recalculatePermissions() { permissions.recalculatePermissions(); }
    @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return permissions.getEffectivePermissions(); }
    @Override public void close() { permissions.close(); }
}

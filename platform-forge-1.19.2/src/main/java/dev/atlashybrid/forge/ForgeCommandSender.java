package dev.atlashybrid.forge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import java.util.Set;

class ForgeCommandSender implements CommandSender {
    private final CommandSourceStack source;
    private final CommandSender permissions;

    ForgeCommandSender(CommandSourceStack source, CommandSender permissions) {
        this.source = source;
        this.permissions = permissions;
    }

    @Override public String getName() { return source.getTextName(); }
    @Override public void sendMessage(String message) { source.sendSuccess(Component.literal(message), false); }
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

    static CommandSender of(CommandSourceStack source) {
        ForgeServerAdapter server = (ForgeServerAdapter) org.bukkit.Bukkit.getServer();
        if (source.getEntity() instanceof ServerPlayer player) return server.player(player);
        if (source.source instanceof net.minecraft.server.rcon.RconConsoleSource) {
            return new ForgeRemoteConsoleCommandSender(source, server.getConsoleSender());
        }
        if (source.source == source.getServer()) return server.getConsoleSender();
        return new ForgeCommandSender(source, server.getConsoleSender());
    }
}

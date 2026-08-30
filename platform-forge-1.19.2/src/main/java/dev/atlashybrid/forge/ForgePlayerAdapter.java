package dev.atlashybrid.forge;

import dev.atlashybrid.diagnostics.CompatibilityRuntime;
import dev.atlashybrid.diagnostics.CompatibilityStatus;
import dev.atlashybrid.runtime.permission.AtlasPermissible;
import dev.atlashybrid.runtime.permission.AtlasPermissionRegistry;
import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.permission.PermissionSubject;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.Plugin;

final class ForgePlayerAdapter implements Player, AutoCloseable {
    private final ServerPlayer player;
    private final AtlasPermissible permissions;
    private volatile GameMode gameModeSnapshot;

    ForgePlayerAdapter(ServerPlayer player, AtlasPermissionRegistry registry, PermissionProviderRegistry providers) {
        this.player = player;
        this.gameModeSnapshot = ForgeGameModeMapper.toBukkit(player.gameMode.getGameModeForPlayer());
        ServerOperator operator = new ServerOperator() {
            @Override public boolean isOp() { return player.hasPermissions(2); }
            @Override public void setOp(boolean value) {
                if (value) player.server.getPlayerList().op(player.getGameProfile());
                else player.server.getPlayerList().deop(player.getGameProfile());
            }
        };
        permissions = new AtlasPermissible(operator, registry, providers,
            () -> new PermissionSubject(getName(), getUniqueId(), PermissionSubject.Type.PLAYER, isOp()));
    }

    void initializePermissions() { permissions.recalculatePermissions(); }
    net.minecraft.commands.CommandSourceStack commandSource() { return player.createCommandSourceStack(); }

    @Override public UUID getUniqueId() { return player.getUUID(); }
    @Override public GameMode getGameMode() { return gameModeSnapshot; }
    @Override public int getEntityId() { return player.getId(); }
    @Override public World getWorld() {
        return ((ForgeServerAdapter) org.bukkit.Bukkit.getServer()).world(player.getLevel());
    }
    @Override public String getDisplayName() { throw CompatibilityRuntime.unsupported("org.bukkit.entity.Player#getDisplayName", "bukkit-player", CompatibilityStatus.NOT_IMPLEMENTED); }
    @Override public Location getLocation() {
        return new Location(getWorld(),
            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }
    @Override public boolean teleport(Location location) {
        if (location == null) throw new IllegalArgumentException("location cannot be null");
        if (!(location.getWorld() instanceof ForgeWorldAdapter world)) {
            throw new IllegalArgumentException("Location world is not managed by AtlasHybrid");
        }
        player.teleportTo(world.level(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        player.moveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        return player.getLevel() == world.level()
            && Math.abs(player.getX() - location.getX()) < 0.001D
            && Math.abs(player.getY() - location.getY()) < 0.001D
            && Math.abs(player.getZ() - location.getZ()) < 0.001D;
    }
    @Override public String getName() { return player.getGameProfile().getName(); }
    @Override public void sendMessage(String message) { player.sendSystemMessage(Component.literal(message)); }
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
    void updateGameMode(GameType type) { gameModeSnapshot = ForgeGameModeMapper.toBukkit(type); }
    @Override public void close() { permissions.close(); }
}

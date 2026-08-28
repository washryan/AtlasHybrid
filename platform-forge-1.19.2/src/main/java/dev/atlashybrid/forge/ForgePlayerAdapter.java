package dev.atlashybrid.forge;

import dev.atlashybrid.diagnostics.CompatibilityRuntime;
import dev.atlashybrid.diagnostics.CompatibilityStatus;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class ForgePlayerAdapter implements Player {
    private final ServerPlayer player;

    ForgePlayerAdapter(ServerPlayer player) {
        this.player = player;
    }

    @Override public UUID getUniqueId() { return player.getUUID(); }
    @Override public String getDisplayName() { throw CompatibilityRuntime.unsupported("org.bukkit.entity.Player#getDisplayName", "bukkit-player", CompatibilityStatus.NOT_IMPLEMENTED); }
    @Override public Location getLocation() {
        ForgeServerAdapter server = (ForgeServerAdapter) org.bukkit.Bukkit.getServer();
        return new Location(new ForgeWorldAdapter(player.getLevel(), server.worldName(player.getLevel())),
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
    @Override public boolean isOp() { return player.hasPermissions(2); }
    @Override public boolean hasPermission(String permission) { return isOp(); }
}

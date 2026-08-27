package dev.atlashybrid.forge;

import dev.atlashybrid.diagnostics.CompatibilityRuntime;
import dev.atlashybrid.diagnostics.CompatibilityStatus;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;

final class ForgePlayerAdapter implements Player {
    private final ServerPlayer player;

    ForgePlayerAdapter(ServerPlayer player) {
        this.player = player;
    }

    @Override public UUID getUniqueId() { return player.getUUID(); }
    @Override public String getDisplayName() { throw CompatibilityRuntime.unsupported("org.bukkit.entity.Player#getDisplayName", "bukkit-player", CompatibilityStatus.NOT_IMPLEMENTED); }
    @Override public String getName() { return player.getGameProfile().getName(); }
    @Override public void sendMessage(String message) { player.sendSystemMessage(Component.literal(message)); }
    @Override public boolean isOp() { return player.hasPermissions(2); }
    @Override public boolean hasPermission(String permission) { return isOp(); }
}

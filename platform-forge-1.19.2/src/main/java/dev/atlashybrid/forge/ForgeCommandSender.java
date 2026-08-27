package dev.atlashybrid.forge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.command.CommandSender;

final class ForgeCommandSender implements CommandSender {
    private final CommandSourceStack source;

    ForgeCommandSender(CommandSourceStack source) {
        this.source = source;
    }

    @Override public String getName() { return source.getTextName(); }
    @Override public void sendMessage(String message) { source.sendSuccess(Component.literal(message), false); }
    @Override public boolean isOp() { return source.hasPermission(2); }
    @Override public boolean hasPermission(String permission) { return source.getEntity() == null || source.hasPermission(2); }

    static CommandSender of(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? new ForgePlayerAdapter(player) : new ForgeCommandSender(source);
    }
}

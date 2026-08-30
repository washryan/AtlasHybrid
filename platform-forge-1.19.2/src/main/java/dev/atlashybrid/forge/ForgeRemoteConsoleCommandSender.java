package dev.atlashybrid.forge;

import net.minecraft.commands.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;

/** Bukkit sender backed by the active vanilla RCON command source. */
final class ForgeRemoteConsoleCommandSender extends ForgeCommandSender implements RemoteConsoleCommandSender {
    ForgeRemoteConsoleCommandSender(CommandSourceStack source, CommandSender permissions) {
        super(source, permissions);
    }
}

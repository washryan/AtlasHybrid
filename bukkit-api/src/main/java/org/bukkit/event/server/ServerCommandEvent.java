package org.bukkit.event.server;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** Called before a command from a non-player server sender is executed. */
public class ServerCommandEvent extends ServerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private String command;
    private final CommandSender sender;
    private boolean cancelled;

    public ServerCommandEvent(CommandSender sender, String command) {
        this.command = command;
        this.sender = sender;
    }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public CommandSender getSender() { return sender; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}

package org.bukkit.event.player;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** Called early when a player attempts to execute a command. */
public class PlayerCommandPreprocessEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private String message;
    private final Set<Player> recipients;

    public PlayerCommandPreprocessEvent(Player player, String message) {
        this(player, message, new HashSet<Player>(player.getServer().getOnlinePlayers()));
    }

    public PlayerCommandPreprocessEvent(Player player, String message, Set<Player> recipients) {
        super(player);
        this.recipients = recipients;
        this.message = message;
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public String getMessage() { return message; }
    public void setMessage(String command) {
        if (command == null) throw new IllegalArgumentException("Command cannot be null");
        if (command.isEmpty()) throw new IllegalArgumentException("Command cannot be empty");
        this.message = command;
    }
    public void setPlayer(Player player) {
        if (player == null) throw new IllegalArgumentException("Player cannot be null");
        this.player = player;
    }
    @Deprecated public Set<Player> getRecipients() { return recipients; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

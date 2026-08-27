package org.bukkit.event.player;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class PlayerJoinEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerJoinEvent(Player player) { super(player); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

package org.bukkit.event.block;

import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BlockBreakEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Block block;
    private final Player player;
    private boolean cancelled;

    public BlockBreakEvent(Block block, Player player) {
        this.block = Objects.requireNonNull(block, "block");
        this.player = Objects.requireNonNull(player, "player");
    }

    public Block getBlock() { return block; }
    public Player getPlayer() { return player; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

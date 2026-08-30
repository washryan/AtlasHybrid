package org.bukkit.event.player;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public abstract class PlayerEvent extends Event {
    protected Player player;

    protected PlayerEvent(Player player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    public final Player getPlayer() {
        return player;
    }
}

package org.bukkit.entity;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

public interface Player extends CommandSender {
    UUID getUniqueId();

    String getDisplayName();

    Location getLocation();

    boolean teleport(Location location);
}

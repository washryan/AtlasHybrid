package org.bukkit.entity;

import java.util.UUID;
import org.bukkit.Location;

public interface Player extends HumanEntity {
    UUID getUniqueId();

    String getDisplayName();

    Location getLocation();

    boolean teleport(Location location);
}

package org.bukkit.entity;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

/**
 * Minimal, coherent foundation for a Bukkit entity backed by a live runtime entity.
 * Unsupported portions of the upstream Entity contract are intentionally deferred.
 */
public interface Entity extends CommandSender {
    UUID getUniqueId();

    int getEntityId();

    World getWorld();

    Location getLocation();

    boolean teleport(Location location);
}

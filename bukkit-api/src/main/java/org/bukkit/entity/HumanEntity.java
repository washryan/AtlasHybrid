package org.bukkit.entity;

import org.bukkit.GameMode;

/** Public Bukkit entity hierarchy boundary; inventory and human-specific behavior are deferred. */
public interface HumanEntity extends LivingEntity {
    GameMode getGameMode();
}

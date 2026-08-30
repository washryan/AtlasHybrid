package org.bukkit.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntityHierarchyTest {
    @Test
    void exposesTheBukkitPlayerEntityHierarchy() {
        assertTrue(Entity.class.isAssignableFrom(LivingEntity.class));
        assertTrue(LivingEntity.class.isAssignableFrom(HumanEntity.class));
        assertTrue(HumanEntity.class.isAssignableFrom(Player.class));
        assertTrue(Entity.class.isAssignableFrom(Player.class));
    }
}

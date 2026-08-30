package org.bukkit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WorldEnvironmentTest {
    @Test
    @SuppressWarnings("deprecation")
    void exposesExactSpigot1192ConstantsAndLegacyLookup() {
        assertArrayEquals(new World.Environment[] {
            World.Environment.NORMAL,
            World.Environment.NETHER,
            World.Environment.THE_END,
            World.Environment.CUSTOM
        }, World.Environment.values());
        assertEquals(0, World.Environment.NORMAL.getId());
        assertEquals(-1, World.Environment.NETHER.getId());
        assertEquals(1, World.Environment.THE_END.getId());
        assertEquals(-999, World.Environment.CUSTOM.getId());
        assertEquals(World.Environment.NORMAL, World.Environment.getEnvironment(0));
        assertEquals(World.Environment.NETHER, World.Environment.getEnvironment(-1));
        assertEquals(World.Environment.THE_END, World.Environment.getEnvironment(1));
        assertEquals(World.Environment.CUSTOM, World.Environment.getEnvironment(-999));
        assertNull(World.Environment.getEnvironment(2));
    }
}

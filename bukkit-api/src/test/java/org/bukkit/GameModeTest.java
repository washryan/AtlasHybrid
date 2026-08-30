package org.bukkit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GameModeTest {
    @Test
    @SuppressWarnings("deprecation")
    void exposesExactSpigot1192ConstantsAndLegacyValues() {
        assertArrayEquals(
            new GameMode[] {GameMode.CREATIVE, GameMode.SURVIVAL, GameMode.ADVENTURE, GameMode.SPECTATOR},
            GameMode.values());
        assertEquals(1, GameMode.CREATIVE.getValue());
        assertEquals(0, GameMode.SURVIVAL.getValue());
        assertEquals(2, GameMode.ADVENTURE.getValue());
        assertEquals(3, GameMode.SPECTATOR.getValue());
        assertEquals(GameMode.SURVIVAL, GameMode.getByValue(0));
        assertEquals(GameMode.CREATIVE, GameMode.getByValue(1));
        assertEquals(GameMode.ADVENTURE, GameMode.getByValue(2));
        assertEquals(GameMode.SPECTATOR, GameMode.getByValue(3));
        assertNull(GameMode.getByValue(-1));
        assertNull(GameMode.getByValue(4));
    }
}

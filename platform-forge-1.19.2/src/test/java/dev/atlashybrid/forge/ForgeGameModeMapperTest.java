package dev.atlashybrid.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.world.level.GameType;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class ForgeGameModeMapperTest {
    @Test
    void mapsEveryMinecraftGameTypeExplicitlyWithoutOrdinalCoupling() {
        assertEquals(GameMode.SURVIVAL, ForgeGameModeMapper.toBukkit(GameType.SURVIVAL));
        assertEquals(GameMode.CREATIVE, ForgeGameModeMapper.toBukkit(GameType.CREATIVE));
        assertEquals(GameMode.ADVENTURE, ForgeGameModeMapper.toBukkit(GameType.ADVENTURE));
        assertEquals(GameMode.SPECTATOR, ForgeGameModeMapper.toBukkit(GameType.SPECTATOR));
        assertEquals(GameType.SURVIVAL, ForgeGameModeMapper.toMinecraft(GameMode.SURVIVAL));
        assertEquals(GameType.CREATIVE, ForgeGameModeMapper.toMinecraft(GameMode.CREATIVE));
        assertEquals(GameType.ADVENTURE, ForgeGameModeMapper.toMinecraft(GameMode.ADVENTURE));
        assertEquals(GameType.SPECTATOR, ForgeGameModeMapper.toMinecraft(GameMode.SPECTATOR));
        assertEquals(0, GameType.SURVIVAL.getId());
        assertEquals(1, GameType.CREATIVE.getId());
        assertThrows(NullPointerException.class, () -> ForgeGameModeMapper.toBukkit(null));
        assertThrows(NullPointerException.class, () -> ForgeGameModeMapper.toMinecraft(null));
    }
}

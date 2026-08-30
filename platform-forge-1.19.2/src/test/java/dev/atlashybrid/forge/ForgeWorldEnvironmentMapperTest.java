package dev.atlashybrid.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.resources.ResourceLocation;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class ForgeWorldEnvironmentMapperTest {
    @Test
    void mapsVanillaKeysAndKeepsModdedDimensionsCustom() {
        assertEquals(World.Environment.NORMAL,
            ForgeWorldEnvironmentMapper.toBukkit(ResourceLocation.tryParse("minecraft:overworld")));
        assertEquals(World.Environment.NETHER,
            ForgeWorldEnvironmentMapper.toBukkit(ResourceLocation.tryParse("minecraft:the_nether")));
        assertEquals(World.Environment.THE_END,
            ForgeWorldEnvironmentMapper.toBukkit(ResourceLocation.tryParse("minecraft:the_end")));
        assertEquals(World.Environment.CUSTOM,
            ForgeWorldEnvironmentMapper.toBukkit(ResourceLocation.tryParse("atlashybrid_test:moon")));
        assertThrows(NullPointerException.class, () -> ForgeWorldEnvironmentMapper.toBukkit(null));
    }
}

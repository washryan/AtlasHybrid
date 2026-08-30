package dev.atlashybrid.forge;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.World;

final class ForgeWorldEnvironmentMapper {
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation NETHER = ResourceLocation.tryParse("minecraft:the_nether");
    private static final ResourceLocation END = ResourceLocation.tryParse("minecraft:the_end");

    private ForgeWorldEnvironmentMapper() {
    }

    static World.Environment toBukkit(ResourceLocation dimension) {
        Objects.requireNonNull(dimension, "dimension");
        if (OVERWORLD.equals(dimension)) return World.Environment.NORMAL;
        if (NETHER.equals(dimension)) return World.Environment.NETHER;
        if (END.equals(dimension)) return World.Environment.THE_END;
        return World.Environment.CUSTOM;
    }
}

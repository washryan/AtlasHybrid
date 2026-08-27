package dev.atlashybrid.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;
import org.bukkit.block.Block;

final class ForgeBlockAdapter implements Block {
    private final LevelAccessor level;
    private final BlockPos position;

    ForgeBlockAdapter(LevelAccessor level, BlockPos position) {
        this.level = level;
        this.position = position.immutable();
    }

    @Override public int getX() { return position.getX(); }
    @Override public int getY() { return position.getY(); }
    @Override public int getZ() { return position.getZ(); }
    @Override public String getType() {
        var key = ForgeRegistries.BLOCKS.getKey(level.getBlockState(position).getBlock());
        return key == null ? "minecraft:air" : key.toString();
    }
}

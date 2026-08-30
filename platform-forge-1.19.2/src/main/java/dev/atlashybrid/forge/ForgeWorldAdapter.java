package dev.atlashybrid.forge;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.World;

final class ForgeWorldAdapter implements World {
    private final ServerLevel level;
    private final String name;
    private final Environment environment;

    ForgeWorldAdapter(ServerLevel level, String name) {
        this.level = Objects.requireNonNull(level, "level");
        this.name = Objects.requireNonNull(name, "name");
        this.environment = ForgeWorldEnvironmentMapper.toBukkit(level.dimension().location());
    }

    ServerLevel level() {
        return level;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Environment getEnvironment() {
        return environment;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ForgeWorldAdapter world && level == world.level;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(level);
    }
}

package dev.atlashybrid.forge;

import java.util.Objects;
import net.minecraft.world.level.GameType;
import org.bukkit.GameMode;

final class ForgeGameModeMapper {
    private ForgeGameModeMapper() {
    }

    static GameMode toBukkit(GameType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case SURVIVAL -> GameMode.SURVIVAL;
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
        };
    }
}

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

    static GameType toMinecraft(GameMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case SURVIVAL -> GameType.SURVIVAL;
            case CREATIVE -> GameType.CREATIVE;
            case ADVENTURE -> GameType.ADVENTURE;
            case SPECTATOR -> GameType.SPECTATOR;
        };
    }
}

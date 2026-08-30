package org.bukkit;

/** Vanilla game modes exposed by the Bukkit 1.19.2 contract. */
public enum GameMode {
    CREATIVE(1),
    SURVIVAL(0),
    ADVENTURE(2),
    SPECTATOR(3);

    private final int value;

    GameMode(int value) {
        this.value = value;
    }

    /** @deprecated legacy numeric game-mode value */
    @Deprecated
    public int getValue() {
        return value;
    }

    /** @deprecated use enum constants; returns null for an unknown value */
    @Deprecated
    public static GameMode getByValue(int value) {
        return switch (value) {
            case 0 -> SURVIVAL;
            case 1 -> CREATIVE;
            case 2 -> ADVENTURE;
            case 3 -> SPECTATOR;
            default -> null;
        };
    }
}

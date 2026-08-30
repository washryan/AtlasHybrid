package org.bukkit;

public interface World {
    /** Bukkit 1.19.2 world environment categories. */
    enum Environment {
        NORMAL(0),
        NETHER(-1),
        THE_END(1),
        CUSTOM(-999);

        private final int id;

        Environment(int id) {
            this.id = id;
        }

        /** @deprecated legacy dimension identifier */
        @Deprecated
        public int getId() {
            return id;
        }

        /** @deprecated legacy lookup; returns null for an unknown identifier */
        @Deprecated
        public static Environment getEnvironment(int id) {
            return switch (id) {
                case 0 -> NORMAL;
                case -1 -> NETHER;
                case 1 -> THE_END;
                case -999 -> CUSTOM;
                default -> null;
            };
        }
    }

    String getName();

    Environment getEnvironment();
}

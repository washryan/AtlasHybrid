package org.bukkit.permissions;

import java.util.Locale;

public enum PermissionDefault {
    TRUE("true"),
    FALSE("false"),
    OP("op"),
    NOT_OP("not op");

    private final String name;

    PermissionDefault(String name) {
        this.name = name;
    }

    public boolean getValue(boolean op) {
        return switch (this) {
            case TRUE -> true;
            case FALSE -> false;
            case OP -> op;
            case NOT_OP -> !op;
        };
    }

    @Override
    public String toString() {
        return name;
    }

    public static PermissionDefault getByName(String value) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ').strip();
        for (PermissionDefault candidate : values()) {
            if (candidate.name.equals(normalized)) return candidate;
        }
        return null;
    }
}

package dev.atlashybrid.forge.compat.luckperms;

public enum LuckPermsBridgeState {
    ABSENT,
    DISCOVERED,
    PERMISSION_PROVIDER_BOUND,
    BUKKIT_SERVICE_REGISTERED,
    VIRTUAL_DEPENDENCY_AVAILABLE,
    SERVICE_UNREGISTERED,
    FAILED
}

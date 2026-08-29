package dev.atlashybrid.runtime;

import dev.atlashybrid.diagnostics.CompatibilityRuntime;
import dev.atlashybrid.diagnostics.CompatibilityStatus;
import org.bukkit.Material;
import org.bukkit.UnsafeValues;

/** Immutable implementation of AtlasHybrid's audited UnsafeValues subset. */
@SuppressWarnings("deprecation")
public final class AtlasUnsafeValues implements UnsafeValues {
    /**
     * Mojang server 1.19.2 version.json field {@code world_version}.
     * This is deliberately not the protocol version (760) or a pack format.
     */
    public static final int MINECRAFT_1_19_2_DATA_VERSION = 3120;

    private final int dataVersion;

    public AtlasUnsafeValues(int dataVersion) {
        if (dataVersion <= 0) throw new IllegalArgumentException("dataVersion must be positive");
        this.dataVersion = dataVersion;
    }

    @Override
    public int getDataVersion() {
        return dataVersion;
    }

    @Override
    public Material toLegacy(Material material) {
        throw unsupported("UnsafeValues#toLegacy(Material)");
    }

    @Override
    public Material fromLegacy(Material material) {
        throw unsupported("UnsafeValues#fromLegacy(Material)");
    }

    @Override
    public Material getMaterial(String material, int version) {
        throw unsupported("UnsafeValues#getMaterial(String,int)");
    }

    private static UnsupportedOperationException unsupported(String api) {
        return CompatibilityRuntime.unsupported(api, "bukkit-unsafe-values", CompatibilityStatus.NOT_IMPLEMENTED);
    }
}

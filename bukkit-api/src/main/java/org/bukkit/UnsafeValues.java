package org.bukkit;

/**
 * Version-sensitive Bukkit services whose contracts are not stable API.
 *
 * <p>AtlasHybrid intentionally exposes only the audited, public subset that it
 * can implement without CraftBukkit or Minecraft internals.</p>
 */
@Deprecated
public interface UnsafeValues {
    Material toLegacy(Material material);

    Material fromLegacy(Material material);

    Material getMaterial(String material, int version);

    int getDataVersion();
}

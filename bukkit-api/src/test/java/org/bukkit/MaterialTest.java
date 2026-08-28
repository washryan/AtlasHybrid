package org.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class MaterialTest {
    @Test
    void exposesRequiredEnumConstantsAndJvmEnumOperations() {
        assertSame(Material.STONE, Material.valueOf("STONE"));
        assertSame(Material.AIR, Material.valueOf("AIR"));
        assertSame(Material.DIRT, Material.valueOf("DIRT"));
        assertSame(Material.BLUE_ICE, Material.valueOf("BLUE_ICE"));
        assertSame(Material.NETHERITE_PICKAXE, Material.valueOf("NETHERITE_PICKAXE"));
        assertEquals(1281, Material.values().length);
    }

    @Test
    void exactAndFriendlyLookupFollowBukkitCaseAndNamespaceRules() {
        assertSame(Material.STONE, Material.getMaterial("STONE"));
        assertNull(Material.getMaterial("stone"));
        assertSame(Material.STONE, Material.matchMaterial("STONE"));
        assertSame(Material.STONE, Material.matchMaterial("stone"));
        assertSame(Material.STONE, Material.matchMaterial("minecraft:stone"));
        assertSame(Material.NETHERITE_PICKAXE, Material.matchMaterial("netherite pickaxe"));
        assertNull(Material.matchMaterial("Minecraft:stone"));
        assertNull(Material.matchMaterial("atlas:stone"));
        assertNull(Material.matchMaterial("not_a_material"));
        assertThrows(IllegalArgumentException.class, () -> Material.matchMaterial(null));
    }

    @Test
    void registryFlagsRepresentVanillaBlocksItemsAndAir() {
        assertTrue(Material.STONE.isBlock());
        assertTrue(Material.STONE.isItem());
        assertTrue(Material.DIRT.isBlock());
        assertTrue(Material.DIRT.isItem());
        assertFalse(Material.NETHERITE_PICKAXE.isBlock());
        assertTrue(Material.NETHERITE_PICKAXE.isItem());
        assertTrue(Material.AIR.isBlock());
        assertTrue(Material.AIR.isItem());
        assertTrue(Material.AIR.isAir());
        assertTrue(Material.CAVE_AIR.isBlock());
        assertFalse(Material.CAVE_AIR.isItem());
        assertTrue(Material.CAVE_AIR.isAir());
        assertFalse(Material.STONE.isAir());
    }

    @Test
    void keysAndNamespacedKeyValueSemanticsAreStable() {
        NamespacedKey stone = new NamespacedKey("minecraft", "stone");
        assertEquals(stone, Material.STONE.getKey());
        assertEquals(stone.hashCode(), Material.STONE.getKey().hashCode());
        assertEquals("minecraft:stone", stone.toString());
        assertEquals("minecraft", stone.getNamespace());
        assertEquals("stone", stone.getKey());
        assertEquals(stone, NamespacedKey.minecraft("stone"));
        assertEquals(stone, NamespacedKey.fromString("stone"));
        assertEquals(stone, NamespacedKey.fromString("minecraft:stone"));
        assertNull(NamespacedKey.fromString("Minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> new NamespacedKey("Minecraft", "stone"));
    }

    @Test
    void catalogIsUniqueAndDeterministic() throws Exception {
        Material[] catalog = Material.values();
        HashSet<NamespacedKey> keys = new HashSet<>();
        StringBuilder fingerprint = new StringBuilder();
        for (Material material : catalog) {
            assertTrue(keys.add(material.getKey()));
            fingerprint.append(material.name()).append('|')
                .append(material.isBlock()).append('|')
                .append(material.isItem()).append('|')
                .append(material.isAir()).append('\n');
        }
        String hash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(fingerprint.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals("8E0DE363C35314708DED5C8CB8FA3FBC11D377AC275C95BB0AE8BE6BB7EA7EB1", hash);
    }
}

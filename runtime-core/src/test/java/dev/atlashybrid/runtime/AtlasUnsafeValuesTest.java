package dev.atlashybrid.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class AtlasUnsafeValuesTest {
    @AfterEach
    void clearBukkitServer() {
        Bukkit.setServer(null);
    }

    @Test
    void bukkitAndServerExposeTheSameDeterministicDataVersion() {
        UnsafeValues values = new AtlasUnsafeValues(AtlasUnsafeValues.MINECRAFT_1_19_2_DATA_VERSION);
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[] { Server.class },
            (proxy, method, arguments) -> method.getName().equals("getUnsafe") ? values : primitiveDefault(method.getReturnType()));
        Bukkit.setServer(server);

        assertNotNull(Bukkit.getUnsafe());
        assertNotNull(Bukkit.getServer().getUnsafe());
        assertSame(Bukkit.getServer().getUnsafe(), Bukkit.getUnsafe());
        assertEquals(3120, Bukkit.getUnsafe().getDataVersion());
        assertEquals(3120, Bukkit.getUnsafe().getDataVersion());
    }

    @Test
    void unsupportedConversionsFailExplicitly() {
        UnsafeValues values = new AtlasUnsafeValues(3120);
        UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
            () -> values.toLegacy(Material.STONE));
        assertTrue(failure.getMessage().contains("UnsafeValues#toLegacy(Material)"));
        assertTrue(failure.getMessage().contains("NOT_IMPLEMENTED"));
    }

    @Test
    void implementationDoesNotLinkMinecraftOrCraftBukkitInternals() throws IOException {
        byte[] bytes;
        try (var stream = AtlasUnsafeValues.class.getResourceAsStream("AtlasUnsafeValues.class")) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        String constantPool = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(!constantPool.contains("net/minecraft"));
        assertTrue(!constantPool.contains("org/bukkit/craftbukkit"));
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}

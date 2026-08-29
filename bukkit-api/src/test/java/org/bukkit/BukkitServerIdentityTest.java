package org.bukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BukkitServerIdentityTest {
    @AfterEach
    void clearServer() {
        Bukkit.setServer(null);
    }

    @Test
    void delegatesOnlineModeTrueToServer() {
        Bukkit.setServer(serverWithOnlineMode(true));
        assertTrue(Bukkit.getServer().getOnlineMode());
        assertTrue(Bukkit.getOnlineMode());
    }

    @Test
    void delegatesOnlineModeFalseToServer() {
        Bukkit.setServer(serverWithOnlineMode(false));
        assertFalse(Bukkit.getServer().getOnlineMode());
        assertFalse(Bukkit.getOnlineMode());
    }

    private static Server serverWithOnlineMode(boolean onlineMode) {
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[] { Server.class },
            (proxy, method, arguments) -> method.getName().equals("getOnlineMode")
                ? onlineMode
                : primitiveDefault(method.getReturnType()));
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

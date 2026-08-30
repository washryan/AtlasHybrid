package org.bukkit.event.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

class PlayerCommandPreprocessEventTest {
    @Test
    void exposesBukkit1192ConstructionAndMutationContract() {
        Player original = player("original");
        Player replacement = player("replacement");
        Set<Player> recipients = new HashSet<>(Set.of(original));
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(original, "/atlas original", recipients);
        assertSame(original, event.getPlayer());
        assertEquals("/atlas original", event.getMessage());
        assertSame(recipients, event.getRecipients());
        assertFalse(event.isCancelled());
        assertFalse(event.isAsynchronous());
        event.setMessage("/atlas changed");
        event.setPlayer(replacement);
        event.setCancelled(true);
        assertEquals("/atlas changed", event.getMessage());
        assertSame(replacement, event.getPlayer());
        assertTrue(event.isCancelled());
    }

    @Test
    void rejectsNullOrEmptyMutationAndNullPlayer() {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(
            player("player"), "/atlas", new HashSet<>());
        assertThrows(IllegalArgumentException.class, () -> event.setMessage(null));
        assertThrows(IllegalArgumentException.class, () -> event.setMessage(""));
        assertThrows(IllegalArgumentException.class, () -> event.setPlayer(null));
    }

    @Test
    void ownsTheStaticHandlerList() {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(
            player("player"), "/atlas", new HashSet<>());
        assertSame(PlayerCommandPreprocessEvent.getHandlerList(), event.getHandlers());
        assertSame(HandlerList.class, event.getHandlers().getClass());
    }

    private static Player player(String name) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] { Player.class },
            (proxy, method, arguments) -> {
                if (method.getName().equals("getName")) return name;
                if (method.getReturnType().isPrimitive()) return primitiveDefault(method.getReturnType());
                return null;
            });
    }

    private static Object primitiveDefault(Class<?> type) {
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

package org.bukkit.event.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

class PlayerChangedWorldEventTest {
    @Test
    void exposesBukkit1192ConstructionContract() {
        Player player = proxy(Player.class);
        World from = proxy(World.class);
        PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(player, from);

        assertSame(player, event.getPlayer());
        assertSame(from, event.getFrom());
        assertFalse(event.isAsynchronous());
        assertFalse(event instanceof Cancellable);
    }

    @Test
    void exposesOneStaticHandlerList() {
        PlayerChangedWorldEvent first = new PlayerChangedWorldEvent(proxy(Player.class), proxy(World.class));
        PlayerChangedWorldEvent second = new PlayerChangedWorldEvent(proxy(Player.class), proxy(World.class));
        assertSame(PlayerChangedWorldEvent.getHandlerList(), first.getHandlers());
        assertSame(first.getHandlers(), second.getHandlers());
        assertSame(HandlerList.class, first.getHandlers().getClass());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (instance, method, arguments) -> primitiveDefault(method.getReturnType()));
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

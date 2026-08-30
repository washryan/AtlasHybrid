package org.bukkit.event.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

class PlayerGameModeChangeEventTest {
    @Test
    void exposesBukkit1192ConstructionContract() {
        Player player = player();
        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(player, GameMode.CREATIVE);
        assertSame(player, event.getPlayer());
        assertSame(GameMode.CREATIVE, event.getNewGameMode());
        assertFalse(event.isAsynchronous());
        assertTrue(event instanceof Cancellable);
        assertFalse(event.isCancelled());
    }

    @Test
    void supportsAllowAndDenyWithoutChangingTheTargetMode() {
        PlayerGameModeChangeEvent event = new PlayerGameModeChangeEvent(player(), GameMode.SURVIVAL);
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        assertSame(GameMode.SURVIVAL, event.getNewGameMode());
        event.setCancelled(false);
        assertFalse(event.isCancelled());
    }

    @Test
    void exposesOneStaticHandlerList() {
        PlayerGameModeChangeEvent first = new PlayerGameModeChangeEvent(player(), GameMode.ADVENTURE);
        PlayerGameModeChangeEvent second = new PlayerGameModeChangeEvent(player(), GameMode.SPECTATOR);
        assertSame(PlayerGameModeChangeEvent.getHandlerList(), first.getHandlers());
        assertSame(first.getHandlers(), second.getHandlers());
        assertSame(HandlerList.class, first.getHandlers().getClass());
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, arguments) -> primitiveDefault(method.getReturnType()));
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

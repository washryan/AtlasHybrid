package org.bukkit.event.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.util.Arrays;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

class PlayerLoginEventTest {
    @Test
    void exposesBukkit1192ConstructionContract() throws Exception {
        Player player = player();
        InetAddress address = InetAddress.getByName("127.0.0.1");
        InetAddress realAddress = InetAddress.getByName("127.0.0.2");
        PlayerLoginEvent event = new PlayerLoginEvent(player, "localhost:25565", address, realAddress);
        assertSame(player, event.getPlayer());
        assertEquals("localhost:25565", event.getHostname());
        assertEquals(address, event.getAddress());
        assertEquals(realAddress, event.getRealAddress());
        assertEquals(PlayerLoginEvent.Result.ALLOWED, event.getResult());
        assertEquals("", event.getKickMessage());
        assertFalse(event.isAsynchronous());
    }

    @Test
    void threeArgumentConstructorUsesAddressAsRealAddress() throws Exception {
        InetAddress address = InetAddress.getLoopbackAddress();
        PlayerLoginEvent event = new PlayerLoginEvent(player(), "localhost:25565", address);
        assertEquals(address, event.getAddress());
        assertEquals(address, event.getRealAddress());
    }

    @Test
    void resultMutationAndFullConstructorPreserveAllValues() throws Exception {
        InetAddress address = InetAddress.getLoopbackAddress();
        PlayerLoginEvent event = new PlayerLoginEvent(player(), "host", address,
            PlayerLoginEvent.Result.KICK_FULL, "full", address);
        assertEquals(PlayerLoginEvent.Result.KICK_FULL, event.getResult());
        assertEquals("full", event.getKickMessage());
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "denied");
        assertEquals(PlayerLoginEvent.Result.KICK_OTHER, event.getResult());
        assertEquals("denied", event.getKickMessage());
        event.setResult(PlayerLoginEvent.Result.KICK_BANNED);
        event.setKickMessage("banned");
        assertEquals(PlayerLoginEvent.Result.KICK_BANNED, event.getResult());
        assertEquals("banned", event.getKickMessage());
        event.allow();
        assertEquals(PlayerLoginEvent.Result.ALLOWED, event.getResult());
        assertEquals("", event.getKickMessage());
    }

    @Test
    void declaresAllTargetResultsAndStaticHandlerList() throws Exception {
        assertEquals(Arrays.asList(PlayerLoginEvent.Result.ALLOWED, PlayerLoginEvent.Result.KICK_FULL,
            PlayerLoginEvent.Result.KICK_BANNED, PlayerLoginEvent.Result.KICK_WHITELIST,
            PlayerLoginEvent.Result.KICK_OTHER), Arrays.asList(PlayerLoginEvent.Result.values()));
        PlayerLoginEvent event = new PlayerLoginEvent(player(), "host", InetAddress.getLoopbackAddress());
        assertSame(PlayerLoginEvent.getHandlerList(), event.getHandlers());
        assertSame(HandlerList.class, event.getHandlers().getClass());
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] { Player.class },
            (proxy, method, arguments) -> method.getReturnType().isPrimitive() ? primitiveDefault(method.getReturnType()) : null);
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

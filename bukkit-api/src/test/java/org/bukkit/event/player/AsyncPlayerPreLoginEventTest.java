package org.bukkit.event.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AsyncPlayerPreLoginEventTest {
    private static final UUID ID = UUID.fromString("9f1e5b65-1e8e-3b20-a38d-fb6d51b71a70");

    @Test
    void exposesBukkit1192ConstructionContract() throws Exception {
        InetAddress address = InetAddress.getByName("127.0.0.1");
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent("AtlasPlayer", address, ID);
        assertEquals("AtlasPlayer", event.getName());
        assertEquals(address, event.getAddress());
        assertEquals(ID, event.getUniqueId());
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
        assertEquals("", event.getKickMessage());
        assertTrue(event.isAsynchronous());
    }

    @Test
    void legacyConstructorPreservesNullUniqueId() throws Exception {
        AsyncPlayerPreLoginEvent event =
            new AsyncPlayerPreLoginEvent("LegacyPlayer", InetAddress.getLoopbackAddress());
        assertNull(event.getUniqueId());
    }

    @Test
    void disallowAndAllowUpdateResultAndMessage() throws Exception {
        AsyncPlayerPreLoginEvent event = event();
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "denied");
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());
        assertEquals("denied", event.getKickMessage());
        event.allow();
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
        assertEquals("", event.getKickMessage());
    }

    @Test
    void settersAndLegacyResultMappingMatchTargetApi() throws Exception {
        AsyncPlayerPreLoginEvent event = event();
        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_FULL);
        event.setKickMessage("full");
        assertEquals(PlayerPreLoginEvent.Result.KICK_FULL, event.getResult());
        event.setResult(PlayerPreLoginEvent.Result.KICK_WHITELIST);
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, event.getLoginResult());
        event.disallow(PlayerPreLoginEvent.Result.KICK_BANNED, "banned");
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, event.getLoginResult());
        assertEquals("banned", event.getKickMessage());
    }

    @Test
    void asynchronousClassificationIsStableOffCallingThread() {
        String caller = Thread.currentThread().getName();
        CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {
            try {
                return event().isAsynchronous() && !Thread.currentThread().getName().equals(caller);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
        assertTrue(result.join());
        assertFalse(caller.isBlank());
    }

    private static AsyncPlayerPreLoginEvent event() throws Exception {
        return new AsyncPlayerPreLoginEvent("AtlasPlayer", InetAddress.getLoopbackAddress(), ID);
    }
}

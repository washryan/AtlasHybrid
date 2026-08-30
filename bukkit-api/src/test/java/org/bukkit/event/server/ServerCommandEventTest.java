package org.bukkit.event.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

class ServerCommandEventTest {
    @Test
    void exposesConstructionMutationCancellationAndHandlers() {
        CommandSender sender = sender(CommandSender.class);
        ServerCommandEvent event = new ServerCommandEvent(sender, "say original");
        assertSame(sender, event.getSender());
        assertEquals("say original", event.getCommand());
        assertFalse(event.isCancelled());
        assertFalse(event.isAsynchronous());
        event.setCommand("say changed");
        event.setCancelled(true);
        assertEquals("say changed", event.getCommand());
        assertTrue(event.isCancelled());
        assertSame(ServerCommandEvent.getHandlerList(), event.getHandlers());
    }

    @Test
    void preservesEmptyWhitespaceAndNullWithoutInventingFallback() {
        ServerCommandEvent event = new ServerCommandEvent(sender(CommandSender.class), "");
        assertEquals("", event.getCommand());
        event.setCommand("   ");
        assertEquals("   ", event.getCommand());
        event.setCommand(null);
        assertEquals(null, event.getCommand());
    }

    @Test
    void remoteEventInheritsCommandAndCancellationButOwnsHandlerList() {
        RemoteConsoleCommandSender sender = sender(RemoteConsoleCommandSender.class);
        RemoteServerCommandEvent event = new RemoteServerCommandEvent(sender, "list");
        assertTrue(event instanceof ServerCommandEvent);
        assertSame(sender, event.getSender());
        event.setCommand("say remote");
        event.setCancelled(true);
        assertEquals("say remote", event.getCommand());
        assertTrue(event.isCancelled());
        assertSame(RemoteServerCommandEvent.getHandlerList(), event.getHandlers());
        assertTrue(event.getHandlers() != ServerCommandEvent.getHandlerList());
        assertSame(HandlerList.class, event.getHandlers().getClass());
    }

    @SuppressWarnings("unchecked")
    private static <T> T sender(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type },
            (proxy, method, arguments) -> method.getReturnType() == boolean.class ? false : null);
    }
}

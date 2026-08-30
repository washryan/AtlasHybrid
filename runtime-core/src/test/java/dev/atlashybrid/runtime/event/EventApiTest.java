package dev.atlashybrid.runtime.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.atlashybrid.runtime.permission.AtlasPermissionRegistry;
import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.service.AtlasServicesManager;
import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EventApiTest {
    private final CapturingHandler logs = new CapturingHandler();
    private final AtlasPluginManager manager = manager();
    private final TestPlugin plugin = new TestPlugin();

    @AfterEach
    void cleanup() {
        HandlerList.unregisterAll();
    }

    @Test
    void eventExecutorIsInvokedExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        Listener listener = new Listener() { };
        manager.registerEvent(TestEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> {
                assertEquals(listener, registered);
                assertInstanceOf(TestEvent.class, event);
                calls.incrementAndGet();
            }, plugin);
        manager.callEvent(new TestEvent());
        assertEquals(1, calls.get());
    }

    @Test
    void dispatchUsesBukkitPriorityOrderAndRegistrationOrderWithinSlot() {
        List<String> order = new ArrayList<>();
        register(order, "monitor", EventPriority.MONITOR);
        register(order, "normal-a", EventPriority.NORMAL);
        register(order, "lowest", EventPriority.LOWEST);
        register(order, "highest", EventPriority.HIGHEST);
        register(order, "low", EventPriority.LOW);
        register(order, "normal-b", EventPriority.NORMAL);
        register(order, "high", EventPriority.HIGH);
        manager.callEvent(new TestEvent());
        assertEquals(List.of("lowest", "low", "normal-a", "normal-b", "high", "highest", "monitor"), order);
    }

    @Test
    void ignoreCancelledSkipsOnlyOptedInListener() {
        AtomicInteger ignored = new AtomicInteger();
        AtomicInteger received = new AtomicInteger();
        Listener listener = new Listener() { };
        manager.registerEvent(TestEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> ignored.incrementAndGet(), plugin, true);
        manager.registerEvent(TestEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> received.incrementAndGet(), plugin, false);
        TestEvent event = new TestEvent();
        event.setCancelled(true);
        manager.callEvent(event);
        assertEquals(0, ignored.get());
        assertEquals(1, received.get());
    }

    @Test
    void registeredExecutorRejectsWrongEventTypeClearly() {
        manager.registerEvent(TestEvent.class, new Listener() { }, EventPriority.NORMAL,
            (registered, event) -> { }, plugin);
        RegisteredListener registered = TestEvent.HANDLERS.getRegisteredListeners()[0];
        EventException failure = assertThrows(EventException.class, () -> registered.callEvent(new OtherEvent()));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains(TestEvent.class.getName()));
    }

    @Test
    void executorFailureIsLoggedWithStructuredContextAndDoesNotStopDispatch() {
        AtomicInteger later = new AtomicInteger();
        manager.registerEvent(TestEvent.class, new NamedListener(), EventPriority.NORMAL,
            (registered, event) -> { throw new EventException(new IllegalStateException("boom")); }, plugin);
        manager.registerEvent(TestEvent.class, new Listener() { }, EventPriority.HIGH,
            (registered, event) -> later.incrementAndGet(), plugin);
        manager.callEvent(new TestEvent());
        assertEquals(1, later.get());
        LogRecord record = logs.records.get(0);
        assertTrue(record.getMessage().contains("[AtlasHybrid Event]"));
        assertTrue(record.getMessage().contains("Plugin: TestPlugin"));
        assertTrue(record.getMessage().contains("Event: TestEvent"));
        assertTrue(record.getMessage().contains("Listener: " + NamedListener.class.getName()));
        assertTrue(record.getMessage().contains("Status: EXECUTION_FAILED"));
        assertInstanceOf(EventException.class, record.getThrown());
    }

    @Test
    void unregisterListenerAndPluginRemoveOwnedRegistrations() {
        AtomicInteger calls = new AtomicInteger();
        Listener first = new Listener() { };
        Listener second = new Listener() { };
        manager.registerEvent(TestEvent.class, first, EventPriority.NORMAL,
            (registered, event) -> calls.incrementAndGet(), plugin);
        manager.registerEvent(TestEvent.class, second, EventPriority.NORMAL,
            (registered, event) -> calls.incrementAndGet(), plugin);
        HandlerList.unregisterAll(first);
        manager.callEvent(new TestEvent());
        assertEquals(1, calls.get());
        HandlerList.unregisterAll(plugin);
        manager.callEvent(new TestEvent());
        assertEquals(1, calls.get());
    }

    @Test
    void annotationRegistrationUsesTheSameExecutorPath() {
        AnnotatedListener listener = new AnnotatedListener();
        manager.registerEvents(listener, plugin);
        manager.callEvent(new TestEvent());
        assertEquals(1, listener.calls);
    }

    @Test
    void cleanupAllowsCleanRegistrationOnRestart() {
        AtomicInteger calls = new AtomicInteger();
        Listener listener = new Listener() { };
        manager.registerEvent(TestEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> calls.incrementAndGet(), plugin);
        manager.cleanupPluginResources(plugin);
        manager.registerEvent(TestEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> calls.incrementAndGet(), plugin);
        manager.callEvent(new TestEvent());
        assertEquals(1, calls.get());
    }

    @Test
    void asyncPreLoginUsesPriorityOrderAndContinuesAfterListenerFailure() {
        List<String> order = new ArrayList<>();
        manager.registerEvent(AsyncPlayerPreLoginEvent.class, new Listener() { }, EventPriority.LOW,
            (registered, event) -> order.add("low"), plugin);
        manager.registerEvent(AsyncPlayerPreLoginEvent.class, new NamedListener(), EventPriority.NORMAL,
            (registered, event) -> { throw new EventException(new IllegalStateException("prelogin boom")); }, plugin);
        manager.registerEvent(AsyncPlayerPreLoginEvent.class, new Listener() { }, EventPriority.MONITOR,
            (registered, event) -> order.add("monitor"), plugin);
        manager.callEvent(new AsyncPlayerPreLoginEvent("AtlasPlayer", InetAddress.getLoopbackAddress(),
            UUID.fromString("9f1e5b65-1e8e-3b20-a38d-fb6d51b71a70")));
        assertEquals(List.of("low", "monitor"), order);
        assertTrue(logs.records.stream().anyMatch(record ->
            record.getMessage().contains("Event: AsyncPlayerPreLoginEvent")
                && record.getMessage().contains("Status: EXECUTION_FAILED")));
    }

    @Test
    void playerLoginUsesPriorityOrderAndContinuesAfterListenerFailure() throws Exception {
        List<String> order = new ArrayList<>();
        manager.registerEvent(PlayerLoginEvent.class, new Listener() { }, EventPriority.LOWEST,
            (registered, event) -> order.add("lowest"), plugin);
        manager.registerEvent(PlayerLoginEvent.class, new NamedListener(), EventPriority.NORMAL,
            (registered, event) -> { throw new EventException(new IllegalStateException("login boom")); }, plugin);
        manager.registerEvent(PlayerLoginEvent.class, new Listener() { }, EventPriority.MONITOR,
            (registered, event) -> order.add("monitor"), plugin);
        Player player = (Player) java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[] { Player.class }, (proxy, method, arguments) -> null);
        manager.callEvent(new PlayerLoginEvent(player, "localhost:25565", InetAddress.getLoopbackAddress()));
        assertEquals(List.of("lowest", "monitor"), order);
        assertTrue(logs.records.stream().anyMatch(record ->
            record.getMessage().contains("Event: PlayerLoginEvent")
                && record.getMessage().contains("Status: EXECUTION_FAILED")));
    }

    @Test
    void serverCommandUsesPriorityMutationCancellationAndExceptionPolicy() {
        List<String> order = new ArrayList<>();
        CommandSender sender = new TestSender();
        manager.registerEvent(ServerCommandEvent.class, new Listener() { }, EventPriority.LOWEST,
            (registered, raw) -> {
                ServerCommandEvent event = (ServerCommandEvent) raw;
                order.add("lowest");
                event.setCommand("say changed");
            }, plugin);
        manager.registerEvent(ServerCommandEvent.class, new NamedListener(), EventPriority.NORMAL,
            (registered, event) -> { throw new EventException(new IllegalStateException("command boom")); }, plugin);
        manager.registerEvent(ServerCommandEvent.class, new Listener() { }, EventPriority.MONITOR,
            (registered, raw) -> {
                order.add("monitor");
                ((ServerCommandEvent) raw).setCancelled(true);
            }, plugin);
        ServerCommandEvent event = new ServerCommandEvent(sender, "say original");
        manager.callEvent(event);
        assertEquals(List.of("lowest", "monitor"), order);
        assertEquals("say changed", event.getCommand());
        assertTrue(event.isCancelled());
        assertTrue(logs.records.stream().anyMatch(record ->
            record.getMessage().contains("Event: ServerCommandEvent")
                && record.getMessage().contains("Status: EXECUTION_FAILED")));
    }

    @Test
    void remoteCommandHasIndependentHandlerListAndNoLocalDuplicate() {
        AtomicInteger local = new AtomicInteger();
        AtomicInteger remote = new AtomicInteger();
        manager.registerEvent(ServerCommandEvent.class, new Listener() { }, EventPriority.NORMAL,
            (registered, event) -> local.incrementAndGet(), plugin);
        manager.registerEvent(RemoteServerCommandEvent.class, new Listener() { }, EventPriority.NORMAL,
            (registered, event) -> remote.incrementAndGet(), plugin);
        manager.callEvent(new RemoteServerCommandEvent(new TestSender(), "list"));
        assertEquals(0, local.get());
        assertEquals(1, remote.get());
    }

    @Test
    void playerCommandUsesPriorityMutationCancellationAndExceptionPolicyOnce() {
        List<String> order = new ArrayList<>();
        Player player = (Player) java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[] { Player.class }, (proxy, method, arguments) -> null);
        manager.registerEvent(PlayerCommandPreprocessEvent.class, new Listener() { }, EventPriority.LOWEST,
            (registered, raw) -> {
                order.add("lowest");
                ((PlayerCommandPreprocessEvent) raw).setMessage("/atlas changed");
            }, plugin);
        manager.registerEvent(PlayerCommandPreprocessEvent.class, new NamedListener(), EventPriority.NORMAL,
            (registered, event) -> { throw new EventException(new IllegalStateException("player command boom")); }, plugin);
        manager.registerEvent(PlayerCommandPreprocessEvent.class, new Listener() { }, EventPriority.MONITOR,
            (registered, raw) -> {
                order.add("monitor");
                ((PlayerCommandPreprocessEvent) raw).setCancelled(true);
            }, plugin);
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/atlas original", new java.util.HashSet<>());
        manager.callEvent(event);
        assertEquals(List.of("lowest", "monitor"), order);
        assertEquals("/atlas changed", event.getMessage());
        assertTrue(event.isCancelled());
        assertTrue(logs.records.stream().anyMatch(record ->
            record.getMessage().contains("Event: PlayerCommandPreprocessEvent")
                && record.getMessage().contains("Status: EXECUTION_FAILED")));
    }

    private void register(List<String> order, String value, EventPriority priority) {
        manager.registerEvent(TestEvent.class, new Listener() { }, priority,
            (registered, event) -> order.add(value), plugin);
    }

    private AtlasPluginManager manager() {
        Logger logger = Logger.getLogger("event-test-" + System.identityHashCode(this));
        logger.setUseParentHandlers(false);
        logger.addHandler(logs);
        return new AtlasPluginManager(logger, new AtlasPermissionRegistry(),
            new PermissionProviderRegistry(logger), new AtlasServicesManager());
    }

    private static final class TestEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private boolean cancelled;
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    private static final class OtherEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    private static final class NamedListener implements Listener { }

    private static final class TestSender implements CommandSender {
        @Override public String getName() { return "test"; }
        @Override public void sendMessage(String message) { }
        @Override public boolean isOp() { return true; }
        @Override public void setOp(boolean value) { }
        @Override public boolean isPermissionSet(String permission) { return false; }
        @Override public boolean isPermissionSet(Permission permission) { return false; }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public boolean hasPermission(Permission permission) { return true; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return null; }
        @Override public void removeAttachment(PermissionAttachment attachment) { }
        @Override public void recalculatePermissions() { }
        @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return Set.of(); }
    }

    private static final class AnnotatedListener implements Listener {
        private int calls;
        @EventHandler public void onTest(TestEvent event) { calls++; }
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    }

    private static final class TestPlugin implements Plugin {
        private final PluginDescriptionFile description = new PluginDescriptionFile(
            "TestPlugin", "1", "test.Plugin", null, null, List.of(), List.of(), List.of(), Set.of());
        @Override public String getName() { return description.getName(); }
        @Override public PluginDescriptionFile getDescription() { return description; }
        @Override public Server getServer() { return null; }
        @Override public java.util.logging.Logger getLogger() { return Logger.getAnonymousLogger(); }
        @Override public File getDataFolder() { return new File("."); }
        @Override public FileConfiguration getConfig() { return null; }
        @Override public void reloadConfig() { }
        @Override public void saveDefaultConfig() { }
        @Override public boolean isEnabled() { return true; }
        @Override public void onLoad() { }
        @Override public void onEnable() { }
        @Override public void onDisable() { }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { return false; }
        @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return null; }
    }
}

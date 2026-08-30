package dev.atlashybrid.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.atlashybrid.runtime.command.CommandRegistry;
import dev.atlashybrid.runtime.event.AtlasPluginManager;
import dev.atlashybrid.runtime.permission.AtlasPermissionRegistry;
import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.scheduler.AtlasScheduler;
import dev.atlashybrid.runtime.service.AtlasServicesManager;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginLifecycleTest {
    private static final String MAIN = "atlashybrid.lifecyclefixture.LifecycleProbePlugin";

    @TempDir Path temp;

    @Test
    void lifecycleEventsAreOrderedObservableExceptionSafeAndRestartClean() throws Exception {
        runCycle(temp.resolve("first"));
        runCycle(temp.resolve("restart"));
    }

    private void runCycle(Path root) throws Exception {
        Logger logger = Logger.getLogger("plugin-lifecycle-" + root.getFileName());
        CapturingHandler logs = new CapturingHandler();
        logger.setUseParentHandlers(false);
        logger.addHandler(logs);
        AtlasPermissionRegistry permissions = new AtlasPermissionRegistry();
        PermissionProviderRegistry providers = new PermissionProviderRegistry(logger);
        AtlasServicesManager services = new AtlasServicesManager();
        AtlasPluginManager manager = new AtlasPluginManager(logger, permissions, providers, services);
        AtlasScheduler scheduler = new AtlasScheduler(logger);
        CommandRegistry commands = new CommandRegistry();
        TestServer server = new TestServer(manager, services, scheduler);
        PluginRuntime runtime = new PluginRuntime(server, manager, commands, scheduler, logger,
            PluginLifecycleTest.class.getClassLoader());
        List<LoadedPlugin> loaded = List.of();
        Bukkit.setServer(server);
        try {
            Path plugins = root.resolve("plugins");
            Files.createDirectories(plugins);
            writeFixtureJar(plugins.resolve("01-observer.jar"), "ObserverProbe");
            writeFixtureJar(plugins.resolve("02-target.jar"), "TargetProbe");
            runtime.loadAll(plugins);
            loaded = runtime.loadedPlugins();
            Plugin observer = manager.getPlugin("ObserverProbe");
            Plugin target = manager.getPlugin("TargetProbe");

            runtime.enableAll();
            runtime.enableAll();

            assertTrue(observer.isEnabled());
            assertTrue(target.isEnabled());
            assertEquals(1, field(observer, "enableCalls"));
            assertEquals(1, field(target, "enableCalls"));
            assertEquals(List.of("ObserverProbe", "TargetProbe"), field(observer, "enableEvents"));
            assertEquals(List.of("TargetProbe"), field(target, "enableEvents"));
            assertEquals(List.of(true, true), field(observer, "enableStates"));
            assertTrue(logs.messages().stream().anyMatch(message -> message.contains("PluginEnableEvent")
                && message.contains("EXECUTION_FAILED")));

            runtime.disableAll();
            runtime.disableAll();

            assertFalse(observer.isEnabled());
            assertFalse(target.isEnabled());
            assertEquals(1, field(observer, "disableCalls"));
            assertEquals(1, field(target, "disableCalls"));
            assertEquals(List.of("TargetProbe", "ObserverProbe"), field(observer, "disableEvents"));
            assertEquals(List.of("TargetProbe"), field(target, "disableEvents"));
            assertEquals(List.of(true, true), field(observer, "disableStates"));
            assertTrue(HandlerList.getRegisteredListeners(observer).isEmpty());
            assertTrue(HandlerList.getRegisteredListeners(target).isEmpty());
            assertEquals(0, scheduler.pendingTasks());
            assertNull(manager.getPlugin("ObserverProbe"));
            assertNull(manager.getPlugin("TargetProbe"));
        } finally {
            runtime.close();
            Bukkit.setServer(null);
            HandlerList.unregisterAll();
        }
        for (LoadedPlugin item : loaded) assertTrue(item.classLoader().isClosed());
    }

    private void writeFixtureJar(Path target, String name) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(target))) {
            writeEntry(output, "plugin.yml", ("name: " + name + "\nversion: 1\nmain: " + MAIN + "\n")
                .getBytes(StandardCharsets.UTF_8));
            String resource = MAIN.replace('.', '/') + ".class";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("Missing fixture class " + resource);
                writeEntry(output, resource, input.readAllBytes());
            }
        }
    }

    private static void writeEntry(JarOutputStream output, String name, byte[] bytes) throws Exception {
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private static Object field(Plugin plugin, String name) throws Exception {
        return plugin.getClass().getField(name).get(plugin);
    }

    private static final class CapturingHandler extends Handler {
        private final java.util.ArrayList<LogRecord> records = new java.util.ArrayList<>();
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
        List<String> messages() { return records.stream().map(LogRecord::getMessage).toList(); }
    }

    private static final class TestServer implements Server {
        private final PluginManager manager;
        private final ServicesManager services;
        private final BukkitScheduler scheduler;
        private TestServer(PluginManager manager, ServicesManager services, BukkitScheduler scheduler) {
            this.manager = manager;
            this.services = services;
            this.scheduler = scheduler;
        }
        @Override public java.util.Collection<? extends org.bukkit.entity.Player> getOnlinePlayers() { return List.of(); }
        @Override public org.bukkit.entity.Player getPlayer(UUID id) { return null; }
        @Override public org.bukkit.entity.Player getPlayerExact(String name) { return null; }
        @Override public String getName() { return "test"; }
        @Override public String getVersion() { return "test"; }
        @Override public String getBukkitVersion() { return "test"; }
        @Override public String getMinecraftVersion() { return "1.19.2"; }
        @Override public String getForgeVersion() { return "43"; }
        @Override public String getAtlasHybridVersion() { return "test"; }
        @Override public org.bukkit.UnsafeValues getUnsafe() { return null; }
        @Override public boolean getOnlineMode() { return true; }
        @Override public int getDetectedModCount() { return 0; }
        @Override public PluginManager getPluginManager() { return manager; }
        @Override public ServicesManager getServicesManager() { return services; }
        @Override public ConsoleCommandSender getConsoleSender() { return null; }
        @Override public BukkitScheduler getScheduler() { return scheduler; }
        @Override public PluginCommand getPluginCommand(String name) { return null; }
        @Override public World getWorld(String name) { return null; }
        @Override public Logger getLogger() { return Logger.getAnonymousLogger(); }
    }
}

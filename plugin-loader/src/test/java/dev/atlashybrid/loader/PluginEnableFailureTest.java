package dev.atlashybrid.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.atlashybrid.runtime.command.CommandRegistry;
import dev.atlashybrid.runtime.event.AtlasPluginManager;
import dev.atlashybrid.runtime.permission.AtlasPermissible;
import dev.atlashybrid.runtime.permission.AtlasPermissionRegistry;
import dev.atlashybrid.runtime.permission.AtlasPermissions;
import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.permission.PermissionSubject;
import dev.atlashybrid.runtime.scheduler.AtlasScheduler;
import dev.atlashybrid.runtime.service.AtlasServicesManager;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginEnableFailureTest {
    private static final String MAIN = "atlashybrid.enablefixture.FailedEnablePlugin";

    @TempDir Path temp;

    @Test
    void failedEnableRollsBackOwnedResourcesAndDiagnosesLiveThread() throws Exception {
        Logger logger = Logger.getLogger("failed-enable-" + System.identityHashCode(this));
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
        Bukkit.setServer(server);
        AtlasPermissions.install(providers);
        TestConsole console = new TestConsole(permissions, providers);
        server.console = console;
        PluginRuntime runtime = new PluginRuntime(server, manager, commands, scheduler, logger,
            PluginEnableFailureTest.class.getClassLoader());
        try {
            Path plugins = temp.resolve("plugins");
            Files.createDirectories(plugins);
            writeFixtureJar(plugins.resolve("failed-enable.jar"));
            runtime.loadAll(plugins);
            LoadedPlugin loaded = runtime.loadedPlugins().get(0);
            Plugin plugin = loaded.plugin();
            assertSame(plugin, manager.getPlugin("FailedEnableProbe"));
            assertTrue(commands.names().contains("failedprobe"));

            runtime.enableAll();

            assertFalse(plugin.isEnabled());
            assertEquals(1, plugin.getClass().getField("disableCalls").getInt(plugin));
            assertTrue(HandlerList.getRegisteredListeners(plugin).isEmpty());
            assertEquals(0, scheduler.pendingTasks(plugin));
            assertEquals(0, services.getRegistrations(plugin).size());
            assertEquals(0, providers.registrations().stream().filter(entry -> entry.owner() == plugin).count());
            assertFalse(console.isPermissionSet("atlas.failed-enable"));
            assertNull(commands.get("failedprobe"));
            assertSame(plugin, manager.getPlugin("FailedEnableProbe"));
            assertEquals(List.of("FailedEnableProbe-worker"), runtime.failedEnableThreads("FailedEnableProbe"));
            assertTrue(logs.messages().stream().anyMatch(message -> message.contains("FAILED_ENABLE_ROLLBACK_OK")));
            assertTrue(logs.messages().stream().anyMatch(message -> message.contains("ENABLE_FAILED_WITH_LIVE_THREADS")));
            plugin.getClass().getMethod("stopWorker").invoke(plugin);

            runtime.enableAll();
            assertFalse(plugin.isEnabled());
            assertEquals(2, plugin.getClass().getField("disableCalls").getInt(plugin));
            assertTrue(HandlerList.getRegisteredListeners(plugin).isEmpty());
            assertEquals(0, scheduler.pendingTasks(plugin));
            assertEquals(0, services.getRegistrations(plugin).size());
            assertEquals(0, providers.size());
            plugin.getClass().getMethod("stopWorker").invoke(plugin);
            assertFalse(loaded.classLoader().isClosed());
        } finally {
            runtime.close();
            console.close();
            AtlasPermissions.clear(providers);
            Bukkit.setServer(null);
            HandlerList.unregisterAll();
        }
        assertTrue(runtime.loadedPlugins().isEmpty());
    }

    private void writeFixtureJar(Path target) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(target))) {
            writeEntry(output, "plugin.yml", ("name: FailedEnableProbe\n"
                + "version: 1\n"
                + "main: " + MAIN + "\n"
                + "commands:\n"
                + "  failedprobe:\n"
                + "    description: rollback probe\n").getBytes(StandardCharsets.UTF_8));
            for (String suffix : List.of("", "$1", "$FailureEvent")) {
                String resource = MAIN.replace('.', '/') + suffix + ".class";
                try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                    if (input == null) throw new IllegalStateException("Missing fixture class " + resource);
                    writeEntry(output, resource, input.readAllBytes());
                }
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
        private ConsoleCommandSender console;
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
        @Override public int getDetectedModCount() { return 0; }
        @Override public PluginManager getPluginManager() { return manager; }
        @Override public ServicesManager getServicesManager() { return services; }
        @Override public ConsoleCommandSender getConsoleSender() { return console; }
        @Override public BukkitScheduler getScheduler() { return scheduler; }
        @Override public PluginCommand getPluginCommand(String name) { return null; }
        @Override public World getWorld(String name) { return null; }
        @Override public Logger getLogger() { return Logger.getAnonymousLogger(); }
    }

    private static final class TestConsole implements ConsoleCommandSender, AutoCloseable {
        private final AtlasPermissible permissions;
        private TestConsole(AtlasPermissionRegistry registry, PermissionProviderRegistry providers) {
            permissions = new AtlasPermissible(new ServerOperator() {
                @Override public boolean isOp() { return true; }
                @Override public void setOp(boolean value) { }
            }, registry, providers, () -> new PermissionSubject("CONSOLE", null, PermissionSubject.Type.CONSOLE, true));
            permissions.recalculatePermissions();
        }
        @Override public String getName() { return "CONSOLE"; }
        @Override public void sendMessage(String message) { }
        @Override public boolean isOp() { return permissions.isOp(); }
        @Override public void setOp(boolean value) { permissions.setOp(value); }
        @Override public boolean isPermissionSet(String permission) { return permissions.isPermissionSet(permission); }
        @Override public boolean isPermissionSet(Permission permission) { return permissions.isPermissionSet(permission); }
        @Override public boolean hasPermission(String permission) { return permissions.hasPermission(permission); }
        @Override public boolean hasPermission(Permission permission) { return permissions.hasPermission(permission); }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return permissions.addAttachment(plugin, name, value); }
        @Override public PermissionAttachment addAttachment(Plugin plugin) { return permissions.addAttachment(plugin); }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return permissions.addAttachment(plugin, name, value, ticks); }
        @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return permissions.addAttachment(plugin, ticks); }
        @Override public void removeAttachment(PermissionAttachment attachment) { permissions.removeAttachment(attachment); }
        @Override public void recalculatePermissions() { permissions.recalculatePermissions(); }
        @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return permissions.getEffectivePermissions(); }
        @Override public void close() { permissions.close(); }
    }
}

package dev.atlashybrid.runtime.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.atlashybrid.runtime.event.AtlasPluginManager;
import dev.atlashybrid.runtime.scheduler.AtlasScheduler;
import dev.atlashybrid.runtime.service.AtlasServicesManager;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermissionCoreTest {
    private Logger logger;
    private AtlasPermissionRegistry permissions;
    private PermissionProviderRegistry providers;
    private AtlasServicesManager services;
    private AtlasPluginManager pluginManager;
    private AtlasScheduler scheduler;
    private TestServer server;
    private TestPlugin plugin;
    private final java.util.ArrayList<AtlasPermissible> subjects = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        permissions = new AtlasPermissionRegistry();
        providers = new PermissionProviderRegistry(logger);
        services = new AtlasServicesManager();
        pluginManager = new AtlasPluginManager(logger, permissions, providers, services);
        scheduler = new AtlasScheduler(logger);
        server = new TestServer(pluginManager, services, scheduler);
        Bukkit.setServer(server);
        plugin = new TestPlugin("Owner", server);
        pluginManager.addPlugin(plugin);
    }

    @AfterEach
    void tearDown() {
        subjects.forEach(AtlasPermissible::close);
        Bukkit.setServer(null);
    }

    @Test void trueDefaultAlwaysGrants() {
        add("example.true", PermissionDefault.TRUE);
        assertTrue(subject(false).hasPermission("example.true"));
    }

    @Test void falseDefaultAlwaysDenies() {
        add("example.false", PermissionDefault.FALSE);
        assertFalse(subject(true).hasPermission("example.false"));
    }

    @Test void opDefaultTracksOperatorState() {
        MutableOperator operator = new MutableOperator(false, true);
        AtlasPermissible subject = subject(operator, PermissionSubject.Type.PLAYER);
        add("example.op", PermissionDefault.OP);
        assertFalse(subject.hasPermission("example.op"));
        subject.setOp(true);
        assertTrue(subject.hasPermission("example.op"));
    }

    @Test void notOpDefaultTracksOperatorState() {
        MutableOperator operator = new MutableOperator(false, true);
        AtlasPermissible subject = subject(operator, PermissionSubject.Type.PLAYER);
        add("example.notop", PermissionDefault.NOT_OP);
        assertTrue(subject.hasPermission("example.notop"));
        subject.setOp(true);
        assertFalse(subject.hasPermission("example.notop"));
    }

    @Test void explicitAttachmentCanGrant() {
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "example.node", true);
        assertTrue(subject.hasPermission("example.node"));
    }

    @Test void explicitAttachmentCanDeny() {
        add("example.node", PermissionDefault.TRUE);
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "example.node", false);
        assertFalse(subject.hasPermission("example.node"));
    }

    @Test void laterAttachmentOverridesEarlierAttachment() {
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "example.node", true);
        subject.addAttachment(plugin, "example.node", false);
        assertFalse(subject.hasPermission("example.node"));
    }

    @Test void removingAttachmentRecalculatesAndInvokesCallbackOnce() {
        AtlasPermissible subject = subject(false);
        PermissionAttachment attachment = subject.addAttachment(plugin, "example.node", true);
        AtomicInteger callbacks = new AtomicInteger();
        attachment.setRemovalCallback(ignored -> callbacks.incrementAndGet());
        assertTrue(attachment.remove());
        assertFalse(attachment.remove());
        assertFalse(subject.hasPermission("example.node"));
        assertEquals(1, callbacks.get());
    }

    @Test void timedAttachmentUsesSchedulerAndExpires() {
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "example.node", true, 2);
        assertTrue(subject.hasPermission("example.node"));
        scheduler.tick();
        assertTrue(subject.hasPermission("example.node"));
        scheduler.tick();
        assertFalse(subject.hasPermission("example.node"));
    }

    @Test void effectivePermissionReportsRealAttachmentAndValue() {
        AtlasPermissible subject = subject(false);
        PermissionAttachment attachment = subject.addAttachment(plugin, "Example.Node", true);
        var info = subject.getEffectivePermissions().stream()
            .filter(candidate -> candidate.getPermission().equals("example.node"))
            .findFirst().orElseThrow();
        assertSame(subject, info.getPermissible());
        assertSame(attachment, info.getAttachment());
        assertTrue(info.getValue());
    }

    @Test void clearThenRecalculateRebuildsExistingAttachments() {
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "example.node", true);
        subject.clearPermissions();
        assertFalse(subject.isPermissionSet("example.node"));
        subject.recalculatePermissions();
        assertTrue(subject.hasPermission("example.node"));
    }

    @Test void registeredChildrenAreExpandedAndParentFalseInverts() {
        Permission permission = new Permission("example.parent", PermissionDefault.FALSE,
            Map.of("example.child", true, "example.inverted", false));
        pluginManager.addPermission(permission);
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "example.parent", false);
        assertFalse(subject.hasPermission("example.child"));
        assertTrue(subject.hasPermission("example.inverted"));
    }

    @Test void nodesAreCaseInsensitiveButWildcardsAreNotInvented() {
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "Example.Node", true);
        assertTrue(subject.hasPermission("EXAMPLE.NODE"));
        assertFalse(subject.hasPermission("example.*"));
    }

    @Test void consoleSubjectUsesRealOpDefault() {
        AtlasPermissible console = subject(new MutableOperator(true, false), PermissionSubject.Type.CONSOLE);
        add("example.console", PermissionDefault.OP);
        assertTrue(console.isOp());
        assertTrue(console.hasPermission("example.console"));
    }

    @Test void providerCanAnswerPermission() {
        providers.register(plugin, (ignored, node) -> Optional.of(node.equals("example.provider")), PermissionProviderPriority.NORMAL);
        assertTrue(subject(false).hasPermission("example.provider"));
    }

    @Test void explicitAttachmentTakesPriorityOverProvider() {
        providers.register(plugin, (ignored, node) -> Optional.of(true), PermissionProviderPriority.HIGHEST);
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "example.node", false);
        assertFalse(subject.hasPermission("example.node"));
    }

    @Test void highestProviderPriorityWins() {
        providers.register(plugin, (ignored, node) -> Optional.of(true), PermissionProviderPriority.LOW);
        providers.register(plugin, (ignored, node) -> Optional.of(false), PermissionProviderPriority.HIGH);
        assertFalse(subject(false).hasPermission("example.node"));
    }

    @Test void unregisterProviderRestoresCoreFallback() {
        PermissionProvider provider = (ignored, node) -> Optional.of(true);
        providers.register(plugin, provider, PermissionProviderPriority.NORMAL);
        AtlasPermissible subject = subject(false);
        assertTrue(subject.hasPermission("example.node"));
        providers.unregister(provider);
        assertFalse(subject.hasPermission("example.node"));
    }

    @Test void failedProviderIsLoggedAndFallsBack() {
        AtomicInteger severe = new AtomicInteger();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { if (record.getLevel().intValue() >= java.util.logging.Level.SEVERE.intValue()) severe.incrementAndGet(); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        providers.register(plugin, (ignored, node) -> { throw new IllegalStateException("boom"); }, PermissionProviderPriority.HIGH);
        providers.register(plugin, (ignored, node) -> Optional.of(true), PermissionProviderPriority.LOW);
        assertTrue(subject(false).hasPermission("example.node"));
        assertEquals(1, severe.get());
    }

    @Test void serviceRegistrationAndLookupWork() {
        Runnable value = () -> { };
        services.register(Runnable.class, value, plugin, ServicePriority.Normal);
        assertSame(value, services.load(Runnable.class));
        assertNotNull(services.getRegistration(Runnable.class));
        assertTrue(services.getKnownServices().contains(Runnable.class));
    }

    @Test void highestServicePriorityWinsAndUnregisterRestoresNext() {
        Runnable low = () -> { };
        Runnable high = () -> { };
        services.register(Runnable.class, low, plugin, ServicePriority.Low);
        services.register(Runnable.class, high, plugin, ServicePriority.High);
        assertSame(high, services.load(Runnable.class));
        services.unregister(Runnable.class, high);
        assertSame(low, services.load(Runnable.class));
    }

    @Test void pluginCleanupRemovesAttachmentsProvidersAndServices() {
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "example.attachment", true);
        providers.register(plugin, (ignored, node) -> Optional.of(true), PermissionProviderPriority.NORMAL);
        services.register(Runnable.class, () -> { }, plugin, ServicePriority.Normal);
        pluginManager.unregisterPlugin(plugin, List.of());
        assertFalse(subject.hasPermission("example.attachment"));
        assertEquals(0, providers.size());
        assertNull(services.load(Runnable.class));
    }

    @Test void concurrentQueriesObservePublishedSnapshots() throws Exception {
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "example.node", true);
        AtomicBoolean failed = new AtomicBoolean();
        Thread reader = new Thread(() -> {
            for (int index = 0; index < 2_000; index++) {
                if (!subject.hasPermission("example.node")) failed.set(true);
            }
        });
        reader.start();
        reader.join();
        assertFalse(failed.get());
    }

    @Test void closingSubjectReleasesRegistryAndSubscriptions() {
        AtlasPermissible subject = subject(false);
        subject.addAttachment(plugin, "example.node", true);
        assertEquals(1, permissions.subjectCount());
        assertTrue(pluginManager.getPermissionSubscriptions("example.node").contains(subject));
        subject.close();
        subjects.remove(subject);
        assertEquals(0, permissions.subjectCount());
        assertFalse(pluginManager.getPermissionSubscriptions("example.node").contains(subject));
    }

    private Permission add(String name, PermissionDefault defaultValue) {
        Permission permission = new Permission(name, defaultValue);
        pluginManager.addPermission(permission);
        return permission;
    }

    private AtlasPermissible subject(boolean op) {
        return subject(new MutableOperator(op, true), PermissionSubject.Type.PLAYER);
    }

    private AtlasPermissible subject(MutableOperator operator, PermissionSubject.Type type) {
        UUID uniqueId = type == PermissionSubject.Type.PLAYER ? UUID.randomUUID() : null;
        AtlasPermissible permissible = new AtlasPermissible(operator, permissions, providers,
            () -> new PermissionSubject(type.name(), uniqueId, type, operator.isOp()));
        subjects.add(permissible);
        permissible.recalculatePermissions();
        return permissible;
    }

    private static final class MutableOperator implements ServerOperator {
        private boolean op;
        private final boolean mutable;
        private MutableOperator(boolean op, boolean mutable) { this.op = op; this.mutable = mutable; }
        @Override public boolean isOp() { return op; }
        @Override public void setOp(boolean value) {
            if (!mutable) throw new UnsupportedOperationException("immutable");
            op = value;
        }
    }

    private static final class TestPlugin implements Plugin {
        private final PluginDescriptionFile description;
        private final Server server;
        private TestPlugin(String name, Server server) {
            this.description = new PluginDescriptionFile(name, "1", "example.Plugin", null, null, List.of(), List.of(), List.of(), Set.of());
            this.server = server;
        }
        @Override public String getName() { return description.getName(); }
        @Override public PluginDescriptionFile getDescription() { return description; }
        @Override public Server getServer() { return server; }
        @Override public Logger getLogger() { return Logger.getAnonymousLogger(); }
        @Override public File getDataFolder() { return new File("."); }
        @Override public FileConfiguration getConfig() { return null; }
        @Override public void reloadConfig() { }
        @Override public void saveDefaultConfig() { }
        @Override public boolean isEnabled() { return true; }
        @Override public void onLoad() { }
        @Override public void onEnable() { }
        @Override public void onDisable() { }
        @Override public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) { return false; }
        @Override public java.util.List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) { return null; }
    }

    private record TestServer(PluginManager pluginManager, ServicesManager services, BukkitScheduler scheduler) implements Server {
        @Override public java.util.Collection<? extends org.bukkit.entity.Player> getOnlinePlayers() { return java.util.List.of(); }
        @Override public org.bukkit.entity.Player getPlayer(java.util.UUID id) { return null; }
        @Override public org.bukkit.entity.Player getPlayerExact(String name) { return null; }
        @Override public String getName() { return "test"; }
        @Override public String getVersion() { return "test"; }
        @Override public String getBukkitVersion() { return "test"; }
        @Override public String getMinecraftVersion() { return "1.19.2"; }
        @Override public String getForgeVersion() { return "43.5.0"; }
        @Override public String getAtlasHybridVersion() { return "0.1.0-alpha"; }
        @Override public int getDetectedModCount() { return 0; }
        @Override public PluginManager getPluginManager() { return pluginManager; }
        @Override public ServicesManager getServicesManager() { return services; }
        @Override public ConsoleCommandSender getConsoleSender() { return null; }
        @Override public BukkitScheduler getScheduler() { return scheduler; }
        @Override public PluginCommand getPluginCommand(String name) { return null; }
        @Override public World getWorld(String name) { return null; }
        @Override public Logger getLogger() { return Logger.getAnonymousLogger(); }
    }
}

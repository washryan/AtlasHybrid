package dev.atlashybrid.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginBootstrap;
import org.bukkit.plugin.java.PluginBootstrapPhaseException;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginBootstrapTest {
    private static final String CONSTRUCTOR_PROBE = "atlashybrid.bootstrapfixture.ConstructorProbe";
    private static final String THROWING_PROBE = "atlashybrid.bootstrapfixture.ThrowingConstructorProbe";
    private static final String UNAVAILABLE_PROBE = "atlashybrid.bootstrapfixture.UnavailableApiProbe";

    @TempDir Path temp;

    @Test
    void constructorSeesCompleteSafeContextAndKeepsLoggerIdentity() throws Exception {
        JavaPluginBootstrap.Context context = context("ConstructorSafe", temp.resolve("data"));
        try (AtlasPluginClassLoader loader = loader(context, CONSTRUCTOR_PROBE)) {
            JavaPlugin plugin;
            try (AtlasPluginClassLoader.ConstructionScope ignored = loader.beginConstruction()) {
                plugin = instantiate(loader, CONSTRUCTOR_PROBE);
                assertSame(context.logger(), field(plugin, "earlyLogger"));
                assertEquals("ConstructorSafe", field(plugin, "earlyName"));
                assertSame(context.description(), field(plugin, "earlyDescription"));
                assertEquals(context.dataFolder(), field(plugin, "earlyDataFolder"));
                assertSame(context.server(), field(plugin, "earlyServer"));
                assertNotNull(field(plugin, "earlyConfig"));
                assertEquals(false, field(plugin, "earlyEnabled"));
            }
            plugin.atlasInitialize(context.server(), context.description(), context.dataFolder(), context.logger());
            assertSame(context.logger(), plugin.getLogger());
        }
    }

    @Test
    void multipleInstancesInOneOwnedConstructionShareContext() throws Exception {
        JavaPluginBootstrap.Context context = context("Shared", temp.resolve("shared"));
        try (AtlasPluginClassLoader loader = loader(context, CONSTRUCTOR_PROBE);
             AtlasPluginClassLoader.ConstructionScope ignored = loader.beginConstruction()) {
            JavaPlugin first = instantiate(loader, CONSTRUCTOR_PROBE);
            JavaPlugin second = instantiate(loader, CONSTRUCTOR_PROBE);
            assertSame(first.getLogger(), second.getLogger());
            assertSame(first.getServer(), second.getServer());
            assertSame(first.getDescription(), second.getDescription());
        }
    }

    @Test
    void constructionContextIsClearedAfterSuccess() throws Exception {
        JavaPluginBootstrap.Context context = context("Cleanup", temp.resolve("cleanup"));
        try (AtlasPluginClassLoader loader = loader(context, CONSTRUCTOR_PROBE)) {
            try (AtlasPluginClassLoader.ConstructionScope ignored = loader.beginConstruction()) {
                instantiate(loader, CONSTRUCTOR_PROBE);
            }
            assertFalse(loader.hasActiveConstructionContext());
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> instantiate(loader, CONSTRUCTOR_PROBE));
            assertEquals(PluginBootstrapPhaseException.class, failure.getCause().getClass());
        }
    }

    @Test
    void constructionContextIsClearedWhenConstructorThrows() throws Exception {
        JavaPluginBootstrap.Context context = context("Throws", temp.resolve("throws"));
        try (AtlasPluginClassLoader loader = loader(context, THROWING_PROBE)) {
            assertThrows(InvocationTargetException.class, () -> {
                try (AtlasPluginClassLoader.ConstructionScope ignored = loader.beginConstruction()) {
                    instantiate(loader, THROWING_PROBE);
                }
            });
            assertFalse(loader.hasActiveConstructionContext());
        }
    }

    @Test
    void sequentialPluginsKeepSeparateIdentity() throws Exception {
        JavaPluginBootstrap.Context firstContext = context("First", temp.resolve("first"));
        JavaPluginBootstrap.Context secondContext = context("Second", temp.resolve("second"));
        try (AtlasPluginClassLoader firstLoader = loader(firstContext, CONSTRUCTOR_PROBE);
             AtlasPluginClassLoader secondLoader = loader(secondContext, CONSTRUCTOR_PROBE)) {
            JavaPlugin first;
            JavaPlugin second;
            try (AtlasPluginClassLoader.ConstructionScope ignored = firstLoader.beginConstruction()) {
                first = instantiate(firstLoader, CONSTRUCTOR_PROBE);
            }
            try (AtlasPluginClassLoader.ConstructionScope ignored = secondLoader.beginConstruction()) {
                second = instantiate(secondLoader, CONSTRUCTOR_PROBE);
            }
            assertEquals("First", first.getName());
            assertEquals("Second", second.getName());
            assertFalse(first.getLogger() == second.getLogger());
        }
    }

    @Test
    void constructionActivationIsThreadConfined() throws Exception {
        JavaPluginBootstrap.Context context = context("Parallel", temp.resolve("parallel"));
        try (AtlasPluginClassLoader loader = loader(context, CONSTRUCTOR_PROBE)) {
            var executor = Executors.newFixedThreadPool(2);
            try {
                Callable<Boolean> active = () -> {
                    try (AtlasPluginClassLoader.ConstructionScope ignored = loader.beginConstruction()) {
                        return loader.atlasBootstrapContext(loader) == context;
                    }
                };
                Callable<Boolean> inactive = () -> loader.atlasBootstrapContext(loader) == null;
                assertEquals(List.of(true, true), executor.invokeAll(List.of(active, inactive)).stream()
                    .map(future -> {
                        try { return future.get(); }
                        catch (Exception exception) { throw new AssertionError(exception); }
                    }).toList());
            } finally {
                executor.shutdownNow();
            }
            assertFalse(loader.hasActiveConstructionContext());
        }
    }

    @Test
    void ownershipAcceptsDescendantsButRejectsUnrelatedLoaders() throws Exception {
        JavaPluginBootstrap.Context context = context("Owner", temp.resolve("owner"));
        try (AtlasPluginClassLoader loader = loader(context, CONSTRUCTOR_PROBE);
             URLClassLoader descendant = new URLClassLoader(new java.net.URL[0], loader);
             URLClassLoader unrelated = new URLClassLoader(new java.net.URL[0], JavaPlugin.class.getClassLoader());
             AtlasPluginClassLoader.ConstructionScope ignored = loader.beginConstruction()) {
            assertSame(context, loader.atlasBootstrapContext(loader));
            assertSame(context, loader.atlasBootstrapContext(descendant));
            assertNull(loader.atlasBootstrapContext(unrelated));
        }
    }

    @Test
    void unavailableConstructorApiHasStructuredPhaseDiagnostic() throws Exception {
        JavaPluginBootstrap.Context context = context("Unavailable", temp.resolve("unavailable"));
        try (AtlasPluginClassLoader loader = loader(context, UNAVAILABLE_PROBE)) {
            InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> {
                try (AtlasPluginClassLoader.ConstructionScope ignored = loader.beginConstruction()) {
                    instantiate(loader, UNAVAILABLE_PROBE);
                }
            });
            PluginBootstrapPhaseException phase = (PluginBootstrapPhaseException) failure.getCause();
            assertEquals("JavaPlugin#getCommand", phase.api());
            assertEquals("CONSTRUCTION", phase.phase());
            assertEquals("AVAILABLE_LATER", phase.status());
        }
    }

    private AtlasPluginClassLoader loader(JavaPluginBootstrap.Context context, String... classes) throws Exception {
        Path root = Files.createTempDirectory(temp, "classes-");
        for (String className : classes) {
            String resource = className.replace('.', '/') + ".class";
            Path destination = root.resolve(resource);
            Files.createDirectories(destination.getParent());
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("Missing test class resource " + resource);
                Files.copy(input, destination);
            }
        }
        return new AtlasPluginClassLoader(root.toUri().toURL(), JavaPlugin.class.getClassLoader(), List.of(), context);
    }

    private static JavaPlugin instantiate(AtlasPluginClassLoader loader, String className) throws Exception {
        return loader.loadClass(className).asSubclass(JavaPlugin.class).getDeclaredConstructor().newInstance();
    }

    private static Object field(JavaPlugin plugin, String name) throws Exception {
        return plugin.getClass().getField(name).get(plugin);
    }

    private static JavaPluginBootstrap.Context context(String name, Path dataFolder) {
        PluginDescriptionFile description = new PluginDescriptionFile(
            name, "1", "probe.Main", null, null, List.of(), List.of(), List.of(), Set.of());
        return new JavaPluginBootstrap.Context(
            new EmptyServer(), description, dataFolder.toFile(), Logger.getLogger("test." + name));
    }

    private static final class EmptyServer implements Server {
        @Override public String getName() { return "test"; }
        @Override public String getVersion() { return "test"; }
        @Override public String getBukkitVersion() { return "test"; }
        @Override public String getMinecraftVersion() { return "1.19.2"; }
        @Override public String getForgeVersion() { return "43"; }
        @Override public String getAtlasHybridVersion() { return "test"; }
        @Override public int getDetectedModCount() { return 0; }
        @Override public PluginManager getPluginManager() { return null; }
        @Override public org.bukkit.plugin.ServicesManager getServicesManager() { return null; }
        @Override public org.bukkit.command.ConsoleCommandSender getConsoleSender() { return null; }
        @Override public BukkitScheduler getScheduler() { return null; }
        @Override public PluginCommand getPluginCommand(String name) { return null; }
        @Override public World getWorld(String name) { return null; }
        @Override public Logger getLogger() { return Logger.getAnonymousLogger(); }
    }
}

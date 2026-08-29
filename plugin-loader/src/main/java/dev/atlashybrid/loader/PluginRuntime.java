package dev.atlashybrid.loader;

import dev.atlashybrid.diagnostics.CompatibilityRuntime;
import dev.atlashybrid.runtime.command.CommandRegistry;
import dev.atlashybrid.runtime.event.AtlasPluginManager;
import dev.atlashybrid.runtime.scheduler.AtlasScheduler;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginBootstrap;
import org.bukkit.plugin.java.PluginBootstrapPhaseException;

public final class PluginRuntime implements AutoCloseable {
    private final Server server;
    private final AtlasPluginManager pluginManager;
    private final CommandRegistry commands;
    private final AtlasScheduler scheduler;
    private final Logger logger;
    private final ClassLoader apiClassLoader;
    private final List<LoadedPlugin> loaded = new ArrayList<>();
    private final PluginMetadataParser parser = new PluginMetadataParser();
    private final DependencyResolver resolver = new DependencyResolver();
    private final PluginThreadMonitor threadMonitor = new PluginThreadMonitor();
    private final Map<String, List<String>> failedEnableThreads = new HashMap<>();
    private final Map<String, List<PluginThreadMonitor.ThreadDiagnostic>> failedEnableThreadDiagnostics = new HashMap<>();
    private int discoveredCount;

    public PluginRuntime(Server server, AtlasPluginManager pluginManager, CommandRegistry commands, AtlasScheduler scheduler, Logger logger, ClassLoader apiClassLoader) {
        this.server = server;
        this.pluginManager = pluginManager;
        this.commands = commands;
        this.scheduler = scheduler;
        this.logger = logger;
        this.apiClassLoader = apiClassLoader;
    }

    public void loadAll(Path pluginsDirectory) throws IOException, DependencyResolutionException {
        Files.createDirectories(pluginsDirectory);
        List<PluginCandidate> candidates = new ArrayList<>();
        try (Stream<Path> files = Files.list(pluginsDirectory)) {
            for (Path jar : files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).sorted().toList()) {
                try {
                    candidates.add(new PluginCandidate(jar, parser.parse(jar)));
                } catch (InvalidPluginMetadataException exception) {
                    logger.log(Level.SEVERE, "Rejected plugin " + jar.getFileName() + ": " + exception.getMessage(), exception);
                }
            }
        }
        discoveredCount = candidates.size();
        List<PluginCandidate> order = resolver.resolve(candidates);
        Map<String, LoadedPlugin> byName = new HashMap<>();
        for (PluginCandidate candidate : order) {
            boolean missingLoadedDependency = candidate.metadata().depend().stream().anyMatch(name -> !byName.containsKey(key(name)));
            if (missingLoadedDependency) {
                logger.severe("Skipping " + candidate.metadata().name() + " because a hard dependency failed to load");
                continue;
            }
            try {
                LoadedPlugin plugin = loadOne(candidate, byName);
                loaded.add(plugin);
                byName.put(key(candidate.metadata().name()), plugin);
            } catch (Throwable throwable) {
                reportCompatibilityFailure(candidate.metadata().name(), throwable);
                logger.log(Level.SEVERE, "Failed to load plugin " + candidate.metadata().name(), throwable);
            }
        }
    }

    private LoadedPlugin loadOne(PluginCandidate candidate, Map<String, LoadedPlugin> byName) throws Exception {
        List<AtlasPluginClassLoader> dependencies = new ArrayList<>();
        Stream.concat(candidate.metadata().depend().stream(), candidate.metadata().softDepend().stream())
            .map(name -> byName.get(key(name))).filter(java.util.Objects::nonNull).map(LoadedPlugin::classLoader).forEach(dependencies::add);
        URL jarUrl = candidate.jar().toUri().toURL();
        Path dataPath = candidate.jar().getParent().resolve(candidate.metadata().name());
        org.bukkit.plugin.PluginDescriptionFile description = candidate.metadata().toDescription();
        Logger pluginLogger = Logger.getLogger("AtlasHybrid.Plugin." + candidate.metadata().name());
        JavaPluginBootstrap.Context bootstrapContext = new JavaPluginBootstrap.Context(
            server, description, dataPath.toFile(), pluginLogger);
        AtlasPluginClassLoader classLoader = new AtlasPluginClassLoader(
            jarUrl, apiClassLoader, dependencies, bootstrapContext);
        JavaPlugin plugin = null;
        try {
            try (AtlasPluginClassLoader.ConstructionScope ignored = classLoader.beginConstruction()) {
                Class<?> mainClass = Class.forName(candidate.metadata().main(), true, classLoader);
                if (!JavaPlugin.class.isAssignableFrom(mainClass)) throw new IllegalArgumentException("Main class does not extend JavaPlugin: " + mainClass.getName());
                @SuppressWarnings("unchecked")
                Constructor<? extends JavaPlugin> constructor = ((Class<? extends JavaPlugin>) mainClass).getDeclaredConstructor();
                constructor.setAccessible(true);
                plugin = constructor.newInstance();
            }
            plugin.atlasInitialize(server, description, dataPath.toFile(), pluginLogger);
            pluginManager.addPlugin(plugin);
            for (String commandName : candidate.metadata().commands()) {
                org.bukkit.command.PluginCommand command = commands.register(commandName, plugin);
                for (String alias : candidate.metadata().commandAliases().getOrDefault(commandName, Set.of())) {
                    commands.registerAlias(alias, command);
                }
            }
            try (CompatibilityRuntime.Scope ignored = CompatibilityRuntime.enter(plugin.getName())) {
                plugin.onLoad();
            }
            logger.info("Loaded plugin " + candidate.metadata().name() + " v" + candidate.metadata().version());
            return new LoadedPlugin(candidate, plugin, classLoader);
        } catch (Throwable throwable) {
            if (plugin != null) {
                scheduler.cancelTasks(plugin);
                commands.unregister(plugin);
                pluginManager.unregisterPlugin(plugin);
            }
            try { classLoader.close(); } catch (IOException closeFailure) { throwable.addSuppressed(closeFailure); }
            throw throwable;
        }
    }

    public void enableAll() {
        for (LoadedPlugin item : loaded) {
            PluginThreadMonitor.Snapshot threadBaseline = threadMonitor.capture();
            try {
                try (CompatibilityRuntime.Scope ignored = CompatibilityRuntime.enter(item.plugin().getName())) {
                    item.plugin().atlasSetEnabled(true);
                }
                logger.info("Enabled plugin " + item.plugin().getDescription().getFullName());
            } catch (Throwable throwable) {
                reportCompatibilityFailure(item.plugin().getName(), throwable);
                logger.log(Level.SEVERE, "Failed to enable plugin " + item.plugin().getName(), throwable);
                rollbackFailedEnable(item, threadBaseline);
            }
        }
    }

    public void disableAll() {
        scheduler.stopAccepting();
        for (int index = loaded.size() - 1; index >= 0; index--) {
            LoadedPlugin item = loaded.get(index);
            JavaPlugin plugin = item.plugin();
            scheduler.cancelTasks(plugin);
            commands.unregister(plugin);
            if (plugin.isEnabled()) {
                try (CompatibilityRuntime.Scope ignored = CompatibilityRuntime.enter(plugin.getName())) { plugin.atlasSetEnabled(false); }
                catch (Throwable throwable) {
                    reportCompatibilityFailure(plugin.getName(), throwable);
                    logger.log(Level.SEVERE, "Failed to disable plugin " + plugin.getName(), throwable);
                }
            }
            pluginManager.unregisterPlugin(plugin);
        }
    }

    @Override
    public void close() {
        disableAll();
        for (LoadedPlugin item : loaded) {
            try { item.classLoader().close(); }
            catch (IOException exception) { logger.log(Level.WARNING, "Failed closing classloader for " + item.plugin().getName(), exception); }
        }
        loaded.clear();
    }

    public int loadedCount() { return loaded.size(); }
    public int discoveredCount() { return discoveredCount; }
    public List<LoadedPlugin> loadedPlugins() { return List.copyOf(loaded); }
    public List<String> failedEnableThreads(String plugin) {
        return failedEnableThreads.getOrDefault(key(plugin), List.of());
    }
    List<PluginThreadMonitor.ThreadDiagnostic> failedEnableThreadDiagnostics(String plugin) {
        return failedEnableThreadDiagnostics.getOrDefault(key(plugin), List.of());
    }
    private static String key(String value) { return value.toLowerCase(Locale.ROOT); }

    private static void reportCompatibilityFailure(String plugin, Throwable throwable) {
        try (CompatibilityRuntime.Scope ignored = CompatibilityRuntime.enter(plugin)) {
            for (Throwable current = throwable; current != null; current = current.getCause()) {
                if (current instanceof PluginBootstrapPhaseException phaseFailure) {
                    CompatibilityRuntime.availableLater(phaseFailure.api(), phaseFailure.phase());
                    return;
                }
            }
            CompatibilityRuntime.reportLinkageFailure(throwable);
        }
    }

    private void rollbackFailedEnable(LoadedPlugin item, PluginThreadMonitor.Snapshot threadBaseline) {
        JavaPlugin plugin = item.plugin();
        scheduler.cancelTasks(plugin);
        commands.unregister(plugin);
        pluginManager.cleanupPluginResources(plugin);
        List<PluginThreadMonitor.ThreadDiagnostic> diagnostics =
            threadMonitor.findNewLiveThreads(threadBaseline, plugin, item.classLoader());
        List<String> liveThreads = diagnostics.stream().map(PluginThreadMonitor.ThreadDiagnostic::name).toList();
        failedEnableThreadDiagnostics.put(key(plugin.getName()), diagnostics);
        failedEnableThreads.put(key(plugin.getName()), liveThreads);
        logger.info("[AtlasHybridIntegration] FAILED_ENABLE_ROLLBACK_OK plugin=" + plugin.getName());
        if (!diagnostics.isEmpty()) {
            StringBuilder report = new StringBuilder("[AtlasHybrid Plugin Resource]\n"
                + "Plugin: " + plugin.getName() + "\n"
                + "Lifecycle: FAILED_ENABLE\n"
                + "Status: ENABLE_FAILED_WITH_LIVE_THREADS\n"
                + "Live threads: " + diagnostics.size() + "\n"
                + "Observed: created during enable attempt");
            for (PluginThreadMonitor.ThreadDiagnostic diagnostic : diagnostics) {
                report.append("\n\nThread: ").append(diagnostic.name())
                    .append("\nDaemon: ").append(diagnostic.daemon())
                    .append("\nState: ").append(diagnostic.state())
                    .append("\nContext ClassLoader: ").append(diagnostic.contextClassLoader())
                    .append("\nOwnership confidence: ").append(diagnostic.ownershipConfidence())
                    .append("\nOwnership evidence: ").append(diagnostic.ownershipEvidence());
                if (!diagnostic.stackTrace().isEmpty()) {
                    report.append("\nStack:");
                    diagnostic.stackTrace().forEach(frame -> report.append("\n  at ").append(frame));
                }
            }
            logger.warning(report.toString());
        }
    }
}

package dev.atlashybrid.loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.plugin.Plugin;

final class PluginThreadMonitor {
    Snapshot capture() {
        Set<Thread> threads = Collections.newSetFromMap(new IdentityHashMap<>());
        threads.addAll(Thread.getAllStackTraces().keySet());
        return new Snapshot(threads);
    }

    List<ThreadDiagnostic> findNewLiveThreads(Snapshot before, Plugin plugin, AtlasPluginClassLoader pluginLoader) {
        String nameToken = plugin.getName().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        List<ThreadDiagnostic> result = new ArrayList<>();
        for (var entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            if (before.threads().contains(thread) || !thread.isAlive()) continue;
            boolean loaderOwned = thread.getContextClassLoader() == pluginLoader;
            boolean stackOwned = java.util.Arrays.stream(entry.getValue())
                .anyMatch(frame -> pluginLoader.hasLoadedClass(frame.getClassName()));
            boolean nameOwned = nameToken.length() >= 3
                && thread.getName().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT).contains(nameToken);
            OwnershipConfidence confidence;
            String evidence;
            if (loaderOwned) {
                confidence = OwnershipConfidence.HIGH;
                evidence = "context classloader belongs to plugin";
            } else if (stackOwned) {
                confidence = OwnershipConfidence.MEDIUM;
                evidence = "stack contains a class already loaded by the plugin classloader";
            } else if (nameOwned) {
                confidence = OwnershipConfidence.LOW;
                evidence = "thread name contains plugin name only";
            } else {
                continue;
            }
            ClassLoader contextLoader = thread.getContextClassLoader();
            result.add(new ThreadDiagnostic(
                thread.getName(),
                thread.isDaemon(),
                thread.getState(),
                contextLoader == null ? "bootstrap" : contextLoader.toString(),
                confidence,
                evidence,
                java.util.Arrays.stream(entry.getValue()).limit(12).map(StackTraceElement::toString).toList()
            ));
        }
        return result.stream().sorted(java.util.Comparator.comparing(ThreadDiagnostic::name)).toList();
    }

    record Snapshot(Set<Thread> threads) { }

    enum OwnershipConfidence { HIGH, MEDIUM, LOW }

    record ThreadDiagnostic(
        String name,
        boolean daemon,
        Thread.State state,
        String contextClassLoader,
        OwnershipConfidence ownershipConfidence,
        String ownershipEvidence,
        List<String> stackTrace
    ) { }
}

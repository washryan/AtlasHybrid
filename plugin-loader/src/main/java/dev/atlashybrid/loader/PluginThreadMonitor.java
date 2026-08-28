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

    List<String> findNewLiveThreads(Snapshot before, Plugin plugin, ClassLoader pluginLoader) {
        String nameToken = plugin.getName().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (before.threads().contains(thread) || !thread.isAlive()) continue;
            boolean loaderOwned = isDescendant(thread.getContextClassLoader(), pluginLoader);
            boolean nameOwned = nameToken.length() >= 3
                && thread.getName().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT).contains(nameToken);
            if (loaderOwned || nameOwned) result.add(thread.getName());
        }
        return result.stream().distinct().sorted().toList();
    }

    private static boolean isDescendant(ClassLoader candidate, ClassLoader expected) {
        for (ClassLoader current = candidate; current != null; current = current.getParent()) {
            if (current == expected) return true;
        }
        return false;
    }

    record Snapshot(Set<Thread> threads) { }
}

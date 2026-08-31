package dev.atlashybrid.loader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class DependencyResolver {
    public List<PluginCandidate> resolve(List<PluginCandidate> candidates) throws DependencyResolutionException {
        return resolve(candidates, new VirtualPluginDependencyRegistry(java.util.logging.Logger.getAnonymousLogger()));
    }

    public List<PluginCandidate> resolve(
        List<PluginCandidate> candidates,
        VirtualPluginDependencyRegistry virtualDependencies
    ) throws DependencyResolutionException {
        Map<String, PluginCandidate> byName = new LinkedHashMap<>();
        for (PluginCandidate candidate : candidates.stream().sorted(Comparator.comparing(item -> item.jar().getFileName().toString())).toList()) {
            String key = key(candidate.metadata().name());
            PluginCandidate duplicate = byName.putIfAbsent(key, candidate);
            if (duplicate != null) throw new DependencyResolutionException("Duplicate plugin name " + candidate.metadata().name() + ": " + duplicate.jar().getFileName() + " and " + candidate.jar().getFileName());
        }
        for (PluginCandidate candidate : candidates) {
            for (String dependency : candidate.metadata().depend()) {
                if (!byName.containsKey(key(dependency))
                    && virtualDependencies.findAvailable(dependency).isEmpty()) {
                    throw new DependencyResolutionException("Plugin " + candidate.metadata().name()
                        + " requires missing dependency " + dependency);
                }
            }
        }
        List<PluginCandidate> ordered = topological(byName, true);
        if (ordered.size() != candidates.size()) {
            ordered = topological(byName, false);
            if (ordered.size() != candidates.size()) throw new DependencyResolutionException("Hard dependency cycle among: " + unresolved(byName, ordered));
        }
        return ordered;
    }

    private static List<PluginCandidate> topological(Map<String, PluginCandidate> byName, boolean includeSoft) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Set<String>> outgoing = new HashMap<>();
        byName.keySet().forEach(name -> { indegree.put(name, 0); outgoing.put(name, new LinkedHashSet<>()); });
        for (Map.Entry<String, PluginCandidate> item : byName.entrySet()) {
            List<String> dependencies = new ArrayList<>(item.getValue().metadata().depend());
            if (includeSoft) dependencies.addAll(item.getValue().metadata().softDepend());
            for (String declared : dependencies) {
                String dependency = key(declared);
                if (!byName.containsKey(dependency) || dependency.equals(item.getKey())) continue;
                if (outgoing.get(dependency).add(item.getKey())) indegree.compute(item.getKey(), (ignored, value) -> value + 1);
            }
        }
        PriorityQueue<String> ready = new PriorityQueue<>();
        indegree.forEach((name, degree) -> { if (degree == 0) ready.add(name); });
        List<PluginCandidate> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            String name = ready.remove();
            result.add(byName.get(name));
            for (String dependent : outgoing.get(name)) if (indegree.compute(dependent, (ignored, value) -> value - 1) == 0) ready.add(dependent);
        }
        return result;
    }

    private static Set<String> unresolved(Map<String, PluginCandidate> all, List<PluginCandidate> resolved) { Set<String> names = new LinkedHashSet<>(all.keySet()); resolved.forEach(item -> names.remove(key(item.metadata().name()))); return names; }
    private static String key(String value) { return value.toLowerCase(Locale.ROOT); }
}

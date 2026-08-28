package dev.atlashybrid.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DependencyResolverTest {
    private final DependencyResolver resolver = new DependencyResolver();

    @Test
    void ordersHardDependenciesBeforeDependents() throws Exception {
        PluginCandidate base = candidate("Base", List.of());
        PluginCandidate child = candidate("Child", List.of("Base"));
        assertEquals(List.of(base, child), resolver.resolve(List.of(child, base)));
    }

    @Test
    void reportsHardDependencyCycles() {
        assertThrows(DependencyResolutionException.class, () -> resolver.resolve(List.of(candidate("A", List.of("B")), candidate("B", List.of("A")))));
    }

    private static PluginCandidate candidate(String name, List<String> depend) {
        return new PluginCandidate(Path.of(name + ".jar"), new PluginMetadata(name, "1", "example." + name, null, null, List.of(), depend, List.of(), Set.of(), java.util.Map.of()));
    }
}

package dev.atlashybrid.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
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

    @Test
    void virtualCapabilitySatisfiesHardDependencyWithoutCreatingOrderingNode() throws Exception {
        VirtualPluginDependencyRegistry capabilities = capabilities();
        capabilities.registerAvailable("LuckPerms", this, "5.4.46", "test");
        PluginCandidate probe = candidate("Probe", List.of("LuckPerms"));

        assertEquals(List.of(probe), resolver.resolve(List.of(probe), capabilities));
    }

    @Test
    void unavailableVirtualCapabilityDoesNotSatisfyHardDependency() {
        assertThrows(DependencyResolutionException.class, () -> resolver.resolve(
            List.of(candidate("Probe", List.of("LuckPerms"))), capabilities()));
    }

    @Test
    void realPluginTakesPrecedenceAndRetainsDependencyOrdering() throws Exception {
        VirtualPluginDependencyRegistry capabilities = capabilities();
        capabilities.registerAvailable("luckperms", this, "5.4.46", "test");
        PluginCandidate real = candidate("LuckPerms", List.of());
        PluginCandidate probe = candidate("Probe", List.of("LuckPerms"));

        assertEquals(List.of(real, probe), resolver.resolve(List.of(probe, real), capabilities));
    }

    @Test
    void missingSoftDependencyNeverBlocksLoading() throws Exception {
        PluginCandidate probe = candidate("Probe", List.of(), List.of("Missing"));
        assertEquals(List.of(probe), resolver.resolve(List.of(probe), capabilities()));
    }

    @Test
    void virtualDependenciesDoNotDestabilizeRealDependencyGraph() throws Exception {
        VirtualPluginDependencyRegistry capabilities = capabilities();
        capabilities.registerAvailable("External", this, "1", "test");
        PluginCandidate base = candidate("Base", List.of());
        PluginCandidate child = candidate("Child", List.of("Base", "External"));
        assertEquals(List.of(base, child), resolver.resolve(List.of(child, base), capabilities));
    }

    private static PluginCandidate candidate(String name, List<String> depend) {
        return candidate(name, depend, List.of());
    }

    private static PluginCandidate candidate(String name, List<String> depend, List<String> softDepend) {
        return new PluginCandidate(Path.of(name + ".jar"), new PluginMetadata(name, "1", "example." + name,
            null, null, List.of(), depend, softDepend, Set.of(), java.util.Map.of()));
    }

    private static VirtualPluginDependencyRegistry capabilities() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return new VirtualPluginDependencyRegistry(logger);
    }
}

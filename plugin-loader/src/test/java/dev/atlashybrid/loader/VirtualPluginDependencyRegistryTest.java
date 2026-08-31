package dev.atlashybrid.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class VirtualPluginDependencyRegistryTest {
    @Test void registrationIsCaseInsensitiveIdempotentAndCarriesDiagnostics() {
        VirtualPluginDependencyRegistry registry = registry();
        Object owner = new Object();
        VirtualPluginDependency first = registry.registerAvailable("LuckPerms", owner, "5.4.46", "Forge bridge");
        VirtualPluginDependency duplicate = registry.registerAvailable("luckperms", owner, "5.4.46", "Forge bridge");

        assertSame(first, duplicate);
        assertEquals(1, registry.size());
        assertEquals("5.4.46", registry.findAvailable("LUCKPERMS").orElseThrow().version());
        assertEquals(VirtualDependencyState.AVAILABLE, registry.state("LuckPerms"));
    }

    @Test void duplicateOwnerConflictIsRejected() {
        VirtualPluginDependencyRegistry registry = registry();
        registry.registerAvailable("LuckPerms", new Object(), "5.4.46", "first");
        assertThrows(IllegalStateException.class,
            () -> registry.registerAvailable("LuckPerms", new Object(), "5.4.46", "second"));
    }

    @Test void unregisterAndOwnerCleanupMakeCapabilitiesUnavailable() {
        VirtualPluginDependencyRegistry registry = registry();
        Object owner = new Object();
        Object other = new Object();
        registry.registerAvailable("LuckPerms", owner, "5.4.46", "test");
        registry.registerAvailable("Other", owner, "1", "test");
        registry.registerAvailable("Foreign", other, "1", "test");

        assertFalse(registry.unregister("LuckPerms", other));
        assertTrue(registry.unregister("LuckPerms", owner));
        assertEquals(VirtualDependencyState.UNAVAILABLE, registry.state("LuckPerms"));
        assertEquals(1, registry.unregisterAll(owner));
        assertEquals(1, registry.size());
    }

    private static VirtualPluginDependencyRegistry registry() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return new VirtualPluginDependencyRegistry(logger);
    }
}

package dev.atlashybrid.forge.compat.luckperms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import org.junit.jupiter.api.Test;

class LuckPermsForgePermissionBridgeTest {
    @Test void registrationUnregistrationAndRebindingHaveExplicitStates() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        PermissionProviderRegistry registry = new PermissionProviderRegistry(logger);
        AtomicBoolean available = new AtomicBoolean(true);
        AtomicReference<LuckPerms> api = new AtomicReference<>(api());
        LuckPermsForgePermissionBridge bridge = new LuckPermsForgePermissionBridge(registry, logger, () -> {
            if (!available.get()) throw new IllegalStateException("not loaded");
            return api.get();
        });

        assertEquals(LuckPermsBridgeState.DISCOVERED, bridge.state());
        bridge.refresh();
        assertEquals(LuckPermsBridgeState.BOUND, bridge.state());
        assertEquals(1, registry.size());
        bridge.refresh();
        assertEquals(1, registry.size());

        available.set(false);
        bridge.refresh();
        assertEquals(LuckPermsBridgeState.UNBOUND, bridge.state());
        assertEquals(0, registry.size());

        available.set(true);
        api.set(api());
        bridge.refresh();
        assertEquals(LuckPermsBridgeState.BOUND, bridge.state());
        assertEquals(1, registry.size());

        bridge.close();
        assertEquals(LuckPermsBridgeState.UNBOUND, bridge.state());
        assertEquals(0, registry.size());
    }

    @Test void unexpectedDiscoveryFailureLeavesAtlasFallbackActive() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        PermissionProviderRegistry registry = new PermissionProviderRegistry(logger);
        LuckPermsForgePermissionBridge bridge = new LuckPermsForgePermissionBridge(
            registry, logger, () -> { throw new IllegalArgumentException("broken API"); });

        bridge.refresh();
        assertEquals(LuckPermsBridgeState.FAILED, bridge.state());
        assertEquals(0, registry.size());
    }

    private static LuckPerms api() {
        return (LuckPerms) Proxy.newProxyInstance(
            LuckPerms.class.getClassLoader(), new Class<?>[] { LuckPerms.class },
            (object, method, args) -> null);
    }
}

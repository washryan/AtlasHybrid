package dev.atlashybrid.forge.compat.luckperms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.service.AtlasServicesManager;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.Test;

class LuckPermsForgePermissionBridgeTest {
    @Test void registrationUnregistrationAndRebindingHaveExplicitStates() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        PermissionProviderRegistry registry = new PermissionProviderRegistry(logger);
        AtlasServicesManager services = new AtlasServicesManager();
        Plugin owner = plugin();
        AtomicBoolean available = new AtomicBoolean(true);
        AtomicReference<LuckPerms> api = new AtomicReference<>(api());
        LuckPermsForgePermissionBridge bridge = new LuckPermsForgePermissionBridge(
            registry, services, owner, logger, () -> {
            if (!available.get()) throw new IllegalStateException("not loaded");
            return api.get();
        });

        assertEquals(LuckPermsBridgeState.DISCOVERED, bridge.state());
        bridge.refresh();
        assertEquals(LuckPermsBridgeState.BUKKIT_SERVICE_REGISTERED, bridge.state());
        assertEquals(1, registry.size());
        assertSame(api.get(), services.load(LuckPerms.class));
        assertSame(api.get(), services.getRegistration(LuckPerms.class).getProvider());
        assertSame(owner, services.getRegistration(LuckPerms.class).getPlugin());
        assertEquals(ServicePriority.Normal, services.getRegistration(LuckPerms.class).getPriority());
        assertEquals(1, services.getRegistrations(LuckPerms.class).size());
        bridge.refresh();
        assertEquals(1, registry.size());
        assertEquals(1, services.getRegistrations(LuckPerms.class).size());

        available.set(false);
        bridge.refresh();
        assertEquals(LuckPermsBridgeState.SERVICE_UNREGISTERED, bridge.state());
        assertEquals(0, registry.size());
        assertNull(services.load(LuckPerms.class));

        available.set(true);
        api.set(api());
        bridge.refresh();
        assertEquals(LuckPermsBridgeState.BUKKIT_SERVICE_REGISTERED, bridge.state());
        assertEquals(1, registry.size());
        assertSame(api.get(), services.load(LuckPerms.class));

        bridge.close();
        assertEquals(LuckPermsBridgeState.SERVICE_UNREGISTERED, bridge.state());
        assertEquals(0, registry.size());
        assertNull(services.load(LuckPerms.class));
    }

    @Test void unexpectedDiscoveryFailureLeavesAtlasFallbackActive() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        PermissionProviderRegistry registry = new PermissionProviderRegistry(logger);
        AtlasServicesManager services = new AtlasServicesManager();
        LuckPermsForgePermissionBridge bridge = new LuckPermsForgePermissionBridge(
            registry, services, plugin(), logger, () -> { throw new IllegalArgumentException("broken API"); });

        bridge.refresh();
        assertEquals(LuckPermsBridgeState.FAILED, bridge.state());
        assertEquals(0, registry.size());
        assertNull(services.load(LuckPerms.class));
    }

    @Test void ownerCleanupRemovesThePublicServiceAndRefreshRestoresItOnce() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        PermissionProviderRegistry registry = new PermissionProviderRegistry(logger);
        AtlasServicesManager services = new AtlasServicesManager();
        Plugin owner = plugin();
        LuckPerms api = api();
        LuckPermsForgePermissionBridge bridge = new LuckPermsForgePermissionBridge(
            registry, services, owner, logger, () -> api);

        bridge.refresh();
        services.unregisterAll(owner);
        assertNull(services.load(LuckPerms.class));
        bridge.refresh();

        assertSame(api, services.load(LuckPerms.class));
        assertEquals(1, services.getRegistrations(LuckPerms.class).size());
        assertEquals(1, registry.size());
    }

    private static LuckPerms api() {
        return (LuckPerms) Proxy.newProxyInstance(
            LuckPerms.class.getClassLoader(), new Class<?>[] { LuckPerms.class },
            (object, method, args) -> null);
    }

    private static Plugin plugin() {
        return (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(), new Class<?>[] { Plugin.class },
            (object, method, args) -> switch (method.getName()) {
                case "getName" -> "AtlasHybridCompatibility";
                case "isEnabled" -> true;
                case "toString" -> "AtlasHybridCompatibility";
                default -> null;
            });
    }
}

package dev.atlashybrid.forge.compat.luckperms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.service.AtlasServicesManager;
import dev.atlashybrid.loader.VirtualPluginDependencyRegistry;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.platform.PluginMetadata;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

class LuckPermsForgePermissionBridgeTest {
    @Test void registrationUnregistrationAndRebindingHaveExplicitStates() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        PermissionProviderRegistry registry = new PermissionProviderRegistry(logger);
        AtlasServicesManager services = new AtlasServicesManager();
        VirtualPluginDependencyRegistry capabilities = new VirtualPluginDependencyRegistry(logger);
        Plugin owner = plugin();
        AtomicBoolean available = new AtomicBoolean(true);
        AtomicReference<LuckPerms> api = new AtomicReference<>(api());
        LuckPermsForgePermissionBridge bridge = new LuckPermsForgePermissionBridge(
            registry, services, owner, capabilities, logger, () -> {
            if (!available.get()) throw new IllegalStateException("not loaded");
            return api.get();
        });

        assertEquals(LuckPermsBridgeState.DISCOVERED, bridge.state());
        bridge.refresh();
        assertEquals(LuckPermsBridgeState.VIRTUAL_DEPENDENCY_AVAILABLE, bridge.state());
        assertEquals(1, registry.size());
        assertSame(api.get(), services.load(LuckPerms.class));
        assertSame(api.get(), services.getRegistration(LuckPerms.class).getProvider());
        assertSame(owner, services.getRegistration(LuckPerms.class).getPlugin());
        assertEquals(ServicePriority.Normal, services.getRegistration(LuckPerms.class).getPriority());
        assertEquals(1, services.getRegistrations(LuckPerms.class).size());
        assertEquals(1, capabilities.size());
        bridge.refresh();
        assertEquals(1, registry.size());
        assertEquals(1, services.getRegistrations(LuckPerms.class).size());

        available.set(false);
        bridge.refresh();
        assertEquals(LuckPermsBridgeState.SERVICE_UNREGISTERED, bridge.state());
        assertEquals(0, registry.size());
        assertNull(services.load(LuckPerms.class));
        assertEquals(0, capabilities.size());

        available.set(true);
        api.set(api());
        bridge.refresh();
        assertEquals(LuckPermsBridgeState.VIRTUAL_DEPENDENCY_AVAILABLE, bridge.state());
        assertEquals(1, registry.size());
        assertSame(api.get(), services.load(LuckPerms.class));

        bridge.close();
        assertEquals(LuckPermsBridgeState.SERVICE_UNREGISTERED, bridge.state());
        assertEquals(0, registry.size());
        assertNull(services.load(LuckPerms.class));
        assertEquals(0, capabilities.size());
    }

    @Test void unexpectedDiscoveryFailureLeavesAtlasFallbackActive() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        PermissionProviderRegistry registry = new PermissionProviderRegistry(logger);
        AtlasServicesManager services = new AtlasServicesManager();
        VirtualPluginDependencyRegistry capabilities = new VirtualPluginDependencyRegistry(logger);
        LuckPermsForgePermissionBridge bridge = new LuckPermsForgePermissionBridge(
            registry, services, plugin(), capabilities, logger,
            () -> { throw new IllegalArgumentException("broken API"); });

        bridge.refresh();
        assertEquals(LuckPermsBridgeState.FAILED, bridge.state());
        assertEquals(0, registry.size());
        assertNull(services.load(LuckPerms.class));
        assertEquals(0, capabilities.size());
    }

    @Test void ownerCleanupRemovesThePublicServiceAndRefreshRestoresItOnce() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        PermissionProviderRegistry registry = new PermissionProviderRegistry(logger);
        AtlasServicesManager services = new AtlasServicesManager();
        VirtualPluginDependencyRegistry capabilities = new VirtualPluginDependencyRegistry(logger);
        Plugin owner = plugin();
        LuckPerms api = api();
        LuckPermsForgePermissionBridge bridge = new LuckPermsForgePermissionBridge(
            registry, services, owner, capabilities, logger, () -> api);

        bridge.refresh();
        services.unregisterAll(owner);
        assertNull(services.load(LuckPerms.class));
        bridge.refresh();

        assertSame(api, services.load(LuckPerms.class));
        assertEquals(1, services.getRegistrations(LuckPerms.class).size());
        assertEquals(1, registry.size());
        assertEquals(1, capabilities.size());
    }

    @Test void serviceRegistrationFailureNeverPublishesVirtualCapability() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        PermissionProviderRegistry registry = new PermissionProviderRegistry(logger);
        VirtualPluginDependencyRegistry capabilities = new VirtualPluginDependencyRegistry(logger);
        ServicesManager failingServices = (ServicesManager) Proxy.newProxyInstance(
            ServicesManager.class.getClassLoader(), new Class<?>[] { ServicesManager.class },
            (object, method, args) -> {
                if (method.getName().equals("register")) throw new IllegalStateException("service unavailable");
                if (method.getName().equals("getRegistrations")) return java.util.List.of();
                return null;
            });
        LuckPermsForgePermissionBridge bridge = new LuckPermsForgePermissionBridge(
            registry, failingServices, plugin(), capabilities, logger, LuckPermsForgePermissionBridgeTest::api);

        bridge.refresh();

        assertEquals(LuckPermsBridgeState.SERVICE_UNREGISTERED, bridge.state());
        assertEquals(0, registry.size());
        assertEquals(0, capabilities.size());
    }

    private static LuckPerms api() {
        return (LuckPerms) Proxy.newProxyInstance(
            LuckPerms.class.getClassLoader(), new Class<?>[] { LuckPerms.class },
            (object, method, args) -> method.getName().equals("getPluginMetadata") ? metadata() : null);
    }

    private static PluginMetadata metadata() {
        return (PluginMetadata) Proxy.newProxyInstance(
            PluginMetadata.class.getClassLoader(), new Class<?>[] { PluginMetadata.class },
            (object, method, args) -> method.getName().equals("getVersion") ? "5.4.46" : "5.4");
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

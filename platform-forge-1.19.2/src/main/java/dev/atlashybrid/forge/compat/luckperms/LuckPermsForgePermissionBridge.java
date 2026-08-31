package dev.atlashybrid.forge.compat.luckperms;

import dev.atlashybrid.runtime.permission.PermissionProviderPriority;
import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.service.AtlasServicesManager;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

public final class LuckPermsForgePermissionBridge implements AutoCloseable {
    @FunctionalInterface
    interface ApiLookup {
        LuckPerms get();
    }

    private static final String OWNER_NAME = "LuckPerms Forge permission bridge";

    private final PermissionProviderRegistry providers;
    private final AtlasServicesManager services;
    private final Plugin serviceOwner;
    private final Logger logger;
    private final ApiLookup apiLookup;
    private volatile LuckPermsBridgeState state = LuckPermsBridgeState.DISCOVERED;
    private LuckPerms boundApi;
    private LuckPermsPermissionProvider provider;
    private boolean failureLogged;

    public LuckPermsForgePermissionBridge(
        PermissionProviderRegistry providers,
        AtlasServicesManager services,
        Plugin serviceOwner,
        Logger logger
    ) {
        this(providers, services, serviceOwner, logger, LuckPermsProvider::get);
    }

    LuckPermsForgePermissionBridge(
        PermissionProviderRegistry providers,
        AtlasServicesManager services,
        Plugin serviceOwner,
        Logger logger,
        ApiLookup apiLookup
    ) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.services = Objects.requireNonNull(services, "services");
        this.serviceOwner = Objects.requireNonNull(serviceOwner, "serviceOwner");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.apiLookup = Objects.requireNonNull(apiLookup, "apiLookup");
        logger.info("[AtlasHybrid LuckPerms] LUCKPERMS_FORGE_DISCOVERED LuckPerms Forge detected");
    }

    public synchronized void refresh() {
        try {
            LuckPerms current = apiLookup.get();
            failureLogged = false;
            if (boundApi == current && provider != null && serviceRegistrationIsCurrent()) return;
            removeBindings();
            boundApi = current;
            provider = new LuckPermsPermissionProvider(current, logger);
            providers.registerSystem(OWNER_NAME, provider, PermissionProviderPriority.HIGHEST);
            state = LuckPermsBridgeState.PERMISSION_PROVIDER_BOUND;
            logger.info("[AtlasHybrid LuckPerms] LUCKPERMS_PERMISSION_PROVIDER_BOUND LuckPerms permission provider registered");
            services.register(LuckPerms.class, current, serviceOwner, ServicePriority.Normal);
            state = LuckPermsBridgeState.BUKKIT_SERVICE_REGISTERED;
            logger.info("[AtlasHybrid LuckPerms] LUCKPERMS_BUKKIT_SERVICE_REGISTERED public API registered with Bukkit ServicesManager");
        } catch (IllegalStateException notAvailableYet) {
            if (provider != null || boundApi != null) {
                removeBindings();
                state = LuckPermsBridgeState.SERVICE_UNREGISTERED;
                logger.info("[AtlasHybrid LuckPerms] LuckPerms permission provider and Bukkit service removed");
            }
        } catch (Throwable failure) {
            removeBindings();
            state = LuckPermsBridgeState.FAILED;
            if (!failureLogged) {
                failureLogged = true;
                logger.log(Level.WARNING,
                    "[AtlasHybrid LuckPerms] Public API bridge failed; Atlas fallback remains active", failure);
            }
        }
    }

    public LuckPermsBridgeState state() {
        return state;
    }

    @Override
    public synchronized void close() {
        boolean wasBound = provider != null || boundApi != null;
        removeBindings();
        state = LuckPermsBridgeState.SERVICE_UNREGISTERED;
        if (wasBound) logger.info("[AtlasHybrid LuckPerms] LuckPerms Bukkit service and permission provider removed");
    }

    private boolean serviceRegistrationIsCurrent() {
        return services.getRegistrations(LuckPerms.class).stream()
            .filter(registration -> registration.getPlugin() == serviceOwner)
            .filter(registration -> registration.getProvider() == boundApi)
            .count() == 1;
    }

    private void removeBindings() {
        if (boundApi != null) services.unregister(LuckPerms.class, boundApi);
        if (provider != null) providers.unregister(provider);
        provider = null;
        boundApi = null;
    }
}

package dev.atlashybrid.forge.compat.luckperms;

import dev.atlashybrid.runtime.permission.PermissionProviderPriority;
import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

public final class LuckPermsForgePermissionBridge implements AutoCloseable {
    @FunctionalInterface
    interface ApiLookup {
        LuckPerms get();
    }

    private static final String OWNER_NAME = "LuckPerms Forge permission bridge";

    private final PermissionProviderRegistry providers;
    private final Logger logger;
    private final ApiLookup apiLookup;
    private volatile LuckPermsBridgeState state = LuckPermsBridgeState.DISCOVERED;
    private LuckPerms boundApi;
    private LuckPermsPermissionProvider provider;
    private boolean failureLogged;

    public LuckPermsForgePermissionBridge(PermissionProviderRegistry providers, Logger logger) {
        this(providers, logger, LuckPermsProvider::get);
    }

    LuckPermsForgePermissionBridge(
        PermissionProviderRegistry providers,
        Logger logger,
        ApiLookup apiLookup
    ) {
        this.providers = Objects.requireNonNull(providers, "providers");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.apiLookup = Objects.requireNonNull(apiLookup, "apiLookup");
        logger.info("[AtlasHybrid LuckPerms] LuckPerms Forge detected");
    }

    public synchronized void refresh() {
        try {
            LuckPerms current = apiLookup.get();
            failureLogged = false;
            if (boundApi == current && provider != null) return;
            removeProvider();
            boundApi = current;
            provider = new LuckPermsPermissionProvider(current, logger);
            providers.registerSystem(OWNER_NAME, provider, PermissionProviderPriority.HIGHEST);
            state = LuckPermsBridgeState.BOUND;
            logger.info("[AtlasHybrid LuckPerms] LuckPerms permission provider registered");
        } catch (IllegalStateException notAvailableYet) {
            if (provider != null) {
                removeProvider();
                state = LuckPermsBridgeState.UNBOUND;
                logger.info("[AtlasHybrid LuckPerms] LuckPerms permission provider removed");
            }
        } catch (Throwable failure) {
            removeProvider();
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
        boolean wasBound = provider != null;
        removeProvider();
        state = LuckPermsBridgeState.UNBOUND;
        if (wasBound) logger.info("[AtlasHybrid LuckPerms] LuckPerms permission provider removed");
    }

    private void removeProvider() {
        if (provider != null) providers.unregister(provider);
        provider = null;
        boundApi = null;
    }
}

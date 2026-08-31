package dev.atlashybrid.forge.compat.luckperms;

import dev.atlashybrid.runtime.permission.PermissionProvider;
import dev.atlashybrid.runtime.permission.PermissionSubject;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;

final class LuckPermsPermissionProvider implements PermissionProvider {
    @FunctionalInterface
    interface PermissionLookup {
        Tristate query(UUID uniqueId, String permission);
    }

    private final PermissionLookup lookup;
    private final Logger logger;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    LuckPermsPermissionProvider(LuckPerms luckPerms, Logger logger) {
        this((uniqueId, permission) -> queryPublicApi(luckPerms, uniqueId, permission), logger);
    }

    LuckPermsPermissionProvider(PermissionLookup lookup, Logger logger) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public Optional<Boolean> query(PermissionSubject subject, String permission) {
        if (subject.type() != PermissionSubject.Type.PLAYER || subject.uniqueId() == null) {
            return Optional.empty();
        }
        try {
            Tristate result = lookup.query(subject.uniqueId(), permission);
            failureLogged.set(false);
            if (result == Tristate.TRUE) return Optional.of(true);
            if (result == Tristate.FALSE) return Optional.of(false);
            return Optional.empty();
        } catch (Throwable failure) {
            if (failureLogged.compareAndSet(false, true)) {
                logger.log(Level.WARNING,
                    "[AtlasHybrid LuckPerms] Permission query failed; Atlas fallback remains active", failure);
            }
            return Optional.empty();
        }
    }

    private static Tristate queryPublicApi(LuckPerms luckPerms, UUID uniqueId, String permission) {
        User user = luckPerms.getUserManager().getUser(uniqueId);
        if (user == null) return Tristate.UNDEFINED;
        Optional<QueryOptions> queryOptions = luckPerms.getContextManager().getQueryOptions(user);
        if (queryOptions.isEmpty()) return Tristate.UNDEFINED;
        return user.getCachedData().getPermissionData(queryOptions.get()).checkPermission(permission);
    }
}

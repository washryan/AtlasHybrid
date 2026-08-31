package dev.atlashybrid.forge.compat.luckperms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.atlashybrid.runtime.permission.PermissionSubject;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.context.ContextManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import org.junit.jupiter.api.Test;

class LuckPermsPermissionProviderTest {
    private final Logger logger = quietLogger();
    private final UUID uniqueId = UUID.randomUUID();
    private final PermissionSubject player =
        new PermissionSubject("Player", uniqueId, PermissionSubject.Type.PLAYER, false);

    @Test void mapsTrueFalseAndUndefinedWithoutCaching() {
        AtomicReference<Tristate> result = new AtomicReference<>(Tristate.TRUE);
        LuckPermsPermissionProvider provider = new LuckPermsPermissionProvider(
            (ignored, node) -> result.get(), logger);

        assertEquals(Optional.of(true), provider.query(player, "atlas.bridge.test"));
        result.set(Tristate.FALSE);
        assertEquals(Optional.of(false), provider.query(player, "atlas.bridge.test"));
        result.set(Tristate.UNDEFINED);
        assertEquals(Optional.empty(), provider.query(player, "atlas.bridge.test"));
    }

    @Test void unsupportedSubjectsAbstainWithoutCallingLuckPerms() {
        AtomicInteger calls = new AtomicInteger();
        LuckPermsPermissionProvider provider = new LuckPermsPermissionProvider(
            (ignored, node) -> { calls.incrementAndGet(); return Tristate.TRUE; }, logger);
        PermissionSubject console = new PermissionSubject(
            "Console", null, PermissionSubject.Type.CONSOLE, true);

        assertEquals(Optional.empty(), provider.query(console, "atlas.bridge.test"));
        assertEquals(0, calls.get());
    }

    @Test void queryFailuresAreIsolatedAndLoggedOnceUntilRecovery() {
        AtomicReference<RuntimeException> failure = new AtomicReference<>(new IllegalArgumentException("boom"));
        AtomicInteger warnings = new AtomicInteger();
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { if (record.getThrown() != null) warnings.incrementAndGet(); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        LuckPermsPermissionProvider provider = new LuckPermsPermissionProvider((ignored, node) -> {
            if (failure.get() != null) throw failure.get();
            return Tristate.TRUE;
        }, logger);

        assertEquals(Optional.empty(), provider.query(player, "atlas.bridge.test"));
        assertEquals(Optional.empty(), provider.query(player, "atlas.bridge.test"));
        assertEquals(1, warnings.get());
        failure.set(null);
        assertEquals(Optional.of(true), provider.query(player, "atlas.bridge.test"));
    }

    @Test void publicApiPathAbstainsWhenUserIsNotLoaded() {
        LuckPerms api = api(null, Optional.empty(), new AtomicReference<>(Tristate.TRUE), new AtomicInteger());
        LuckPermsPermissionProvider provider = new LuckPermsPermissionProvider(api, logger);
        assertEquals(Optional.empty(), provider.query(player, "atlas.bridge.test"));
    }

    @Test void publicApiPathDoesNotFabricateContextWhenQueryOptionsAreUnavailable() {
        User user = proxy(User.class, (object, method, args) -> defaultValue(method.getReturnType()));
        LuckPerms api = api(user, Optional.empty(), new AtomicReference<>(Tristate.TRUE), new AtomicInteger());
        LuckPermsPermissionProvider provider = new LuckPermsPermissionProvider(api, logger);
        assertEquals(Optional.empty(), provider.query(player, "atlas.bridge.test"));
    }

    @Test void publicApiPathUsesFreshContextualQueryOptionsOnEveryCheck() {
        QueryOptions options = proxy(QueryOptions.class, (object, method, args) -> defaultValue(method.getReturnType()));
        AtomicReference<Tristate> result = new AtomicReference<>(Tristate.TRUE);
        AtomicInteger contextQueries = new AtomicInteger();
        User user = proxy(User.class, (object, method, args) -> defaultValue(method.getReturnType()));
        LuckPerms api = api(user, Optional.of(options), result, contextQueries);
        LuckPermsPermissionProvider provider = new LuckPermsPermissionProvider(api, logger);

        assertEquals(Optional.of(true), provider.query(player, "atlas.bridge.context"));
        result.set(Tristate.FALSE);
        assertEquals(Optional.of(false), provider.query(player, "atlas.bridge.context"));
        assertEquals(2, contextQueries.get());
    }

    private LuckPerms api(
        User suppliedUser,
        Optional<QueryOptions> options,
        AtomicReference<Tristate> result,
        AtomicInteger contextQueries
    ) {
        CachedPermissionData permissionData = proxy(CachedPermissionData.class, (object, method, args) ->
            method.getName().equals("checkPermission") ? result.get() : defaultValue(method.getReturnType()));
        CachedDataManager cachedData = proxy(CachedDataManager.class, (object, method, args) ->
            method.getName().equals("getPermissionData") ? permissionData : defaultValue(method.getReturnType()));
        User user = suppliedUser == null ? null : proxy(User.class, (object, method, args) -> {
            if (method.getName().equals("getCachedData")) return cachedData;
            if (method.getName().equals("getUniqueId")) return uniqueId;
            return defaultValue(method.getReturnType());
        });
        UserManager users = proxy(UserManager.class, (object, method, args) ->
            method.getName().equals("getUser") ? user : defaultValue(method.getReturnType()));
        ContextManager contexts = proxy(ContextManager.class, (object, method, args) -> {
            if (method.getName().equals("getQueryOptions")) {
                contextQueries.incrementAndGet();
                return options;
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(LuckPerms.class, (object, method, args) -> {
            if (method.getName().equals("getUserManager")) return users;
            if (method.getName().equals("getContextManager")) return contexts;
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }
}

package dev.atlashybrid.forge;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Narrow bridge between the version-pinned login Mixins and the runtime. */
public final class LoginAdmissionBridge {
    private static final Logger LOGGER = Logger.getLogger("AtlasHybrid");
    private static final Map<Connection, String> HOSTNAMES =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile AdmissionHandler handler;

    private LoginAdmissionBridge() { }

    public static void install(AdmissionHandler admissionHandler) {
        handler = Objects.requireNonNull(admissionHandler, "admissionHandler");
    }

    public static void clear() {
        handler = null;
        HOSTNAMES.clear();
    }

    public static void captureHostname(Connection connection, String hostname) {
        if (connection != null) HOSTNAMES.put(connection, Objects.requireNonNullElse(hostname, ""));
    }

    public static AdmissionResult admit(Connection connection, ServerPlayer player) {
        String hostname = HOSTNAMES.remove(connection);
        AdmissionHandler current = handler;
        if (current == null) {
            return AdmissionResult.denied("AtlasHybrid login admission is not available");
        }
        SocketAddress remote = connection == null ? null : connection.getRemoteAddress();
        if (!(remote instanceof InetSocketAddress socketAddress) || socketAddress.getAddress() == null) {
            return AdmissionResult.denied("AtlasHybrid could not determine the remote address");
        }
        try {
            return Objects.requireNonNull(
                current.admit(player, Objects.requireNonNullElse(hostname, ""), socketAddress.getAddress()),
                "admission handler returned null");
        } catch (Throwable throwable) {
            LOGGER.log(Level.SEVERE, "[AtlasHybrid Connection] PlayerLoginEvent admission failed closed", throwable);
            return AdmissionResult.denied("AtlasHybrid login admission failed");
        }
    }

    public static void abort(ServerPlayer player) {
        AdmissionHandler current = handler;
        if (current == null || player == null) return;
        try {
            current.abort(player);
        } catch (Throwable throwable) {
            LOGGER.log(Level.WARNING, "[AtlasHybrid Connection] Login adapter cleanup failed", throwable);
        }
    }

    @FunctionalInterface
    public interface AdmissionHandler {
        AdmissionResult admit(ServerPlayer player, String hostname, InetAddress address);

        default void abort(ServerPlayer player) { }
    }

    public record AdmissionResult(boolean allowed, Component message) {
        public AdmissionResult {
            Objects.requireNonNull(message, "message");
        }

        public static AdmissionResult permit() {
            return new AdmissionResult(true, Component.empty());
        }

        public static AdmissionResult denied(String message) {
            return new AdmissionResult(false, Component.literal(Objects.requireNonNullElse(message, "")));
        }
    }
}

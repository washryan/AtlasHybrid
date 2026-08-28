package dev.atlashybrid.diagnostics;

import java.time.Instant;
import java.util.Objects;

public final class CompatibilityRuntime {
    private static final ThreadLocal<String> CURRENT_PLUGIN = new ThreadLocal<>();
    private static volatile CompatibilityCollector collector;

    private CompatibilityRuntime() {
    }

    public static void install(CompatibilityCollector value) {
        collector = Objects.requireNonNull(value, "value");
    }

    public static void clear() {
        collector = null;
        CURRENT_PLUGIN.remove();
    }

    public static Scope enter(String plugin) {
        String previous = CURRENT_PLUGIN.get();
        CURRENT_PLUGIN.set(Objects.requireNonNull(plugin, "plugin"));
        return () -> {
            if (previous == null) CURRENT_PLUGIN.remove();
            else CURRENT_PLUGIN.set(previous);
        };
    }

    public static CompatibilityException unsupported(String api, String module, CompatibilityStatus status) {
        CompatibilityCollector current = collector;
        String plugin = Objects.requireNonNullElse(CURRENT_PLUGIN.get(), "UNKNOWN");
        if (current != null) return current.unsupported(plugin, api, module, status);
        return new CompatibilityException(new CompatibilityDiagnostic(Instant.now(), plugin, api, module, status, "Diagnostics collector is not installed"));
    }

    public static boolean reportLinkageFailure(Throwable throwable) {
        Throwable linkage = findLinkageFailure(throwable);
        CompatibilityCollector current = collector;
        if (linkage == null || current == null) return false;
        current.missing(Objects.requireNonNullElse(CURRENT_PLUGIN.get(), "UNKNOWN"), symbol(linkage), linkage);
        return true;
    }

    private static Throwable findLinkageFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof NoSuchMethodError || current instanceof AbstractMethodError
                || current instanceof NoClassDefFoundError || current instanceof ClassNotFoundException) return current;
        }
        return null;
    }

    private static String symbol(Throwable failure) {
        String message = Objects.requireNonNullElse(failure.getMessage(), failure.getClass().getName()).replace('/', '.');
        if (failure instanceof NoSuchMethodError || failure instanceof AbstractMethodError) {
            message = message.replace("'", "");
            int open = message.indexOf('(');
            int dot = open < 0 ? -1 : message.lastIndexOf('.', open);
            int space = dot < 0 ? -1 : message.lastIndexOf(' ', dot);
            if (dot >= 0) message = message.substring(space + 1, dot) + "#" + message.substring(dot + 1);
        }
        return message;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}

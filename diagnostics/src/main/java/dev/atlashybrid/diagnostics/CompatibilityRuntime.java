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

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override void close();
    }
}

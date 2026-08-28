package dev.atlashybrid.diagnostics;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

public final class CompatibilityCollector {
    private final Logger logger;
    private final String runtimeVersion;
    private final List<CompatibilityDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
    private final Map<String, LongAdder> supportedCalls = new ConcurrentHashMap<>();

    public CompatibilityCollector(Logger logger) {
        this(logger, "UNKNOWN");
    }

    public CompatibilityCollector(Logger logger, String runtimeVersion) {
        this.logger = logger;
        this.runtimeVersion = runtimeVersion;
    }

    public void supported(String plugin) {
        supportedCalls.computeIfAbsent(plugin, ignored -> new LongAdder()).increment();
    }

    public CompatibilityException unsupported(String plugin, String api, String module, CompatibilityStatus status) {
        CompatibilityDiagnostic diagnostic = new CompatibilityDiagnostic(Instant.now(), plugin, api, module, status, "");
        diagnostics.add(diagnostic);
        logger.warning(diagnostic.format());
        return new CompatibilityException(diagnostic);
    }

    public void missing(String plugin, String api, Throwable failure) {
        CompatibilityDiagnostic diagnostic = new CompatibilityDiagnostic(
            Instant.now(), plugin, api, "runtime-linkage", CompatibilityStatus.NOT_IMPLEMENTED, failure.getClass().getName());
        diagnostics.add(diagnostic);
        logger.warning("[AtlasHybrid Compatibility]\n"
            + "Plugin: " + plugin + "\n"
            + "Missing API: " + api + "\n"
            + "Status: NOT_IMPLEMENTED\n"
            + "Runtime: " + runtimeVersion);
    }

    public List<CompatibilityDiagnostic> diagnostics() { return List.copyOf(diagnostics); }
    public long supportedCalls(String plugin) { LongAdder value = supportedCalls.get(plugin); return value == null ? 0 : value.sum(); }
    public long unsupportedCalls(String plugin) { return diagnostics.stream().filter(item -> item.plugin().equals(plugin)).count(); }
    public long totalUnsupportedCalls() { return diagnostics.size(); }
}

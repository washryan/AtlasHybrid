package dev.atlashybrid.diagnostics;

import java.time.Instant;
import java.util.Objects;

public record CompatibilityDiagnostic(
    Instant timestamp,
    String plugin,
    String api,
    String module,
    CompatibilityStatus status,
    String detail
) {
    public CompatibilityDiagnostic {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(api, "api");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(status, "status");
        detail = Objects.requireNonNullElse(detail, "");
    }

    public String format() {
        return "[AtlasHybrid Compatibility]\n"
            + "Plugin: " + plugin + "\n"
            + "Unsupported API: " + api + "\n"
            + "Module: " + module + "\n"
            + "Status: " + status + (detail.isBlank() ? "" : "\nDetail: " + detail);
    }
}

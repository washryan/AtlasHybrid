package org.bukkit.plugin.java;

import java.util.Objects;

/** Raised when a plugin API is used before the loader has reached its required phase. */
public final class PluginBootstrapPhaseException extends IllegalStateException {
    private final String api;
    private final String phase;

    public PluginBootstrapPhaseException(String api, String phase) {
        super("[AtlasHybrid Compatibility]\n"
            + "API: " + Objects.requireNonNull(api, "api") + "\n"
            + "Diagnostic: PLUGIN_BOOTSTRAP_PHASE\n"
            + "Phase: " + Objects.requireNonNull(phase, "phase") + "\n"
            + "Status: AVAILABLE_LATER");
        this.api = api;
        this.phase = phase;
    }

    public String api() { return api; }
    public String phase() { return phase; }
    public String status() { return "AVAILABLE_LATER"; }
}

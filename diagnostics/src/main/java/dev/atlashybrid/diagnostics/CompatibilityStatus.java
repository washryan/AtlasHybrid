package dev.atlashybrid.diagnostics;

public enum CompatibilityStatus {
    SUPPORTED,
    NOT_IMPLEMENTED,
    NMS_NOT_SUPPORTED,
    PAPER_API_NOT_SUPPORTED,
    MISSING_DEPENDENCY,
    DEPENDENCY_CYCLE,
    PROTECTED_NAMESPACE,
    WRONG_THREAD,
    PLUGIN_FAILURE
}

package dev.atlashybrid.diagnostics;

public final class CompatibilityException extends UnsupportedOperationException {
    private final CompatibilityDiagnostic diagnostic;

    public CompatibilityException(CompatibilityDiagnostic diagnostic) {
        super(diagnostic.format());
        this.diagnostic = diagnostic;
    }

    public CompatibilityDiagnostic diagnostic() {
        return diagnostic;
    }
}

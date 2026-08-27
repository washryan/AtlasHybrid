package dev.atlashybrid.loader;

public final class InvalidPluginMetadataException extends Exception {
    public InvalidPluginMetadataException(String message) { super(message); }
    public InvalidPluginMetadataException(String message, Throwable cause) { super(message, cause); }
}

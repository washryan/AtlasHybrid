package dev.atlashybrid.loader;

import java.nio.file.Path;

public record PluginCandidate(Path jar, PluginMetadata metadata) {
}

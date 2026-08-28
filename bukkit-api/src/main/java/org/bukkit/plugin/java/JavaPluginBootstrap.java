package org.bukkit.plugin.java;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.PluginDescriptionFile;

/**
 * AtlasHybrid's construction-time bridge between a plugin classloader and
 * {@link JavaPlugin}. This is runtime infrastructure, not a Bukkit plugin API.
 */
public final class JavaPluginBootstrap {
    private JavaPluginBootstrap() {
    }

    public record Context(
        Server server,
        PluginDescriptionFile description,
        File dataFolder,
        Logger logger
    ) {
        public Context {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(dataFolder, "dataFolder");
            Objects.requireNonNull(logger, "logger");
        }
    }

    /** Implemented by an AtlasHybrid-owned plugin classloader. */
    public interface Provider {
        Context atlasBootstrapContext(ClassLoader requestingClassLoader);
    }

    static Context findFor(ClassLoader requestingClassLoader) {
        for (ClassLoader current = requestingClassLoader; current != null; current = current.getParent()) {
            if (current instanceof Provider provider) {
                return provider.atlasBootstrapContext(requestingClassLoader);
            }
        }
        return null;
    }
}

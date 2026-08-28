package atlashybrid.bootstrapfixture;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConstructorProbe extends JavaPlugin {
    public final Logger earlyLogger = getLogger();
    public final String earlyName = getName();
    public final PluginDescriptionFile earlyDescription = getDescription();
    public final File earlyDataFolder = getDataFolder();
    public final Server earlyServer = getServer();
    public final FileConfiguration earlyConfig = getConfig();
    public final boolean earlyEnabled = isEnabled();
}

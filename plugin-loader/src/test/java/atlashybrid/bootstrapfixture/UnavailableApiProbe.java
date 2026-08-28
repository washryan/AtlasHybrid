package atlashybrid.bootstrapfixture;

import org.bukkit.plugin.java.JavaPlugin;

public final class UnavailableApiProbe extends JavaPlugin {
    public UnavailableApiProbe() {
        getCommand("not-registered-yet");
    }
}

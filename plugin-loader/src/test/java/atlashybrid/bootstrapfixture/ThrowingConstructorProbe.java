package atlashybrid.bootstrapfixture;

import org.bukkit.plugin.java.JavaPlugin;

public final class ThrowingConstructorProbe extends JavaPlugin {
    public ThrowingConstructorProbe() {
        getLogger();
        throw new IllegalStateException("expected constructor failure");
    }
}

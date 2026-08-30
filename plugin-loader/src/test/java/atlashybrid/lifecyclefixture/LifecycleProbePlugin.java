package atlashybrid.lifecyclefixture;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class LifecycleProbePlugin extends JavaPlugin implements Listener {
    public final List<String> enableEvents = new ArrayList<>();
    public final List<String> disableEvents = new ArrayList<>();
    public final List<Boolean> enableStates = new ArrayList<>();
    public final List<Boolean> disableStates = new ArrayList<>();
    public int enableCalls;
    public int disableCalls;

    @Override
    public void onEnable() {
        enableCalls++;
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        disableCalls++;
        if (!disableEvents.contains(getName())) {
            throw new IllegalStateException("Own PluginDisableEvent was not observed before onDisable");
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        enableEvents.add(event.getPlugin().getName());
        enableStates.add(event.getPlugin().isEnabled());
        if (getName().equals("ObserverProbe") && event.getPlugin().getName().equals("TargetProbe")) {
            throw new IllegalStateException("expected lifecycle observer failure");
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        disableEvents.add(event.getPlugin().getName());
        disableStates.add(event.getPlugin().isEnabled());
    }
}

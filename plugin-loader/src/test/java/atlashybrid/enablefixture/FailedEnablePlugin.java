package atlashybrid.enablefixture;

import dev.atlashybrid.runtime.permission.AtlasPermissions;
import dev.atlashybrid.runtime.permission.PermissionProviderPriority;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class FailedEnablePlugin extends JavaPlugin {
    public int disableCalls;
    public int enableEventCalls;
    public int disableEventCalls;
    public Thread worker;

    @Override
    public void onEnable() {
        Listener listener = new Listener() { };
        getServer().getPluginManager().registerEvent(FailureEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> { }, this, false);
        getServer().getPluginManager().registerEvent(PluginEnableEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> enableEventCalls++, this, false);
        getServer().getPluginManager().registerEvent(PluginDisableEvent.class, listener, EventPriority.NORMAL,
            (registered, event) -> disableEventCalls++, this, false);
        getServer().getScheduler().runTaskLater(this, () -> { }, 100);
        getServer().getServicesManager().register(Runnable.class, () -> { }, this, ServicePriority.Normal);
        AtlasPermissions.providers().register(this, (subject, permission) -> java.util.Optional.of(true),
            PermissionProviderPriority.NORMAL);
        getServer().getConsoleSender().addAttachment(this, "atlas.failed-enable", true);

        worker = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) Thread.sleep(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "FailedEnableProbe-worker");
        worker.setContextClassLoader(getClass().getClassLoader());
        worker.setDaemon(true);
        worker.start();
        throw new IllegalStateException("expected enable failure");
    }

    @Override
    public void onDisable() {
        disableCalls++;
    }

    public void stopWorker() throws InterruptedException {
        if (worker == null) return;
        worker.interrupt();
        worker.join(5000L);
    }

    public static final class FailureEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }
}

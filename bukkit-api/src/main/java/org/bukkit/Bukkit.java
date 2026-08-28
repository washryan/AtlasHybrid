package org.bukkit;

import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

public final class Bukkit {
    private static volatile Server server;

    private Bukkit() {
    }

    public static Server getServer() {
        return Objects.requireNonNull(server, "AtlasHybrid server is not initialized");
    }

    public static void setServer(Server value) {
        if (server != null && value != null && server != value) {
            throw new IllegalStateException("Bukkit server is already initialized");
        }
        server = value;
    }

    public static PluginManager getPluginManager() {
        return getServer().getPluginManager();
    }

    public static BukkitScheduler getScheduler() {
        return getServer().getScheduler();
    }

    public static Logger getLogger() {
        return getServer().getLogger();
    }

    public static String getVersion() {
        return getServer().getVersion();
    }

    public static String getBukkitVersion() {
        return getServer().getBukkitVersion();
    }

    public static World getWorld(String name) {
        return getServer().getWorld(name);
    }
}

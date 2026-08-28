package org.bukkit.plugin.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaPluginLifecycleTest {
    @TempDir Path temp;

    @Test
    void invokesLifecycleExactlyOncePerTransition() {
        CountingPlugin plugin = new CountingPlugin();
        plugin.atlasInitialize(new EmptyServer(), new PluginDescriptionFile("Test", "1", "example.Test", null, null, List.of(), List.of(), List.of(), Set.of()), temp.toFile(), Logger.getAnonymousLogger());
        plugin.onLoad();
        plugin.atlasSetEnabled(true);
        plugin.atlasSetEnabled(true);
        plugin.atlasSetEnabled(false);
        plugin.atlasSetEnabled(false);
        assertEquals(List.of("load", "enable", "disable"), plugin.calls);
    }

    @Test
    void saveConfigPersistsCurrentConfiguration() throws Exception {
        CountingPlugin plugin = new CountingPlugin();
        plugin.atlasInitialize(new EmptyServer(), new PluginDescriptionFile("Test", "1", "example.Test", null, null, List.of(), List.of(), List.of(), Set.of()), temp.toFile(), Logger.getAnonymousLogger());
        plugin.getConfig().set("warplist", List.of("home"));
        plugin.saveConfig();
        assertTrue(Files.readString(temp.resolve("config.yml")).contains("home"));
        plugin.reloadConfig();
        assertEquals(List.of("home"), plugin.getConfig().getStringList("warplist"));
    }

    private static final class CountingPlugin extends JavaPlugin {
        private final java.util.ArrayList<String> calls = new java.util.ArrayList<>();
        @Override public void onLoad() { calls.add("load"); }
        @Override public void onEnable() { calls.add("enable"); }
        @Override public void onDisable() { calls.add("disable"); }
    }

    private static final class EmptyServer implements Server {
        @Override public String getName() { return "test"; }
        @Override public String getVersion() { return "test"; }
        @Override public String getBukkitVersion() { return "test"; }
        @Override public String getMinecraftVersion() { return "1.19.2"; }
        @Override public String getForgeVersion() { return "43"; }
        @Override public String getAtlasHybridVersion() { return "test"; }
        @Override public int getDetectedModCount() { return 0; }
        @Override public PluginManager getPluginManager() { return null; }
        @Override public org.bukkit.plugin.ServicesManager getServicesManager() { return null; }
        @Override public org.bukkit.command.ConsoleCommandSender getConsoleSender() { return null; }
        @Override public BukkitScheduler getScheduler() { return null; }
        @Override public PluginCommand getPluginCommand(String name) { return null; }
        @Override public World getWorld(String name) { return null; }
        @Override public Logger getLogger() { return Logger.getAnonymousLogger(); }
    }
}

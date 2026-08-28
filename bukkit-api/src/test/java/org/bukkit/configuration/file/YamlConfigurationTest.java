package org.bukkit.configuration.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlConfigurationTest {
    @TempDir Path temp;

    @Test
    void loadsMissingAndEmptyFilesAsBlankConfigurations() throws Exception {
        assertTrue(YamlConfiguration.loadConfiguration(temp.resolve("missing.yml").toFile()).getKeys(false).isEmpty());
        Path empty = temp.resolve("empty.yml");
        Files.writeString(empty, "", StandardCharsets.UTF_8);
        assertTrue(YamlConfiguration.loadConfiguration(empty.toFile()).getKeys(false).isEmpty());
    }

    @Test
    void loadsUtf8ScalarsNestedPathsListsAndSections() throws Exception {
        Path path = temp.resolve("typed.yml");
        Files.writeString(path, """
            server: são-paulo
            enabled: true
            count: 12
            ratio: 2.5
            data:
              address: localhost
              nested:
                value: "olá mundo"
            names:
              - alpha
              - beta
            """, StandardCharsets.UTF_8);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(path.toFile());
        assertEquals("são-paulo", config.getString("server"));
        assertTrue(config.getBoolean("enabled"));
        assertEquals(12, config.getInt("count"));
        assertEquals(2.5D, config.getDouble("ratio"));
        assertEquals("localhost", config.getString("data.address"));
        assertEquals("olá mundo", config.getString("data.nested.value"));
        assertEquals(List.of("alpha", "beta"), config.getStringList("names"));
        assertEquals(Set.of("address", "nested"), config.getConfigurationSection("data").getKeys(false));
        assertEquals(Set.of("address", "nested", "nested.value"), config.getConfigurationSection("data").getKeys(true));
    }

    @Test
    void supportsReaderOverloadAndNullSemantics() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new StringReader("present: value\nempty: null\n"));
        assertEquals("value", config.getString("present"));
        assertFalse(config.isSet("empty"));
        config.set("present", null);
        assertFalse(config.contains("present"));
        assertThrows(NullPointerException.class, () -> YamlConfiguration.loadConfiguration((File) null));
        assertThrows(NullPointerException.class, () -> YamlConfiguration.loadConfiguration((java.io.Reader) null));
    }

    @Test
    void invalidYamlIsLoggedAndReturnsBlankConfiguration() throws Exception {
        Path path = temp.resolve("invalid.yml");
        Files.writeString(path, "not a mapping\n", StandardCharsets.UTF_8);
        assertTrue(YamlConfiguration.loadConfiguration(path.toFile()).getKeys(false).isEmpty());
    }

    @Test
    void rejectsUnsafeTagsAnchorsAliasesAndMergeKeys() {
        assertTrue(YamlConfiguration.loadConfiguration(new StringReader("value: !!java/object:example.Type {}\n")).getKeys(false).isEmpty());
        assertTrue(YamlConfiguration.loadConfiguration(new StringReader("!java/object:example.Type key: value\n")).getKeys(false).isEmpty());
        assertTrue(YamlConfiguration.loadConfiguration(new StringReader("value: &anchor data\ncopy: *anchor\n")).getKeys(false).isEmpty());
        assertTrue(YamlConfiguration.loadConfiguration(new StringReader("<<: *defaults\n")).getKeys(false).isEmpty());
    }

    @Test
    void saveModifyReloadRoundTripPreservesSemanticValues() {
        Path path = temp.resolve("roundtrip.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage-method", "h2");
        config.set("data.address", "localhost:3306");
        config.set("data.pool.maximum", 10);
        config.set("features", List.of("one", "dois"));
        YamlConfiguration.saveConfiguration(config, path);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(path.toFile());
        assertEquals("h2", loaded.getString("storage-method"));
        assertEquals("localhost:3306", loaded.getString("data.address"));
        assertEquals(10, loaded.getInt("data.pool.maximum"));
        assertEquals(List.of("one", "dois"), loaded.getStringList("features"));
        assertNotNull(loaded.createSection("new.section"));
        loaded.set("new.section.value", false);
        YamlConfiguration.saveConfiguration(loaded, path);
        assertFalse(YamlConfiguration.loadConfiguration(path).getBoolean("new.section.value", true));
    }

    @Test
    void persistsMutableStringListsAndTypedLocations() {
        World world = () -> "world";
        Bukkit.setServer(new ConfigServer(world));
        try {
            Path path = temp.resolve("config.yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(path);
            config.set("warplist", List.of("home"));
            config.set("warps.home", new Location(world, 12.5, 70.0, -4.25, 90.0F, 15.0F));
            YamlConfiguration.saveConfiguration(config, path);

            FileConfiguration loaded = YamlConfiguration.loadConfiguration(path);
            assertEquals(List.of("home"), loaded.getStringList("warplist"));
            assertTrue(loaded.isSet("warps.home"));
            Location location = loaded.getLocation("warps.home");
            assertNotNull(location);
            assertEquals("world", location.getWorld().getName());
            assertEquals(12.5, location.getX());
            assertEquals(70.0, location.getY());
            assertEquals(-4.25, location.getZ());

            loaded.getStringList("warplist").clear();
            assertEquals(List.of("home"), loaded.getStringList("warplist"));
            loaded.set("warps.home", null);
            assertFalse(loaded.isSet("warps.home"));
            assertNull(loaded.getLocation("warps.home"));
        } finally {
            Bukkit.setServer(null);
        }
    }

    @Test
    void exposesRequiredLegacyColorCodes() {
        assertEquals("\u00a7c", ChatColor.RED.toString());
        assertEquals("\u00a7a", ChatColor.GREEN.toString());
        assertEquals("\u00a7e", ChatColor.YELLOW.toString());
    }

    private record ConfigServer(World world) implements Server {
        @Override public String getName() { return "test"; }
        @Override public String getVersion() { return "test"; }
        @Override public String getBukkitVersion() { return "test"; }
        @Override public String getMinecraftVersion() { return "1.19.2"; }
        @Override public String getForgeVersion() { return "43.5.0"; }
        @Override public String getAtlasHybridVersion() { return "0.1.0-alpha"; }
        @Override public int getDetectedModCount() { return 0; }
        @Override public PluginManager getPluginManager() { return null; }
        @Override public org.bukkit.plugin.ServicesManager getServicesManager() { return null; }
        @Override public org.bukkit.command.ConsoleCommandSender getConsoleSender() { return null; }
        @Override public BukkitScheduler getScheduler() { return null; }
        @Override public PluginCommand getPluginCommand(String name) { return null; }
        @Override public World getWorld(String name) { return world.getName().equals(name) ? world : null; }
        @Override public Logger getLogger() { return Logger.getAnonymousLogger(); }
    }
}

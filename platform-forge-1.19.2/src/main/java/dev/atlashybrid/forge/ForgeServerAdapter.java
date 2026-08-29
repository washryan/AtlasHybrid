package dev.atlashybrid.forge;

import dev.atlashybrid.runtime.command.CommandRegistry;
import dev.atlashybrid.runtime.AtlasUnsafeValues;
import dev.atlashybrid.runtime.event.AtlasPluginManager;
import dev.atlashybrid.runtime.scheduler.AtlasScheduler;
import dev.atlashybrid.runtime.permission.AtlasPermissionRegistry;
import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.player.PlayerSessionRegistry;
import dev.atlashybrid.runtime.service.AtlasServicesManager;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;

final class ForgeServerAdapter implements Server {
    private final MinecraftServer minecraftServer;
    private final AtlasPluginManager pluginManager;
    private final AtlasScheduler scheduler;
    private final CommandRegistry commands;
    private final AtlasPermissionRegistry permissions;
    private final PermissionProviderRegistry providers;
    private final AtlasServicesManager services;
    private final PlayerSessionRegistry players = new PlayerSessionRegistry(player -> ((ForgePlayerAdapter) player).close());
    private final ForgeConsoleCommandSender console;
    private final UnsafeValues unsafeValues = new AtlasUnsafeValues(AtlasUnsafeValues.MINECRAFT_1_19_2_DATA_VERSION);

    ForgeServerAdapter(MinecraftServer minecraftServer, AtlasPluginManager pluginManager, AtlasScheduler scheduler, CommandRegistry commands,
                       AtlasPermissionRegistry permissions, PermissionProviderRegistry providers, AtlasServicesManager services) {
        this.minecraftServer = minecraftServer;
        this.pluginManager = pluginManager;
        this.scheduler = scheduler;
        this.commands = commands;
        this.permissions = permissions;
        this.providers = providers;
        this.services = services;
        this.console = new ForgeConsoleCommandSender(minecraftServer, permissions, providers);
    }

    @Override public String getName() { return "AtlasHybrid"; }
    @Override public String getVersion() { return "AtlasHybrid " + AtlasHybridMod.VERSION + " (MC: " + getMinecraftVersion() + ", Forge: " + getForgeVersion() + ")"; }
    @Override public Collection<? extends Player> getOnlinePlayers() { return players.onlinePlayers(); }
    @Override public Player getPlayer(UUID id) { return players.getPlayer(id); }
    @Override public Player getPlayerExact(String name) { return players.getPlayerExact(name); }
    @Override public String getBukkitVersion() { return "1.19.2-R0.1-ATLASHYBRID"; }
    @Override public String getMinecraftVersion() { return minecraftServer.getServerVersion(); }
    @Override public String getForgeVersion() { return ForgeVersion.getVersion(); }
    @Override public String getAtlasHybridVersion() { return AtlasHybridMod.VERSION; }
    @Override public UnsafeValues getUnsafe() { return unsafeValues; }
    @Override public boolean getOnlineMode() { return minecraftServer.usesAuthentication(); }
    @Override public int getDetectedModCount() { return ModList.get().size(); }
    @Override public PluginManager getPluginManager() { return pluginManager; }
    @Override public ServicesManager getServicesManager() { return services; }
    @Override public ConsoleCommandSender getConsoleSender() { return console; }
    @Override public BukkitScheduler getScheduler() { return scheduler; }
    @Override public PluginCommand getPluginCommand(String name) { return commands.get(name); }
    @Override public World getWorld(String name) {
        for (ServerLevel level : minecraftServer.getAllLevels()) {
            String bukkitName = worldName(level);
            if (bukkitName.equalsIgnoreCase(name) || level.dimension().location().toString().equalsIgnoreCase(name)) {
                return new ForgeWorldAdapter(level, bukkitName);
            }
        }
        return null;
    }
    @Override public Logger getLogger() { return Logger.getLogger("AtlasHybrid"); }

    void initializePermissions() { console.initializePermissions(); }

    ForgePlayerAdapter player(net.minecraft.server.level.ServerPlayer player) {
        return (ForgePlayerAdapter) players.getOrRegister(player.getUUID(), player.getGameProfile().getName(), () -> {
            ForgePlayerAdapter adapter = new ForgePlayerAdapter(player, permissions, providers);
            adapter.initializePermissions();
            return adapter;
        });
    }

    void disconnect(net.minecraft.server.level.ServerPlayer player) {
        players.remove(player.getUUID());
    }

    int permissionProviderCount() { return providers.size(); }
    int serviceCount() { return services.size(); }

    void close() {
        players.clear();
        console.close();
    }

    String worldName(ServerLevel level) {
        String root = minecraftServer.getWorldData().getLevelName();
        if (level.dimension() == Level.OVERWORLD) return root;
        if (level.dimension() == Level.NETHER) return root + "_nether";
        if (level.dimension() == Level.END) return root + "_the_end";
        return level.dimension().location().toString();
    }
}

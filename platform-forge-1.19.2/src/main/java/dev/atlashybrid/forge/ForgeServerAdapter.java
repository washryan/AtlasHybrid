package dev.atlashybrid.forge;

import dev.atlashybrid.runtime.command.CommandRegistry;
import dev.atlashybrid.runtime.event.AtlasPluginManager;
import dev.atlashybrid.runtime.scheduler.AtlasScheduler;
import java.util.logging.Logger;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.versions.forge.ForgeVersion;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

final class ForgeServerAdapter implements Server {
    private final MinecraftServer minecraftServer;
    private final AtlasPluginManager pluginManager;
    private final AtlasScheduler scheduler;
    private final CommandRegistry commands;

    ForgeServerAdapter(MinecraftServer minecraftServer, AtlasPluginManager pluginManager, AtlasScheduler scheduler, CommandRegistry commands) {
        this.minecraftServer = minecraftServer;
        this.pluginManager = pluginManager;
        this.scheduler = scheduler;
        this.commands = commands;
    }

    @Override public String getName() { return "AtlasHybrid"; }
    @Override public String getVersion() { return "AtlasHybrid " + AtlasHybridMod.VERSION + " (MC: " + getMinecraftVersion() + ", Forge: " + getForgeVersion() + ")"; }
    @Override public String getBukkitVersion() { return "1.19.2-R0.1-ATLASHYBRID"; }
    @Override public String getMinecraftVersion() { return minecraftServer.getServerVersion(); }
    @Override public String getForgeVersion() { return ForgeVersion.getVersion(); }
    @Override public String getAtlasHybridVersion() { return AtlasHybridMod.VERSION; }
    @Override public int getDetectedModCount() { return ModList.get().size(); }
    @Override public PluginManager getPluginManager() { return pluginManager; }
    @Override public BukkitScheduler getScheduler() { return scheduler; }
    @Override public PluginCommand getPluginCommand(String name) { return commands.get(name); }
    @Override public Logger getLogger() { return Logger.getLogger("AtlasHybrid"); }
}

package org.bukkit;

import java.util.Collection;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;

public interface Server {
    String getName();

    String getVersion();

    Collection<? extends Player> getOnlinePlayers();

    Player getPlayer(UUID id);

    Player getPlayerExact(String name);

    String getBukkitVersion();

    String getMinecraftVersion();

    String getForgeVersion();

    String getAtlasHybridVersion();

    int getDetectedModCount();

    PluginManager getPluginManager();

    ServicesManager getServicesManager();

    ConsoleCommandSender getConsoleSender();

    BukkitScheduler getScheduler();

    PluginCommand getPluginCommand(String name);

    World getWorld(String name);

    Logger getLogger();
}

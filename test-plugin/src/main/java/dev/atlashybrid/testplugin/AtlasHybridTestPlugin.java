package dev.atlashybrid.testplugin;

import java.util.Arrays;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class AtlasHybridTestPlugin extends JavaPlugin implements Listener, CommandExecutor {
    @Override
    public void onLoad() {
        getLogger().info("[AtlasHybridTestPlugin] onLoad");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        if (getCommand("atlas") == null) {
            throw new IllegalStateException("Atlas command was not created from plugin.yml");
        }
        getCommand("atlas").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        long delay = getConfig().getInt("scheduler-delay-ticks", 20);
        getServer().getScheduler().runTaskLater(this,
            () -> getLogger().info("[AtlasHybridTestPlugin] delayed scheduler task executed"), delay);
        getServer().getScheduler().runTask(this,
            () -> getLogger().info("[AtlasHybridTestPlugin] immediate scheduler task executed"));
        getLogger().info("[AtlasHybridTestPlugin] onEnable");
    }

    @Override
    public void onDisable() {
        getLogger().info("[AtlasHybridTestPlugin] onDisable");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("AtlasHybrid is running.");
            return true;
        }
        if (args.length == 1 && "info".equalsIgnoreCase(args[0])) {
            sender.sendMessage("Minecraft version: " + getServer().getMinecraftVersion());
            sender.sendMessage("Forge version: " + getServer().getForgeVersion());
            sender.sendMessage("AtlasHybrid version: " + getServer().getAtlasHybridVersion());
            sender.sendMessage("Plugins loaded: " + getServer().getPluginManager().getPlugins().length);
            sender.sendMessage("Mods detected: " + getServer().getDetectedModCount());
            return true;
        }
        sender.sendMessage("Usage: /atlas [info]; got " + Arrays.toString(args));
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        getLogger().info("[AtlasHybridTestPlugin] PlayerJoinEvent: " + event.getPlayer().getName());
        Location before = event.getPlayer().getLocation();
        Location target = new Location(before.getWorld(), before.getX() + 0.25D, before.getY(), before.getZ(), before.getYaw(), before.getPitch());
        boolean teleported = event.getPlayer().teleport(target);
        Location after = event.getPlayer().getLocation();
        boolean positionMatches = Math.abs(after.getX() - target.getX()) < 0.001D
            && Math.abs(after.getY() - target.getY()) < 0.001D
            && Math.abs(after.getZ() - target.getZ()) < 0.001D;
        getLogger().info("[AtlasHybridTestPlugin] Location/teleport bridge: teleported=" + teleported
            + " positionMatches=" + positionMatches
            + " target=" + target.getX() + "," + target.getY() + "," + target.getZ()
            + " actual=" + after.getX() + "," + after.getY() + "," + after.getZ());
        try {
            event.getPlayer().getDisplayName();
        } catch (UnsupportedOperationException expected) {
            getLogger().info("[AtlasHybridTestPlugin] unsupported API diagnostic observed");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        getLogger().info("[AtlasHybridTestPlugin] PlayerQuitEvent: " + event.getPlayer().getName());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        getLogger().info("[AtlasHybridTestPlugin] BlockBreakEvent: " + event.getBlock().getType());
        if (getConfig().getBoolean("cancel-block-break", false)) {
            event.setCancelled(true);
            getLogger().info("[AtlasHybridTestPlugin] BlockBreakEvent cancelled by config");
        }
    }
}

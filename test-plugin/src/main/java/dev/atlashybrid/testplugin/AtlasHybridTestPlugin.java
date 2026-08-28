package dev.atlashybrid.testplugin;

import dev.atlashybrid.runtime.permission.AtlasPermissions;
import dev.atlashybrid.runtime.permission.PermissionProviderPriority;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.ServicePriority;

public final class AtlasHybridTestPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final java.util.logging.Logger earlyLogger = getLogger();
    private org.bukkit.entity.Player sessionPlayer;

    @Override
    public void onLoad() {
        if (earlyLogger != getLogger()) {
            throw new IllegalStateException("JavaPlugin logger identity changed after construction");
        }
        earlyLogger.info("[AtlasHybridIntegration] EARLY_LOGGER_OK");
        getLogger().info("[AtlasHybridTestPlugin] onLoad");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration loadedFromFile = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        if (loadedFromFile.getInt("scheduler-delay-ticks", -1) < 0
            || !loadedFromFile.isSet("cancel-block-break")) {
            throw new IllegalStateException("YAML file loading proof failed");
        }
        getLogger().info("[AtlasHybridIntegration] YAML_LOAD_OK");
        if (getCommand("atlas") == null) {
            throw new IllegalStateException("Atlas command was not created from plugin.yml");
        }
        getCommand("atlas").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        registerPermissionProof();
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
        if (args.length == 2 && "permission".equalsIgnoreCase(args[0])) {
            boolean value = sender.hasPermission(args[1]);
            sender.sendMessage("Permission " + args[1] + ": " + value);
            return value;
        }
        sender.sendMessage("Usage: /atlas [info|permission <node>]; got " + Arrays.toString(args));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("alpha", "beta", "gamma") : List.of();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sessionPlayer = event.getPlayer();
        getLogger().info("[AtlasHybridTestPlugin] PlayerJoinEvent: " + event.getPlayer().getName());
        PermissionAttachment attachment = event.getPlayer().addAttachment(this, "atlas.test.attachment", true);
        boolean attachmentTrue = event.getPlayer().hasPermission("ATLAS.TEST.ATTACHMENT");
        attachment.setPermission("atlas.test.attachment", false);
        boolean attachmentFalse = !event.getPlayer().hasPermission("atlas.test.attachment");
        attachment.remove();
        boolean provider = event.getPlayer().hasPermission("atlas.test.provider");
        boolean console = getServer().getConsoleSender().hasPermission("atlas.test.console");
        boolean service = getServer().getServicesManager().load(PermissionProofService.class) != null;
        if (!attachmentTrue || !attachmentFalse || !provider || !console || !service) {
            throw new IllegalStateException("Permission integration proof failed");
        }
        getLogger().info("[AtlasHybridPermissionProof] attachmentTrue=" + attachmentTrue
            + " attachmentFalse=" + attachmentFalse + " provider=" + provider
            + " console=" + console + " service=" + service);
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
        getLogger().info("[AtlasHybridPermissionProof] quitIdentityStable=" + (event.getPlayer() == sessionPlayer));
        getLogger().info("[AtlasHybridTestPlugin] PlayerQuitEvent: " + event.getPlayer().getName());
        sessionPlayer = null;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        getLogger().info("[AtlasHybridPermissionProof] blockIdentityStable=" + (event.getPlayer() == sessionPlayer));
        getLogger().info("[AtlasHybridTestPlugin] BlockBreakEvent: " + event.getBlock().getType());
        if (getConfig().getBoolean("cancel-block-break", false)) {
            event.setCancelled(true);
            getLogger().info("[AtlasHybridTestPlugin] BlockBreakEvent cancelled by config");
        }
    }

    private void registerPermissionProof() {
        getServer().getPluginManager().addPermission(new Permission("atlas.test.console", PermissionDefault.OP));
        AtlasPermissions.providers().register(this,
            (subject, node) -> node.equals("atlas.test.provider") ? Optional.of(true) : Optional.empty(),
            PermissionProviderPriority.NORMAL);
        getServer().getServicesManager().register(PermissionProofService.class,
            () -> "ready", this, ServicePriority.Normal);
    }

    @FunctionalInterface
    public interface PermissionProofService {
        String status();
    }
}

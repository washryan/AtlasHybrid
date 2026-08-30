package dev.atlashybrid.testplugin;

import dev.atlashybrid.runtime.permission.AtlasPermissions;
import dev.atlashybrid.runtime.permission.PermissionProviderPriority;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.ServicePriority;

public final class AtlasHybridTestPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final java.util.logging.Logger earlyLogger = getLogger();
    private org.bukkit.entity.Player sessionPlayer;
    private final java.util.concurrent.ConcurrentMap<String, java.util.List<String>> loginOrder =
        new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger localServerCommands = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger remoteServerCommands = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger playerCommands = new java.util.concurrent.atomic.AtomicInteger();

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
        if (org.bukkit.Bukkit.getUnsafe() == null
            || getServer().getUnsafe() == null
            || org.bukkit.Bukkit.getUnsafe() != getServer().getUnsafe()
            || org.bukkit.Bukkit.getUnsafe().getDataVersion() != 3120) {
            throw new IllegalStateException("UnsafeValues integration proof failed");
        }
        getLogger().info("[AtlasHybridIntegration] UNSAFE_VALUES_OK");
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
        java.util.concurrent.atomic.AtomicInteger executorCalls = new java.util.concurrent.atomic.AtomicInteger();
        Listener executorListener = new Listener() { };
        getServer().getPluginManager().registerEvent(ExecutorProbeEvent.class, executorListener,
            EventPriority.NORMAL, (registered, event) -> executorCalls.incrementAndGet(), this, false);
        getServer().getPluginManager().callEvent(new ExecutorProbeEvent());
        if (executorCalls.get() != 1) {
            throw new IllegalStateException("EventExecutor integration proof failed");
        }
        getLogger().info("[AtlasHybridIntegration] EVENT_EXECUTOR_OK");
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
        if (args.length == 1 && "server-original".equals(args[0])) {
            throw new IllegalStateException("Original local server command executed after mutation");
        }
        if (args.length == 1 && "server-mutated".equals(args[0])) {
            if (localServerCommands.get() != 1 || sender != getServer().getConsoleSender()) {
                throw new IllegalStateException("Mutated local server command sender/count mismatch");
            }
            sender.sendMessage("local server mutation executed");
            return true;
        }
        if (args.length == 1 && "server-cancelled".equals(args[0])) {
            throw new IllegalStateException("Cancelled local server command executed");
        }
        if (args.length == 1 && "remote-original".equals(args[0])) {
            throw new IllegalStateException("Original remote server command executed after mutation");
        }
        if (args.length == 1 && "remote-mutated".equals(args[0])) {
            if (remoteServerCommands.get() != 1
                || !(sender instanceof org.bukkit.command.RemoteConsoleCommandSender)) {
                throw new IllegalStateException("Mutated remote server command sender/count mismatch");
            }
            sender.sendMessage("remote server mutation executed");
            return true;
        }
        if (args.length == 1 && "player-original".equals(args[0])) {
            throw new IllegalStateException("Original player command executed after mutation");
        }
        if (args.length == 1 && "player-mutated".equals(args[0])) {
            if (playerCommands.get() != 1 || sender != sessionPlayer) {
                throw new IllegalStateException("Mutated player command sender/count mismatch");
            }
            sender.sendMessage("player command mutation executed");
            return true;
        }
        if (args.length == 1 && "player-cancelled".equals(args[0])) {
            throw new IllegalStateException("Cancelled player command executed");
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
        if (event.getPlayer().getName().equals("AtlasLoginDenied")) {
            throw new IllegalStateException("Denied PlayerLoginEvent reached PlayerJoinEvent");
        }
        if (event.getPlayer().getName().equals("AtlasAllowed")) {
            appendLoginStage("AtlasAllowed", "PlayerJoin");
            requireLoginOrder("AtlasAllowed", java.util.List.of("AsyncPreLogin", "PlayerLogin", "PlayerJoin"));
        }
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

    @EventHandler(priority = EventPriority.LOW)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!event.getName().equals("AtlasDenied") && !event.getName().equals("AtlasAllowed")
            && !event.getName().equals("AtlasLoginDenied")) return;
        java.util.UUID expected = java.util.UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + event.getName()).getBytes(StandardCharsets.UTF_8));
        if (!event.isAsynchronous()
            || Thread.currentThread().getName().equals("Server thread")
            || !event.getAddress().isLoopbackAddress()
            || !expected.equals(event.getUniqueId())) {
            throw new IllegalStateException("Async pre-login connection data/thread contract mismatch");
        }
        if (event.getName().equals("AtlasDenied")) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "AtlasHybrid integration deny");
        } else {
            appendLoginStage(event.getName(), "AsyncPreLogin");
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "temporary");
            event.allow();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        String name = event.getPlayer().getName();
        if (!name.equals("AtlasAllowed") && !name.equals("AtlasLoginDenied")) return;
        java.util.UUID expected = java.util.UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        if (event.isAsynchronous()
            || !Thread.currentThread().getName().equals("Server thread")
            || !event.getAddress().isLoopbackAddress()
            || !event.getAddress().equals(event.getRealAddress())
            || !event.getHostname().equals("localhost:25565")
            || !expected.equals(event.getPlayer().getUniqueId())
            || getServer().getPlayer(expected) != null
            || getServer().getPlayerExact(name) != null
            || getServer().getOnlinePlayers().stream().anyMatch(player -> player.getUniqueId().equals(expected))) {
            throw new IllegalStateException("PlayerLoginEvent connection/transient-state contract mismatch");
        }
        appendLoginStage(name, "PlayerLogin");
        requireLoginOrder(name, java.util.List.of("AsyncPreLogin", "PlayerLogin"));
        if (name.equals("AtlasLoginDenied")) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "AtlasHybrid PlayerLoginEvent deny");
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
        if (event.getBlock().getType() != Material.STONE) {
            throw new IllegalStateException("Expected Material.STONE but got " + event.getBlock().getType());
        }
        getLogger().info("[AtlasHybridIntegration] MATERIAL_API_OK");
        getLogger().info("[AtlasHybridTestPlugin] BlockBreakEvent: " + event.getBlock().getType());
        if (getConfig().getBoolean("cancel-block-break", false)) {
            event.setCancelled(true);
            getLogger().info("[AtlasHybridTestPlugin] BlockBreakEvent cancelled by config");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        if (event instanceof RemoteServerCommandEvent) {
            throw new IllegalStateException("Remote command dispatched through local ServerCommandEvent handlers");
        }
        if (!event.getCommand().equals("atlas server-original")
            && !event.getCommand().equals("atlas server-cancelled")) return;
        if (event.getSender() != getServer().getConsoleSender()
            || !Thread.currentThread().getName().equals("Server thread")) {
            throw new IllegalStateException("Local ServerCommandEvent sender/thread mismatch");
        }
        int calls = localServerCommands.incrementAndGet();
        if (calls > 2) throw new IllegalStateException("Duplicate local ServerCommandEvent dispatch");
        if (event.getCommand().equals("atlas server-original")) {
            if (calls != 1) throw new IllegalStateException("Local mutation command order mismatch");
            event.setCommand("atlas server-mutated");
        } else {
            if (calls != 2) throw new IllegalStateException("Local cancellation command order mismatch");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRemoteServerCommand(RemoteServerCommandEvent event) {
        if (!event.getCommand().equals("atlas remote-original")) return;
        if (!(event.getSender() instanceof org.bukkit.command.RemoteConsoleCommandSender)
            || !Thread.currentThread().getName().equals("Server thread")) {
            throw new IllegalStateException("RemoteServerCommandEvent sender/thread mismatch");
        }
        if (remoteServerCommands.incrementAndGet() != 1) {
            throw new IllegalStateException("Duplicate RemoteServerCommandEvent dispatch");
        }
        event.setCommand("atlas remote-mutated");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!event.getMessage().equals("/atlas player-original")
            && !event.getMessage().equals("/atlas player-cancelled")) return;
        if (event.getPlayer() != sessionPlayer
            || !Thread.currentThread().getName().equals("Server thread")) {
            throw new IllegalStateException("PlayerCommandPreprocessEvent sender/thread mismatch");
        }
        int calls = playerCommands.incrementAndGet();
        if (calls > 2) throw new IllegalStateException("Duplicate PlayerCommandPreprocessEvent dispatch");
        if (event.getMessage().equals("/atlas player-original")) {
            if (calls != 1) throw new IllegalStateException("Player mutation command order mismatch");
            event.setMessage("/atlas player-mutated");
        } else {
            if (calls != 2) throw new IllegalStateException("Player cancellation command order mismatch");
            event.setCancelled(true);
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

    private void appendLoginStage(String name, String stage) {
        java.util.List<String> order = loginOrder.computeIfAbsent(name,
            ignored -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()));
        synchronized (order) {
            if (order.contains(stage)) throw new IllegalStateException("Duplicate login lifecycle stage: " + name + " " + stage);
            order.add(stage);
        }
    }

    private void requireLoginOrder(String name, java.util.List<String> expected) {
        java.util.List<String> order = loginOrder.get(name);
        synchronized (order) {
            if (!java.util.List.copyOf(order).equals(expected)) {
                throw new IllegalStateException("Login lifecycle order mismatch for " + name + ": " + order);
            }
        }
    }

    @FunctionalInterface
    public interface PermissionProofService {
        String status();
    }

    public static final class ExecutorProbeEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }
}

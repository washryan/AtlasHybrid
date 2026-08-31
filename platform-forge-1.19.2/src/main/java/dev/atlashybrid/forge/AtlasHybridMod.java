package dev.atlashybrid.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.atlashybrid.diagnostics.CompatibilityCollector;
import dev.atlashybrid.diagnostics.CompatibilityRuntime;
import dev.atlashybrid.forge.compat.luckperms.LuckPermsForgePermissionBridge;
import dev.atlashybrid.forge.compat.AtlasCompatibilityServiceOwner;
import dev.atlashybrid.loader.DependencyResolutionException;
import dev.atlashybrid.loader.PluginRuntime;
import dev.atlashybrid.runtime.command.CommandRegistry;
import dev.atlashybrid.runtime.event.AtlasPluginManager;
import dev.atlashybrid.runtime.scheduler.AtlasScheduler;
import dev.atlashybrid.runtime.permission.AtlasPermissionRegistry;
import dev.atlashybrid.runtime.permission.AtlasPermissions;
import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.service.AtlasServicesManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerNegotiationEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.bukkit.Bukkit;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;

@Mod(AtlasHybridMod.MOD_ID)
public final class AtlasHybridMod {
    public static final String MOD_ID = "atlashybrid";
    public static final String VERSION = "0.1.0-alpha";
    private static final Logger LOGGER = Logger.getLogger("AtlasHybrid");

    private AtlasPluginManager pluginManager;
    private AtlasScheduler scheduler;
    private CommandRegistry commands;
    private ForgeServerAdapter serverAdapter;
    private PluginRuntime pluginRuntime;
    private CompatibilityCollector compatibilityCollector;
    private AtlasPermissionRegistry permissionRegistry;
    private PermissionProviderRegistry permissionProviders;
    private AtlasServicesManager servicesManager;
    private LuckPermsForgePermissionBridge luckPermsBridge;
    private AtlasCompatibilityServiceOwner compatibilityServiceOwner;

    public AtlasHybridMod() {
        configureLogging();
        logStartupBanner();
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("[AtlasHybrid] Runtime mod constructed; waiting for dedicated server lifecycle");
    }

    private static void logStartupBanner() {
        LOGGER.info(System.lineSeparator()
            + "     _   _   _                _   _       _          _     _" + System.lineSeparator()
            + "    / \\ | |_| | __ _ ___    | | | |_   _| |__  _ __(_) __| |" + System.lineSeparator()
            + "   / _ \\| __| |/ _` / __|   | |_| | | | | '_ \\| '__| |/ _` |" + System.lineSeparator()
            + "  / ___ \\ |_| | (_| \\__ \\   |  _  | |_| | |_) | |  | | (_| |" + System.lineSeparator()
            + " /_/   \\_\\__|_|\\__,_|___/   |_| |_|\\__, |_.__/|_|  |_|\\__,_|" + System.lineSeparator()
            + "                                      |___/" + System.lineSeparator()
            + "  0.1.0-alpha | Minecraft 1.19.2 | Forge 43.5.0" + System.lineSeparator()
            + "  Experimental Forge + Bukkit compatibility subset" + System.lineSeparator()
            + "  Independent clean-room server runtime");
    }

    private static void configureLogging() {
        for (java.util.logging.Handler handler : LOGGER.getHandlers()) LOGGER.removeHandler(handler);
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(java.util.logging.Level.ALL);
        Log4jForwardingHandler forwarding = new Log4jForwardingHandler();
        forwarding.setLevel(java.util.logging.Level.ALL);
        LOGGER.addHandler(forwarding);
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        permissionRegistry = new AtlasPermissionRegistry();
        permissionProviders = new PermissionProviderRegistry(LOGGER);
        servicesManager = new AtlasServicesManager();
        AtlasPermissions.install(permissionProviders);
        pluginManager = new AtlasPluginManager(LOGGER, permissionRegistry, permissionProviders, servicesManager);
        scheduler = new AtlasScheduler(LOGGER);
        commands = new CommandRegistry();
        compatibilityCollector = new CompatibilityCollector(LOGGER, VERSION);
        CompatibilityRuntime.install(compatibilityCollector);
        serverAdapter = new ForgeServerAdapter(event.getServer(), pluginManager, scheduler, commands,
            permissionRegistry, permissionProviders, servicesManager);
        Bukkit.setServer(serverAdapter);
        compatibilityServiceOwner = new AtlasCompatibilityServiceOwner(
            serverAdapter, LOGGER, Path.of("config", "atlashybrid", "compatibility").toFile());
        serverAdapter.initializePermissions();
        LoginAdmissionBridge.install(new LoginAdmissionBridge.AdmissionHandler() {
            @Override
            public LoginAdmissionBridge.AdmissionResult admit(ServerPlayer player, String hostname,
                                                                java.net.InetAddress address) {
                if (!event.getServer().isSameThread()) {
                    throw new IllegalStateException("PlayerLoginEvent must execute on the Server thread");
                }
                org.bukkit.entity.Player adapter = serverAdapter.connectingPlayer(player);
                PlayerLoginEvent bridged = new PlayerLoginEvent(adapter, hostname, address, address);
                pluginManager.callEvent(bridged);
                if (bridged.getResult() == PlayerLoginEvent.Result.ALLOWED) {
                    return LoginAdmissionBridge.AdmissionResult.permit();
                }
                String reason = java.util.Objects.toString(bridged.getKickMessage(), "");
                LOGGER.info("[AtlasHybrid Connection] Player login denied name=" + player.getGameProfile().getName()
                    + " result=" + bridged.getResult() + " reason=" + reason);
                serverAdapter.disconnect(player);
                return LoginAdmissionBridge.AdmissionResult.denied(reason);
            }

            @Override
            public void abort(ServerPlayer player) {
                serverAdapter.disconnect(player);
            }
        });
        pluginRuntime = new PluginRuntime(serverAdapter, pluginManager, commands, scheduler, LOGGER, AtlasHybridMod.class.getClassLoader());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        try {
            pluginRuntime.loadAll(Path.of("plugins"));
            registerPluginCommands(event.getServer().getCommands().getDispatcher());
        } catch (IOException | DependencyResolutionException exception) {
            LOGGER.log(Level.SEVERE, "AtlasHybrid plugin discovery failed", exception);
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (ModList.get().isLoaded("luckperms")) {
            try {
                luckPermsBridge = new LuckPermsForgePermissionBridge(
                    permissionProviders, servicesManager, compatibilityServiceOwner, LOGGER);
                luckPermsBridge.refresh();
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.log(Level.WARNING,
                    "[AtlasHybrid LuckPerms] Optional public API bridge unavailable; Atlas fallback remains active",
                    failure);
                luckPermsBridge = null;
            }
        }
        pluginRuntime.enableAll();
        LOGGER.info("[AtlasHybrid] " + pluginRuntime.loadedCount() + " plugin(s) loaded and enable phase completed");
        LOGGER.info("[AtlasHybrid Compatibility]\n"
            + "Plugins discovered: " + pluginRuntime.discoveredCount() + "\n"
            + "Loaded: " + pluginRuntime.loadedCount() + "\n"
            + "Unsupported: " + compatibilityCollector.totalUnsupportedCalls() + "\n"
            + "Permission providers: " + serverAdapter.permissionProviderCount() + "\n"
            + "Services: " + serverAdapter.serviceCount());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        registerAtlasCommand(event.getDispatcher());
    }

    private void registerAtlasCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atlas")
            .executes(context -> dispatch(context.getSource(), new String[0]))
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .suggests((context, builder) -> suggestPlugin("atlas", context.getSource(), builder))
                .executes(context -> dispatch(context.getSource(),
                    splitArguments(StringArgumentType.getString(context, "args")))))
        );
    }

    private void registerPluginCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : commands.names()) {
            if ("atlas".equals(name)) continue;
            dispatcher.register(Commands.literal(name)
                .executes(context -> dispatchPlugin(name, context.getSource(), new String[0]))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests((context, builder) -> suggestPlugin(name, context.getSource(), builder))
                    .executes(context -> dispatchPlugin(name, context.getSource(), splitArguments(StringArgumentType.getString(context, "args"))))));
        }
    }

    private CompletableFuture<Suggestions> suggestPlugin(
        String name,
        CommandSourceStack source,
        SuggestionsBuilder builder
    ) {
        if (source.getServer().isSameThread()) {
            addPluginSuggestions(name, source, builder);
            return builder.buildFuture();
        }
        CompletableFuture<Suggestions> result = new CompletableFuture<>();
        source.getServer().execute(() -> {
            try {
                addPluginSuggestions(name, source, builder);
                result.complete(builder.build());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result;
    }

    private void addPluginSuggestions(String name, CommandSourceStack source, SuggestionsBuilder builder) {
        for (String suggestion : commands.tabComplete(name, ForgeCommandSender.of(source),
            splitCompletionArguments(builder.getRemaining()))) {
            builder.suggest(suggestion);
        }
    }

    private int dispatchPlugin(String name, CommandSourceStack source, String[] args) {
        if (!commands.dispatch(name, ForgeCommandSender.of(source), args)) return 0;
        return 1;
    }

    private static String[] splitArguments(String raw) {
        String value = raw.strip();
        return value.isEmpty() ? new String[0] : value.split("\\s+");
    }

    private static String[] splitCompletionArguments(String raw) {
        return raw.isEmpty() ? new String[] { "" } : raw.split("\\s+", -1);
    }

    private int dispatch(CommandSourceStack source, String[] args) {
        if (commands == null || !commands.dispatch("atlas", ForgeCommandSender.of(source), args)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("AtlasHybrid command is not available."));
            return 0;
        }
        return 1;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (luckPermsBridge != null) luckPermsBridge.refresh();
        if (scheduler != null) scheduler.tick();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerCommand(net.minecraftforge.event.CommandEvent event) {
        if (pluginManager == null || serverAdapter == null) return;
        var parse = event.getParseResults();
        CommandSourceStack source = parse.getContext().getSource();
        if (source.getEntity() instanceof ServerPlayer player) {
            if (!source.getServer().isSameThread()) {
                throw new IllegalStateException("Player command events must execute on the Server thread");
            }
            ForgePlayerAdapter sender = serverAdapter.player(player);
            String original = parse.getReader().getString();
            PlayerCommandPreprocessEvent bridged = new PlayerCommandPreprocessEvent(sender, "/" + original);
            pluginManager.callEvent(bridged);
            if (bridged.isCancelled()) {
                event.setCanceled(true);
                return;
            }
            String command = bridged.getMessage().substring(1);
            CommandSourceStack executionSource = source;
            if (bridged.getPlayer() != sender) {
                if (!(bridged.getPlayer() instanceof ForgePlayerAdapter replacement)) {
                    throw new IllegalArgumentException("PlayerCommandPreprocessEvent player is not managed by AtlasHybrid");
                }
                executionSource = replacement.commandSource();
            }
            if (!java.util.Objects.equals(original, command) || executionSource != source) {
                event.setParseResults(source.getServer().getCommands().getDispatcher()
                    .parse(command, executionSource));
            }
            return;
        }
        boolean remote = source.source instanceof net.minecraft.server.rcon.RconConsoleSource;
        boolean localConsole = source.source == source.getServer();
        if (!remote && !localConsole) return;
        if (!source.getServer().isSameThread()) {
            throw new IllegalStateException("Server command events must execute on the Server thread");
        }

        String original = parse.getReader().getString();
        ServerCommandEvent bridged = remote
            ? new RemoteServerCommandEvent(ForgeCommandSender.of(source), original)
            : new ServerCommandEvent(serverAdapter.getConsoleSender(), original);
        pluginManager.callEvent(bridged);
        if (bridged.isCancelled()) {
            event.setCanceled(true);
            return;
        }
        if (!java.util.Objects.equals(original, bridged.getCommand())) {
            event.setParseResults(source.getServer().getCommands().getDispatcher()
                .parse(bridged.getCommand(), source));
        }
    }

    @SubscribeEvent
    public void onPlayerNegotiation(PlayerNegotiationEvent event) {
        if (pluginManager == null) return;
        SocketAddress remote = event.getConnection().getRemoteAddress();
        if (!(remote instanceof InetSocketAddress socketAddress) || socketAddress.getAddress() == null) {
            LOGGER.warning("[AtlasHybrid Connection] AsyncPlayerPreLoginEvent skipped: remote InetAddress unavailable");
            return;
        }
        String name = event.getProfile().getName();
        java.util.UUID uniqueId = event.getProfile().getId() != null
            ? event.getProfile().getId()
            : UUIDUtil.createOfflinePlayerUUID(name);
        event.enqueueWork(() -> {
            AsyncPlayerPreLoginEvent bridged =
                new AsyncPlayerPreLoginEvent(name, socketAddress.getAddress(), uniqueId);
            pluginManager.callEvent(bridged);
            if (bridged.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                LOGGER.info("[AtlasHybrid Connection] Async pre-login denied name=" + name
                    + " result=" + bridged.getLoginResult()
                    + " reason=" + java.util.Objects.toString(bridged.getKickMessage(), ""));
                event.getConnection().disconnect(Component.literal(
                    java.util.Objects.toString(bridged.getKickMessage(), "")));
            }
        });
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (pluginManager != null && event.getEntity() instanceof ServerPlayer player) {
            pluginManager.callEvent(new PlayerJoinEvent(serverAdapter.promotePlayer(player)));
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (pluginManager != null && event.getEntity() instanceof ServerPlayer player) {
            try {
                pluginManager.callEvent(new PlayerQuitEvent(serverAdapter.player(player)));
            } finally {
                serverAdapter.disconnect(player);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (pluginManager == null || serverAdapter == null
            || !(event.getEntity() instanceof ServerPlayer player)
            || event.getFrom().equals(event.getTo())) return;
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("PlayerChangedWorldEvent must execute on the Server thread");
        }
        ForgePlayerAdapter adapter = serverAdapter.onlinePlayer(player);
        ForgeWorldAdapter from = serverAdapter.world(event.getFrom());
        ForgeWorldAdapter destination = serverAdapter.world(event.getTo());
        if (adapter == null || from == null || destination == null) {
            LOGGER.warning("[AtlasHybrid World] PlayerChangedWorldEvent skipped: session or world adapter unavailable"
                + " from=" + event.getFrom().location() + " to=" + event.getTo().location());
            return;
        }
        if (adapter.getWorld() != destination) {
            throw new IllegalStateException("Player world adapter did not reflect destination before dispatch");
        }
        pluginManager.callEvent(new PlayerChangedWorldEvent(adapter, from));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPlayerGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (pluginManager == null || serverAdapter == null || event.isCanceled()
            || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("PlayerGameModeChangeEvent must execute on the Server thread");
        }
        ForgePlayerAdapter adapter = serverAdapter.onlinePlayer(player);
        if (adapter == null) {
            serverAdapter.updateGameMode(player, event.getNewGameMode());
            return;
        }
        org.bukkit.GameMode current = ForgeGameModeMapper.toBukkit(event.getCurrentGameMode());
        org.bukkit.GameMode target = ForgeGameModeMapper.toBukkit(event.getNewGameMode());
        if (current == target) return;
        if (adapter.getGameMode() != current) {
            throw new IllegalStateException("Player game-mode snapshot did not reflect the pre-change mode");
        }
        PlayerGameModeChangeEvent bridged = new PlayerGameModeChangeEvent(adapter, target);
        pluginManager.callEvent(bridged);
        if (bridged.isCancelled()) {
            event.setCanceled(true);
            return;
        }
        serverAdapter.updateGameMode(player, event.getNewGameMode());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (pluginManager == null || !(event.getPlayer() instanceof ServerPlayer player)) return;
        boolean previouslyCancelled = event.isCanceled();
        BlockBreakEvent bridged = new BlockBreakEvent(new ForgeBlockAdapter(event.getLevel(), event.getPos()), serverAdapter.player(player));
        bridged.setCancelled(previouslyCancelled);
        pluginManager.callEvent(bridged);
        event.setCanceled(previouslyCancelled || bridged.isCancelled());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (luckPermsBridge != null) luckPermsBridge.close();
        if (compatibilityServiceOwner != null) {
            servicesManager.unregisterAll(compatibilityServiceOwner);
            compatibilityServiceOwner.close();
        }
        if (pluginRuntime != null) {
            pluginRuntime.disableAll();
            LOGGER.info("[AtlasHybrid Permission] lifecycle cleanup providers=" + serverAdapter.permissionProviderCount()
                + " services=" + serverAdapter.serviceCount());
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        LoginAdmissionBridge.clear();
        if (pluginRuntime != null) {
            pluginRuntime.close();
            pluginRuntime = null;
        }
        if (serverAdapter != null) serverAdapter.close();
        if (permissionProviders != null) AtlasPermissions.clear(permissionProviders);
        Bukkit.setServer(null);
        CompatibilityRuntime.clear();
        pluginManager = null;
        scheduler = null;
        commands = null;
        serverAdapter = null;
        compatibilityCollector = null;
        permissionRegistry = null;
        permissionProviders = null;
        servicesManager = null;
        luckPermsBridge = null;
        compatibilityServiceOwner = null;
    }
}

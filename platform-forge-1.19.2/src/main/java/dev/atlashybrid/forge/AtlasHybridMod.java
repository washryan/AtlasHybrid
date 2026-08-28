package dev.atlashybrid.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.atlashybrid.diagnostics.CompatibilityCollector;
import dev.atlashybrid.diagnostics.CompatibilityRuntime;
import dev.atlashybrid.loader.DependencyResolutionException;
import dev.atlashybrid.loader.PluginRuntime;
import dev.atlashybrid.runtime.command.CommandRegistry;
import dev.atlashybrid.runtime.event.AtlasPluginManager;
import dev.atlashybrid.runtime.scheduler.AtlasScheduler;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.bukkit.Bukkit;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        pluginManager = new AtlasPluginManager(LOGGER);
        scheduler = new AtlasScheduler(LOGGER);
        commands = new CommandRegistry();
        compatibilityCollector = new CompatibilityCollector(LOGGER, VERSION);
        CompatibilityRuntime.install(compatibilityCollector);
        serverAdapter = new ForgeServerAdapter(event.getServer(), pluginManager, scheduler, commands);
        Bukkit.setServer(serverAdapter);
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
        pluginRuntime.enableAll();
        LOGGER.info("[AtlasHybrid] " + pluginRuntime.loadedCount() + " plugin(s) loaded and enable phase completed");
        LOGGER.info("[AtlasHybrid Compatibility]\n"
            + "Plugins discovered: " + pluginRuntime.discoveredCount() + "\n"
            + "Loaded: " + pluginRuntime.loadedCount() + "\n"
            + "Unsupported: " + compatibilityCollector.totalUnsupportedCalls());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        registerAtlasCommand(event.getDispatcher());
    }

    private void registerAtlasCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atlas")
            .executes(context -> dispatch(context.getSource(), new String[0]))
            .then(Commands.literal("info").executes(context -> dispatch(context.getSource(), new String[] { "info" }))));
    }

    private void registerPluginCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : commands.names()) {
            if ("atlas".equals(name)) continue;
            dispatcher.register(Commands.literal(name)
                .executes(context -> dispatchPlugin(name, context.getSource(), new String[0]))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .executes(context -> dispatchPlugin(name, context.getSource(), splitArguments(StringArgumentType.getString(context, "args"))))));
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

    private int dispatch(CommandSourceStack source, String[] args) {
        if (commands == null || !commands.dispatch("atlas", ForgeCommandSender.of(source), args)) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("AtlasHybrid command is not available."));
            return 0;
        }
        return 1;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && scheduler != null) scheduler.tick();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (pluginManager != null && event.getEntity() instanceof ServerPlayer player) {
            pluginManager.callEvent(new PlayerJoinEvent(new ForgePlayerAdapter(player)));
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (pluginManager != null && event.getEntity() instanceof ServerPlayer player) {
            pluginManager.callEvent(new PlayerQuitEvent(new ForgePlayerAdapter(player)));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (pluginManager == null || !(event.getPlayer() instanceof ServerPlayer player)) return;
        boolean previouslyCancelled = event.isCanceled();
        BlockBreakEvent bridged = new BlockBreakEvent(new ForgeBlockAdapter(event.getLevel(), event.getPos()), new ForgePlayerAdapter(player));
        bridged.setCancelled(previouslyCancelled);
        pluginManager.callEvent(bridged);
        event.setCanceled(previouslyCancelled || bridged.isCancelled());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (pluginRuntime != null) pluginRuntime.disableAll();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (pluginRuntime != null) {
            pluginRuntime.close();
            pluginRuntime = null;
        }
        Bukkit.setServer(null);
        CompatibilityRuntime.clear();
        pluginManager = null;
        scheduler = null;
        commands = null;
        serverAdapter = null;
        compatibilityCollector = null;
    }
}

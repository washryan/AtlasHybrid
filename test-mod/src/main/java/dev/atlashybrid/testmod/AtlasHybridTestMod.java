package dev.atlashybrid.testmod;

import com.mojang.logging.LogUtils;
import java.util.List;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

@Mod(AtlasHybridTestMod.MOD_ID)
public final class AtlasHybridTestMod {
    public static final String MOD_ID = "atlashybrid_test_mod";
    private static final Logger LOGGER = LogUtils.getLogger();
    private MinecraftServer server;
    private int ticks;
    private boolean probeRan;

    public AtlasHybridTestMod() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("[Forge] AtlasHybridTestMod loaded successfully.");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        ticks = 0;
        LOGGER.info("[AtlasHybridIntegration] SERVER_STARTED");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (server == null || event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (!probeRan && ticks >= 5) {
            probeRan = true;
            runProbe();
        }
        if (ticks >= 60) {
            LOGGER.info("[AtlasHybridIntegration] SHUTDOWN_REQUESTED");
            MinecraftServer current = server;
            server = null;
            current.halt(false);
        }
    }

    private void runProbe() {
        LOGGER.info("[AtlasHybridIntegration] PROBE_START");
        var source = server.createCommandSourceStack();
        int atlasResult = server.getCommands().performCommand(server.getCommands().getDispatcher().parse("atlas", source), "atlas");
        int infoResult = server.getCommands().performCommand(server.getCommands().getDispatcher().parse("atlas info", source), "atlas info");
        int permissionResult = server.getCommands().performCommand(server.getCommands().getDispatcher().parse("atlas permission atlas.test.provider", source), "atlas permission atlas.test.provider");
        LOGGER.info("[AtlasHybridIntegration] COMMAND_RESULTS atlas={} info={} permission={}", atlasResult, infoResult, permissionResult);

        ServerLevel level = server.overworld();
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        EmbeddedChannel channel = new EmbeddedChannel();
        Connection connection = player.connection.getConnection();
        ObfuscationReflectionHelper.setPrivateValue(Connection.class, connection, channel, "channel");
        BlockPos position = level.getSharedSpawnPos().above(2);
        player.setPos(position.getX() + 0.5D, position.getY() + 1.0D, position.getZ() + 0.5D);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);

        List<String> consoleSuggestions = suggestions("atlas ", source);
        List<String> playerSuggestions = suggestions("atlas ", player.createCommandSourceStack());
        List<String> expectedSuggestions = List.of("alpha", "beta", "gamma");
        if (!expectedSuggestions.equals(consoleSuggestions) || !expectedSuggestions.equals(playerSuggestions)) {
            throw new IllegalStateException("Tab completion mismatch: console=" + consoleSuggestions
                + " player=" + playerSuggestions);
        }
        LOGGER.info("[AtlasHybridIntegration] TAB_COMPLETION_OK");

        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        org.bukkit.Server bukkitServer = org.bukkit.Bukkit.getServer();
        org.bukkit.entity.Player onlinePlayer = bukkitServer.getPlayer(player.getUUID());
        if (bukkitServer.getOnlinePlayers().size() != 1
            || onlinePlayer == null
            || onlinePlayer != bukkitServer.getPlayerExact(player.getGameProfile().getName())
            || onlinePlayer != bukkitServer.getOnlinePlayers().iterator().next()) {
            throw new IllegalStateException("Online player registry did not expose a stable adapter");
        }
        runExternalRegressionIfPresent(player);
        level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
        boolean destroyed = player.gameMode.destroyBlock(position);
        boolean blockStillPresent = level.getBlockState(position).is(Blocks.STONE);
        LOGGER.info("[AtlasHybridIntegration] BLOCK_BREAK_RESULT destroyed={} blockStillPresent={}", destroyed, blockStillPresent);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        if (!bukkitServer.getOnlinePlayers().isEmpty()
            || bukkitServer.getPlayer(player.getUUID()) != null
            || bukkitServer.getPlayerExact(player.getGameProfile().getName()) != null) {
            throw new IllegalStateException("Online player registry retained a disconnected player");
        }
        LOGGER.info("[AtlasHybridIntegration] ONLINE_PLAYERS_OK");
        channel.finishAndReleaseAll();
        LOGGER.info("[AtlasHybridIntegration] PLAYER_EVENTS_POSTED");
        LOGGER.info("[AtlasHybridIntegration] PROBE_END");
    }

    private void runExternalRegressionIfPresent(FakePlayer player) {
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("WelcomeMessage") != null) {
            LOGGER.info("[AtlasHybridExternalRegression] WELCOME_JOIN_POSTED");
        }
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("WarpPlugin") == null) return;

        var source = player.createCommandSourceStack();
        double savedX = player.getX();
        double savedY = player.getY();
        double savedZ = player.getZ();
        int cleanupBefore = perform("delwarp atlasphase92", source);
        int addAlias = perform("addwarp atlasphase92", source);
        player.setPos(savedX + 12.0D, savedY, savedZ + 12.0D);
        int listAlias = perform("warps", source);
        int teleport = perform("warp atlasphase92", source);
        boolean positionMatches = Math.abs(player.getX() - savedX) < 0.001D
            && Math.abs(player.getY() - savedY) < 0.001D
            && Math.abs(player.getZ() - savedZ) < 0.001D;
        int removeAlias = perform("remwarp atlasphase92", source);
        int missing = perform("warp atlasphase92", source);
        LOGGER.info("[AtlasHybridExternalRegression] WARP_COMMANDS cleanupBefore={} addAlias={} listAlias={} teleport={} positionMatches={} removeAlias={} missing={}",
            cleanupBefore, addAlias, listAlias, teleport, positionMatches, removeAlias, missing);
    }

    private int perform(String command, net.minecraft.commands.CommandSourceStack source) {
        return server.getCommands().performCommand(server.getCommands().getDispatcher().parse(command, source), command);
    }

    private List<String> suggestions(String command, net.minecraft.commands.CommandSourceStack source) {
        var dispatcher = server.getCommands().getDispatcher();
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, source)).join().getList().stream()
            .map(suggestion -> suggestion.getText())
            .toList();
    }
}

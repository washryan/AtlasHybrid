package dev.atlashybrid.testmod;

import com.mojang.logging.LogUtils;
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
        LOGGER.info("[AtlasHybridIntegration] COMMAND_RESULTS atlas={} info={}", atlasResult, infoResult);

        ServerLevel level = server.overworld();
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        EmbeddedChannel channel = new EmbeddedChannel();
        Connection connection = player.connection.getConnection();
        ObfuscationReflectionHelper.setPrivateValue(Connection.class, connection, channel, "channel");
        BlockPos position = level.getSharedSpawnPos().above(2);
        player.setPos(position.getX() + 0.5D, position.getY() + 1.0D, position.getZ() + 0.5D);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);

        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
        boolean destroyed = player.gameMode.destroyBlock(position);
        boolean blockStillPresent = level.getBlockState(position).is(Blocks.STONE);
        LOGGER.info("[AtlasHybridIntegration] BLOCK_BREAK_RESULT destroyed={} blockStillPresent={}", destroyed, blockStillPresent);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        channel.finishAndReleaseAll();
        LOGGER.info("[AtlasHybridIntegration] PLAYER_EVENTS_POSTED");
        LOGGER.info("[AtlasHybridIntegration] PROBE_END");
    }
}

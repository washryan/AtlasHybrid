package dev.atlashybrid.testmod;

import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
    private CompletableFuture<LoginProofResult> loginProof;

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
        if (ticks == 1) {
            loginProof = CompletableFuture.supplyAsync(this::runRealLoginProof);
        }
        if (!probeRan && ticks >= 5 && loginProof != null && loginProof.isDone()) {
            probeRan = true;
            LoginProofResult login = loginProof.join();
            if (!login.preLoginDenied() || !login.loginDenied() || !login.allowed()
                || !login.joinObserved() || !login.quitCleanupObserved() || !login.vanillaGateAllowed()
                || !login.remoteCommandEvent()) {
                throw new IllegalStateException("Real login proof failed: " + login);
            }
            LOGGER.info("[AtlasHybridIntegration] ASYNC_PRELOGIN_OK");
            LOGGER.info("[AtlasHybridIntegration] PRELOGIN_DENY_OK");
            LOGGER.info("[AtlasHybridIntegration] PLAYER_LOGIN_HOOK_OK");
            LOGGER.info("[AtlasHybridIntegration] PLAYER_LOGIN_DENY_OK");
            LOGGER.info("[AtlasHybridIntegration] REMOTE_SERVER_COMMAND_EVENT_OK");
            runProbe();
        }
        if (ticks >= 300 && !probeRan) {
            throw new IllegalStateException("Real login proof did not complete");
        }
        if (probeRan && ticks >= 60) {
            LOGGER.info("[AtlasHybridIntegration] SHUTDOWN_REQUESTED");
            MinecraftServer current = server;
            server = null;
            current.halt(false);
        }
    }

    private LoginProofResult runRealLoginProof() {
        try {
            LoginResponse denied = loginDenied("AtlasDenied");
            boolean deniedSessionAbsent = remainsPlayerAbsent("AtlasDenied", 2_000L)
                && absentFromMinecraftAndBukkit("AtlasDenied");
            LoginResponse loginDenied = loginDenied("AtlasLoginDenied");
            boolean loginDeniedSessionAbsent = remainsPlayerAbsent("AtlasLoginDenied", 2_000L)
                && absentFromMinecraftAndBukkit("AtlasLoginDenied");
            LoginResponse vanillaGate = loginAndHold("AtlasVanillaGate");
            LoginResponse allowed = loginAndHold("AtlasAllowed");
            boolean remoteCommandEvent = runRconCommandProof();
            return new LoginProofResult(
                denied.packetId() == 0
                    && (denied.payload().contains("AtlasHybrid integration deny")
                        || denied.payload().equals("connection-closed-after-deny"))
                    && deniedSessionAbsent,
                loginDenied.packetId() == 0
                    && loginDenied.payload().contains("AtlasHybrid PlayerLoginEvent deny")
                    && loginDeniedSessionAbsent,
                allowed.packetId() == 2,
                allowed.joinObserved(),
                allowed.quitCleanupObserved(),
                vanillaGate.packetId() == 2 && vanillaGate.joinObserved() && vanillaGate.quitCleanupObserved(),
                remoteCommandEvent
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Real Minecraft login probe failed", exception);
        }
    }

    private LoginResponse loginDenied(String name) throws IOException {
        try (Socket socket = openLoginSocket(name)) {
            LoginResponse response = readLoginResponse(socket);
            if (response.packetId() == 0) {
                // Let the server finish its own disconnect before the client socket is closed.
                socket.getInputStream().read();
            }
            return response;
        } catch (EOFException expectedTransportClose) {
            return new LoginResponse(0, "connection-closed-after-deny", false, false);
        }
    }

    private LoginResponse loginAndHold(String name) throws IOException {
        Socket socket = openLoginSocket(name);
        try {
            LoginResponse response = readLoginResponse(socket);
            boolean joined = response.packetId() == 2 && waitForPlayer(name, true, 5_000L);
            socket.close();
            boolean quit = waitForPlayer(name, false, 5_000L);
            return new LoginResponse(response.packetId(), response.payload(), joined, quit);
        } finally {
            if (!socket.isClosed()) socket.close();
        }
    }

    private Socket openLoginSocket(String name) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort());
        socket.setSoTimeout(10_000);
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());

        ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
        DataOutputStream handshake = new DataOutputStream(handshakeBytes);
        writeVarInt(handshake, 0);
        writeVarInt(handshake, 760);
        writeString(handshake, "localhost");
        handshake.writeShort(server.getPort());
        writeVarInt(handshake, 2);
        writePacket(output, handshakeBytes.toByteArray());

        ByteArrayOutputStream loginBytes = new ByteArrayOutputStream();
        DataOutputStream login = new DataOutputStream(loginBytes);
        writeVarInt(login, 0);
        writeString(login, name);
        login.writeBoolean(false);
        login.writeBoolean(false);
        writePacket(output, loginBytes.toByteArray());
        output.flush();
        return socket;
    }

    private LoginResponse readLoginResponse(Socket socket) throws IOException {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        for (int attempt = 0; attempt < 8; attempt++) {
            int frameLength = readVarInt(input);
            byte[] frame = input.readNBytes(frameLength);
            if (frame.length != frameLength) throw new EOFException("Incomplete login frame");
            DataInputStream packet = new DataInputStream(new ByteArrayInputStream(frame));
            int packetId = readVarInt(packet);
            if (packetId == 0) return new LoginResponse(packetId, readString(packet), false, false);
            if (packetId == 2) return new LoginResponse(packetId, "login-success", false, false);
            if (packetId == 1) throw new IOException("Server requested online-mode encryption during offline integration proof");
            if (packetId == 3) throw new IOException("Unexpected compression packet during uncompressed integration proof");
        }
        throw new IOException("No terminal login response received");
    }

    private boolean waitForPlayer(String name, boolean present, long timeoutMillis) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            boolean found = org.bukkit.Bukkit.getServer().getPlayerExact(name) != null;
            if (found == present) return true;
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Login proof interrupted", exception);
            }
        }
        return false;
    }

    private boolean remainsPlayerAbsent(String name, long durationMillis) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(durationMillis);
        while (System.nanoTime() < deadline) {
            if (org.bukkit.Bukkit.getServer().getPlayerExact(name) != null) return false;
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Login proof interrupted", exception);
            }
        }
        return true;
    }

    private boolean absentFromMinecraftAndBukkit(String name) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        server.execute(() -> {
            boolean absent = org.bukkit.Bukkit.getServer().getPlayerExact(name) == null
                && server.getPlayerList().getPlayerByName(name) == null;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.players().stream().anyMatch(player -> player.getGameProfile().getName().equals(name))) {
                    absent = false;
                    break;
                }
            }
            result.complete(absent);
        });
        return result.orTimeout(5, java.util.concurrent.TimeUnit.SECONDS).join();
    }

    private static void writePacket(DataOutputStream output, byte[] packet) throws IOException {
        writeVarInt(output, packet.length);
        output.write(packet);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = readVarInt(input);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Incomplete string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream output, int value) throws IOException {
        do {
            int next = value & 0x7F;
            value >>>= 7;
            if (value != 0) next |= 0x80;
            output.writeByte(next);
        } while (value != 0);
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int next = input.readUnsignedByte();
            result |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) return result;
        }
        throw new IOException("VarInt exceeds five bytes");
    }

    private record LoginResponse(int packetId, String payload, boolean joinObserved, boolean quitCleanupObserved) { }

    private record LoginProofResult(boolean preLoginDenied, boolean loginDenied, boolean allowed,
                                    boolean joinObserved, boolean quitCleanupObserved, boolean vanillaGateAllowed,
                                    boolean remoteCommandEvent) { }

    private boolean runRconCommandProof() throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), 25575)) {
            socket.setSoTimeout(10_000);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());
            writeRconPacket(output, 912, 3, "atlas-integration");
            RconPacket auth = readRconPacket(input);
            if (auth.id() != 912 || auth.type() != 2) return false;
            writeRconPacket(output, 913, 2, "atlas remote-original");
            RconPacket response = readRconPacket(input);
            return response.id() == 913 && response.type() == 0
                && response.payload().contains("remote server mutation executed");
        }
    }

    private static void writeRconPacket(DataOutputStream output, int id, int type, String payload) throws IOException {
        byte[] value = payload.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length + 14);
        DataOutputStream packet = new DataOutputStream(bytes);
        packet.writeInt(Integer.reverseBytes(value.length + 10));
        packet.writeInt(Integer.reverseBytes(id));
        packet.writeInt(Integer.reverseBytes(type));
        packet.write(value);
        packet.writeByte(0);
        packet.writeByte(0);
        output.write(bytes.toByteArray());
        output.flush();
    }

    private static RconPacket readRconPacket(DataInputStream input) throws IOException {
        int length = Integer.reverseBytes(input.readInt());
        if (length < 10 || length > 4096) throw new IOException("Invalid RCON packet length: " + length);
        int id = Integer.reverseBytes(input.readInt());
        int type = Integer.reverseBytes(input.readInt());
        byte[] payload = input.readNBytes(length - 10);
        if (payload.length != length - 10 || input.readUnsignedByte() != 0 || input.readUnsignedByte() != 0) {
            throw new EOFException("Incomplete RCON packet");
        }
        return new RconPacket(id, type, new String(payload, StandardCharsets.UTF_8));
    }

    private record RconPacket(int id, int type, String payload) { }

    private void runProbe() {
        LOGGER.info("[AtlasHybridIntegration] PROBE_START");
        var source = server.createCommandSourceStack();
        int atlasResult = server.getCommands().performCommand(server.getCommands().getDispatcher().parse("atlas", source), "atlas");
        int infoResult = server.getCommands().performCommand(server.getCommands().getDispatcher().parse("atlas info", source), "atlas info");
        int permissionResult = server.getCommands().performCommand(server.getCommands().getDispatcher().parse("atlas permission atlas.test.provider", source), "atlas permission atlas.test.provider");
        LOGGER.info("[AtlasHybridIntegration] COMMAND_RESULTS atlas={} info={} permission={}", atlasResult, infoResult, permissionResult);
        int localMutation = server.getCommands().performPrefixedCommand(source, "atlas server-original");
        server.getCommands().performPrefixedCommand(source, "atlas server-cancelled");
        if (localMutation != 1) throw new IllegalStateException("Local server command mutation did not execute");
        LOGGER.info("[AtlasHybridIntegration] SERVER_COMMAND_EVENT_OK");

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
        boolean runtimeOnlineMode = server.usesAuthentication();
        if (bukkitServer.getOnlineMode() != runtimeOnlineMode
            || org.bukkit.Bukkit.getOnlineMode() != runtimeOnlineMode) {
            throw new IllegalStateException("Bukkit online mode differs from Minecraft runtime state");
        }
        LOGGER.info("[AtlasHybridIntegration] SERVER_ONLINE_MODE_OK");
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

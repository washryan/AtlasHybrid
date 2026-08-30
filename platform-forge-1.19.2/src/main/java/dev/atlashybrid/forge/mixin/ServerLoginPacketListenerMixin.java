package dev.atlashybrid.forge.mixin;

import com.mojang.authlib.GameProfile;
import dev.atlashybrid.forge.LoginAdmissionBridge;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ServerLoginPacketListenerImpl.class)
abstract class ServerLoginPacketListenerMixin {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final public Connection connection;
    @Shadow private GameProfile gameProfile;
    @Unique private ServerPlayer atlas$candidate;

    @Inject(
        method = "handleAcceptedLogin",
        at = @At(value = "NEW", target = "net/minecraft/network/protocol/login/ClientboundGameProfilePacket"),
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void atlas$admitPlayer(CallbackInfo callback, ProfilePublicKey profilePublicKey, Component duplicateReason) {
        atlas$candidate = server.getPlayerList().getPlayerForLogin(gameProfile, profilePublicKey);
        LoginAdmissionBridge.AdmissionResult result = LoginAdmissionBridge.admit(connection, atlas$candidate);
        if (!result.allowed()) {
            LoginAdmissionBridge.abort(atlas$candidate);
            atlas$candidate = null;
            connection.send(new ClientboundLoginDisconnectPacket(result.message()),
                PacketSendListener.thenRun(() -> connection.disconnect(result.message())));
            connection.setReadOnly();
            callback.cancel();
        }
    }

    @Redirect(
        method = "handleAcceptedLogin",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;getPlayerForLogin(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/world/entity/player/ProfilePublicKey;)Lnet/minecraft/server/level/ServerPlayer;"
        )
    )
    private ServerPlayer atlas$reuseAdmittedPlayer(PlayerList playerList, GameProfile profile, ProfilePublicKey profilePublicKey) {
        if (atlas$candidate == null) {
            throw new IllegalStateException("AtlasHybrid admitted player was not retained");
        }
        return atlas$candidate;
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void atlas$cleanupAbortedLogin(Component reason, CallbackInfo callback) {
        LoginAdmissionBridge.abort(atlas$candidate);
        atlas$candidate = null;
    }
}

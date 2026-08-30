package dev.atlashybrid.forge.mixin;

import dev.atlashybrid.forge.LoginAdmissionBridge;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerHandshakePacketListenerImpl.class)
abstract class ServerHandshakePacketListenerMixin {
    @Shadow @Final private Connection connection;

    @Inject(method = "handleIntention", at = @At("HEAD"))
    private void atlas$captureHostname(ClientIntentionPacket packet, CallbackInfo callback) {
        LoginAdmissionBridge.captureHostname(connection, packet.getHostName() + ":" + packet.getPort());
    }
}

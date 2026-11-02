package com.randomperks.mixin;

import com.randomperks.game.DragonTamerService;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 末影人瞬移之后, 客户端还会补发瞬移前的载具位置。那些包会把人和坐骑拉回去。
 *
 * <p>只在短窗口里丢掉离当前坐标还很远、或仍更靠近瞬移前位置的包, 并再补一发
 * 服务端坐标。已经跟上的驾驶包照常交给原版。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class DragonTamerBlinkMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void randomperks$rejectStaleBlink(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (DragonTamerService.INSTANCE.shouldIgnoreStaleVehicleMove(this.player, packet.position())) {
            ci.cancel();
        }
    }
}

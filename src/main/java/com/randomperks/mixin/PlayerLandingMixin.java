package com.randomperks.mixin;

import com.randomperks.game.PerkAbilityService;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "二段跳"的"每段离地一次": 检查每个移动包携带的落地标志, 玩家一旦位于地面就复位使用标记。
 * (移动包里的 isOnGround 是最可靠的落地信号, 不依赖原版 onLanding 的微妙触发条件)
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class PlayerLandingMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V", at = @At("TAIL"))
    private void randomperks$onLanded(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (!packet.isOnGround()) return;
        PerkAbilityService.INSTANCE.clearDoubleJumpUsed(this.player);
        PerkAbilityService.INSTANCE.clearDashUsed(this.player);
    }
}

package com.randomperks.mixin;

import com.randomperks.game.DoubleJumpGlideGate;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "二段跳 + 鞘翅"协作 (仅客户端启用, 见 mixins.randomperks.json 的 client 列表)。
 *
 * 原版启动滑翔的链路: ClientPlayerEntity.tickMovement → PlayerEntity.checkGliding()
 * (内部 startGliding() 置滑翔位, 返回 true 后发 START_FALL_FLYING)。
 * 这里在 HEAD 拦掉: 当持有二段跳且"这次按跳应该先给二段跳"时返回 false ——
 * 本地不会进入滑翔, 也不会发包; 二段跳照常由 AbilityHandler 触发。
 * 服务端收到 START_FALL_FLYING 时同样走 checkGliding(), 因此无需额外的服务端校验。
 */
@Mixin(PlayerEntity.class)
public class DoubleJumpGlideMixin {

    @Inject(method = "checkGliding", at = @At("HEAD"), cancellable = true)
    private void randomperks$deferGlideToDoubleJump(CallbackInfoReturnable<Boolean> cir) {
        if (DoubleJumpGlideGate.getSuppress()) {
            cir.setReturnValue(false);
        }
    }
}

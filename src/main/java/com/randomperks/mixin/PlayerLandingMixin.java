package com.randomperks.mixin;

import com.randomperks.game.PerkAbilityService;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "二段跳"词条的"每段离地一次": 在落地瞬间复位使用标记。
 * 挂钩原版 ServerPlayerEntity.onLanding (移动包处理里在落地时调用), 事件驱动、无轮询。
 */
@Mixin(ServerPlayerEntity.class)
public class PlayerLandingMixin {

    @Inject(method = "onLanding()V", at = @At("TAIL"))
    private void randomperks$onLanding(CallbackInfo ci) {
        PerkAbilityService.INSTANCE.clearDoubleJumpUsed((ServerPlayerEntity) (Object) this);
    }
}

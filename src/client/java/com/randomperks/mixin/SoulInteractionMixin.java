package com.randomperks.mixin;

import com.randomperks.client.SoulCameraClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "灵魂出窍": 灵魂状态不能攻击 / 使用物品 / 挖掘 —— 这些操作都会从身体的位置发出,
 * 而玩家此刻在看别处, 很容易误操作。
 */
@Mixin(MinecraftClient.class)
public abstract class SoulInteractionMixin {

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void randomperks$blockAttack(CallbackInfoReturnable<Boolean> cir) {
        if (SoulCameraClient.active) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void randomperks$blockItemUse(CallbackInfo ci) {
        if (SoulCameraClient.active) {
            ci.cancel();
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void randomperks$blockBreaking(boolean breaking, CallbackInfo ci) {
        if (SoulCameraClient.active) {
            ci.cancel();
        }
    }
}

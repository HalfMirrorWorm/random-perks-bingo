package com.randomperks.mixin;

import com.randomperks.client.SoulCameraClient;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "灵魂出窍": 灵魂视角没有手 —— 相机已经离开身体, 第一人称手臂会悬浮在灵魂相机前面,
 * 所以在灵魂状态下直接跳过手部渲染。
 */
@Mixin(GameRenderer.class)
public abstract class SoulHandMixin {

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void randomperks$hideSoulHand(float tickDelta, boolean sleeping, Matrix4f matrix, CallbackInfo ci) {
        if (SoulCameraClient.active) {
            ci.cancel();
        }
    }
}

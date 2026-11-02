package com.randomperks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.randomperks.client.SoulCameraClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "灵魂出窍": 灵魂视角没有手 —— 相机已经离开身体, 第一人称手臂会悬浮在灵魂相机前面,
 * 所以在灵魂状态下直接跳过手部渲染。
 * 26.2 的手部提交还在 ItemInHandRenderer.submitHandsWithItems, 参数是本地玩家。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class SoulHandMixin {

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void randomperks$hideSoulHand(
            float tickDelta,
            PoseStack matrices,
            SubmitNodeCollector collector,
            LocalPlayer player,
            int packedLight,
            CallbackInfo ci) {
        if (SoulCameraClient.active) {
            ci.cancel();
        }
    }
}

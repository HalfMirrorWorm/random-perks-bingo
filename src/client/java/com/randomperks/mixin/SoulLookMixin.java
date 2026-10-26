package com.randomperks.mixin;

import com.randomperks.client.SoulCameraClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "灵魂出窍": 鼠标转视角时不要把身体转过去, 而是转灵魂相机。
 * 原版 {@code Mouse} 每帧调用玩家的 {@code changeLookDirection}, 这里截下来即可,
 * 身体的朝向保持不变 (其他玩家看到的身体也不会转圈)。
 */
@Mixin(Entity.class)
public abstract class SoulLookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void randomperks$rotateSoulInsteadOfBody(double deltaX, double deltaY, CallbackInfo ci) {
        if (!SoulCameraClient.active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || (Object) this != client.player) return;

        SoulCameraClient.INSTANCE.rotate(deltaX, deltaY);
        ci.cancel();
    }
}

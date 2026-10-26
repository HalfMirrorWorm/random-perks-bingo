package com.randomperks.mixin;

import com.randomperks.client.SoulCameraClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "灵魂出窍": 灵魂状态把身体的移动输入清零 —— 身体站在原地看着灵魂飞。
 * (空格/潜行仍然被 [com.randomperks.client.SoulCameraClient] 直接读取用于升降)
 */
@Mixin(KeyboardInput.class)
public abstract class SoulInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void randomperks$freezeBodyInput(CallbackInfo ci) {
        if (!SoulCameraClient.active) return;

        this.playerInput = PlayerInput.DEFAULT;
        this.movementVector = Vec2f.ZERO;
    }
}

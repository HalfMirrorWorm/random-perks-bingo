package com.randomperks.mixin;

import com.randomperks.client.SoulCameraClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "灵魂出窍": 灵魂状态把身体的移动输入清零 —— 身体站在原地看着灵魂飞。
 * (空格/潜行仍然被 [com.randomperks.client.SoulCameraClient] 直接读取用于升降)
 */
@Mixin(KeyboardInput.class)
public abstract class SoulInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void randomperks$freezeBodyInput(CallbackInfo ci) {
        if (!SoulCameraClient.active) return;

        this.keyPresses = Input.EMPTY;
        this.moveVector = Vec2.ZERO;
    }
}

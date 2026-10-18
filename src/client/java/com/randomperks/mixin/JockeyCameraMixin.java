package com.randomperks.mixin;

import com.randomperks.client.JockeyClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Jockey" 被骑者一侧: 把被骑者的视角同步成骑乘者的视角。
 *
 * 必须在 {@code tickMovement} 之前改 —— 原版正是用 {@code getYaw()} 把
 * "向前"换算成世界坐标下的移动向量, 朝向不同步的话骑乘者就没法给被骑者指方向。
 */
@Mixin(ClientPlayerEntity.class)
public abstract class JockeyCameraMixin {

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void randomperks$applyJockeyRotation(CallbackInfo ci) {
        if (!JockeyClientState.isControlled()) return;

        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        self.setYaw(JockeyClientState.getForcedYaw());
        self.setPitch(JockeyClientState.getForcedPitch());
    }
}

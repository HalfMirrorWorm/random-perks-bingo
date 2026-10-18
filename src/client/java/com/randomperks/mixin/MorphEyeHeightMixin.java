package com.randomperks.mixin;

import com.randomperks.client.MorphClientState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变群友" 的视角伪装 —— 变猪期间眼睛高度降到猪的高度。
 *
 * 只拦 {@code getEyeHeight} 而**不动碰撞箱**: 相机高度就是从
 * {@code getStandingEyeHeight()} → {@code getEyeHeight(pose)} 来的, 改这里就够;
 * 而改 {@code getDimensions}/{@code getBaseDimensions} 会顺带把碰撞箱也压成猪的尺寸
 * (主流变形 mod 的做法是前者, 例如 Morphling 的 PlayerEyeHeightMixin)。
 *
 * 注: 目标方法在 Entity 上是 final, 但 @Inject 是往方法体里插字节码, 与 final 无关。
 */
@Mixin(Entity.class)
public class MorphEyeHeightMixin {

    @Inject(
            method = "getEyeHeight(Lnet/minecraft/entity/EntityPose;)F",
            at = @At("HEAD"),
            cancellable = true)
    private void randomperks$pigEyeHeight(EntityPose pose, CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        if (!MorphClientState.isMorphed(self.getId())) return;

        cir.setReturnValue(EntityType.PIG.getDimensions().eyeHeight());
    }
}

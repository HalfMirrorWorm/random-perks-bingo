package com.randomperks.mixin;

import com.randomperks.game.MorphState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变猪" 的视角伪装 —— 变猪期间眼睛高度跟着降下来。
 *
 * 和 main 源集的 MorphDimensionsMixin (碰撞箱) 用的是同一组常量, 只是分工不同:
 * 眼睛高度纯粹是客户端表现 (改这里不会顺带压扁碰撞箱), 碰撞箱则两端都要一致。
 * 相机高度就是从 {@code getStandingEyeHeight()} → {@code getEyeHeight(pose)} 来的,
 * 所以只改这里就够了。
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
        if (!MorphState.isMorphed(self.getId())) return;

        cir.setReturnValue(MorphState.PIG_EYE_HEIGHT);
    }
}

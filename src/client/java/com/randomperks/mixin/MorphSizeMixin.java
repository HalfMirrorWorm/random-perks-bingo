package com.randomperks.mixin;

import com.randomperks.client.MorphClientState;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变群友" 的尺寸伪装 —— 变猪期间用猪的尺寸。
 *
 * 只挂客户端: 用户的诉求是"视角高度变成猪的高度", 而相机高度正是
 * {@code Entity.getEyeHeight()} → {@code getDimensions().eyeHeight()} 来的。
 * 改 {@code getBaseDimensions} 而不是 {@code getDimensions} —— 后者在 LivingEntity 里是 final。
 */
@Mixin(LivingEntity.class)
public class MorphSizeMixin {

    @Inject(method = "getBaseDimensions", at = @At("HEAD"), cancellable = true)
    private void randomperks$pigDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!MorphClientState.isMorphed(self.getId())) return;

        cir.setReturnValue(EntityType.PIG.getDimensions());
    }
}

package com.randomperks.mixin;

import com.randomperks.game.MorphState;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变猪" 的碰撞箱伪装 —— 变猪期间用猪的尺寸 (0.6 × 0.9)。
 *
 * ★ 必须挂在 {@link PlayerEntity} 上, 不能挂 {@link net.minecraft.entity.LivingEntity}:
 *   PlayerEntity **自己重写了** {@code getBaseDimensions}, 而且是直接查
 *   POSE_DIMENSIONS 表、不调用 super —— 挂在父类上对玩家完全不生效。
 *
 * 高于 0.9 的姿势不覆盖睡姿 / 死亡姿势, 免得躺下或倒地时也被压成猪的高度。
 *
 * 碰撞箱属于"服务端权威 + 客户端预测"的东西, 所以 [MorphState] 两端各记一份;
 * 状态变化时由调用方主动 {@code calculateDimensions()} 刷新已缓存的 boundingBox。
 */
@Mixin(PlayerEntity.class)
public class MorphDimensionsMixin {

    @Inject(method = "getBaseDimensions", at = @At("HEAD"), cancellable = true)
    private void randomperks$pigDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose == EntityPose.SLEEPING || pose == EntityPose.DYING) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        if (MorphState.isMorphed(self.getId())) {
            cir.setReturnValue(EntityDimensions.fixed(MorphState.PIG_WIDTH, MorphState.PIG_HEIGHT));
        }
    }
}

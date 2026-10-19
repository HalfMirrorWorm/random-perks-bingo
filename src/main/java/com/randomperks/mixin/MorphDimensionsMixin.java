package com.randomperks.mixin;

import com.randomperks.game.MorphState;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变群友" 的碰撞箱伪装 —— 变猪期间用猪的尺寸 (0.6 × 1.4)。
 *
 * 参考 sparktraits 的 PigPlayerDimensionsMixin, 拦的是 {@code getBaseDimensions} 而不是
 * {@code getDimensions}: 后者是 final 的, 而且会在返回值上再乘一遍 scale。
 * 睡姿 / 死亡姿势不覆盖, 免得躺下或倒地时也被压成猪的高度。
 *
 * 碰撞箱属于"服务端权威 + 客户端预测"的东西, 所以 [MorphState] 两端各记一份 ——
 * 只改一端的话, 客户端预测自己能钻过去、服务端却判定撞墙, 走位会被拉回来 (橡皮筋)。
 */
@Mixin(LivingEntity.class)
public class MorphDimensionsMixin {

    @Inject(method = "getBaseDimensions", at = @At("HEAD"), cancellable = true)
    private void randomperks$pigDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose == EntityPose.SLEEPING || pose == EntityPose.DYING) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (MorphState.isMorphed(self.getId())) {
            cir.setReturnValue(EntityDimensions.fixed(MorphState.PIG_WIDTH, MorphState.PIG_HEIGHT));
        }
    }

    /**
     * 变形结束的那一 tick 把 boundingBox 恢复。
     *
     * 光把 getBaseDimensions 的返回值改回去是不够的: Entity 的 boundingBox 是缓存的,
     * 只在 setPose / calculateDimensions 时重算。漏掉这一步, 玩家变回来之后碰撞箱
     * 还是猪的大小 (能钻进 1.5 格高的洞)。
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void randomperks$refreshBoxAfterMorph(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (MorphState.consumeExpired(self.getId())) {
            self.calculateDimensions();
        }
    }
}

package com.randomperks.mixin;

import com.randomperks.effect.PermanentEffects;
import java.util.Collection;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "永久效果"的事件驱动重挂。
 *
 * 所有效果移除路径 (主动移除 removeStatusEffect / 清空 clearStatusEffects / 自然到期)
 * 最终都会汇聚到 LivingEntity.onStatusEffectsRemoved —— 在这里通知 PermanentEffects,
 * 若玩家仍持有对应词条则立即重挂 (无周期轮询)。
 *
 * 覆盖的关键场景: ∞ 效果被同类药水/信标 (等级更高或时长更长) 覆盖成有限时长后,
 * 到期消失时也会走这里, 从而自动恢复到 ∞。
 */
@Mixin(LivingEntity.class)
public class EffectPersistMixin {

    @Inject(method = "onStatusEffectsRemoved(Ljava/util/Collection;)V", at = @At("TAIL"))
    private void randomperks$persistEffects(Collection<StatusEffectInstance> effects, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        PermanentEffects.INSTANCE.onEffectsRemoved(player, effects);
    }
}

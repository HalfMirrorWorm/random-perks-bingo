package com.randomperks.mixin;

import com.randomperks.effect.PermanentEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "永久效果"的事件驱动重挂: 效果被原版机制移除/清空时通知 PermanentEffects,
 * 若玩家仍持有对应词条则立即重挂 (无周期轮询)。
 *
 * 只挂钩公开方法 —— 效果自然到期的内部路径 (removeStatusEffectInternal) 不受影响。
 */
@Mixin(LivingEntity.class)
public class EffectPersistMixin {

    @Inject(method = "removeStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z", at = @At("TAIL"))
    private void randomperks$persistOne(RegistryEntry<StatusEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        PermanentEffects.INSTANCE.onEffectRemoved(player, effect);
    }

    @Inject(method = "clearStatusEffects()Z", at = @At("TAIL"))
    private void randomperks$persistAll(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        PermanentEffects.INSTANCE.onEffectsCleared(player);
    }
}

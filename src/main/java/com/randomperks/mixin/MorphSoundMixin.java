package com.randomperks.mixin;

import com.randomperks.game.MorphState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变群友" 的叫声伪装 —— 变猪期间受伤 / 死亡发出的都是猪叫。
 *
 * 拦的是 {@link LivingEntity} 上的默认实现。玩家和大多数生物都不 override 这两个方法
 * (mappings 里 getHurtSound / getDeathSound 只在 LivingEntity 声明), 所以一处就够;
 * 少数自带音效的生物 (比如猪自己) 本来就叫得像猪, 覆盖不到也无所谓。
 *
 * 变形瞬间那一声"哼"不在这里 —— 那个由服务端的 [com.randomperks.game.MorphService] 广播。
 */
@Mixin(LivingEntity.class)
public class MorphSoundMixin {

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void randomperks$pigHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (MorphState.isMorphed(self.getId())) {
            cir.setReturnValue(SoundEvents.ENTITY_PIG_HURT);
        }
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void randomperks$pigDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (MorphState.isMorphed(self.getId())) {
            cir.setReturnValue(SoundEvents.ENTITY_PIG_DEATH);
        }
    }
}

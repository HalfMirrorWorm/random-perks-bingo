package com.randomperks.mixin;

import com.randomperks.game.MorphState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变猪" 的叫声伪装 —— 变猪期间受伤 / 死亡发出的是猪叫。
 *
 * ★ 和碰撞箱同理: {@link PlayerEntity} 自己重写了这两个方法 (受伤音直接按伤害类型取,
 *   死亡音固定玩家音效), 都不调用 super —— 所以必须挂在 PlayerEntity 上。
 */
@Mixin(PlayerEntity.class)
public class MorphSoundMixin {

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void randomperks$pigHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (MorphState.isMorphed(self.getUuid())) {
            cir.setReturnValue(SoundEvents.ENTITY_PIG_HURT);
        }
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void randomperks$pigDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (MorphState.isMorphed(self.getUuid())) {
            cir.setReturnValue(SoundEvents.ENTITY_PIG_DEATH);
        }
    }
}

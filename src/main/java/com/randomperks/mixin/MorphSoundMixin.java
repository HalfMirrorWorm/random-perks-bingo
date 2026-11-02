package com.randomperks.mixin;

import com.randomperks.game.MorphState;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.pig.PigSoundVariants;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "变猪" 的叫声伪装 —— 变猪期间受伤 / 死亡发出的是猪叫。
 *
 * ★ 和碰撞箱同理: {@link Player} 自己重写了这两个方法 (受伤音直接按伤害类型取,
 *   死亡音固定玩家音效), 都不调用 super —— 所以必须挂在 PlayerEntity 上。
 */
@Mixin(Player.class)
public class MorphSoundMixin {

    private static SoundEvent pigHurt;
    private static SoundEvent pigDeath;

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void randomperks$pigHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        Player self = (Player) (Object) this;
        if (MorphState.isMorphed(self.getUUID())) {
            cir.setReturnValue(pigSound(self, true));
        }
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void randomperks$pigDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        Player self = (Player) (Object) this;
        if (MorphState.isMorphed(self.getUUID())) {
            cir.setReturnValue(pigSound(self, false));
        }
    }

    /** 成年经典猪的受伤/死亡音。26.3 起没有单独的 PIG_HURT 常量。 */
    private static SoundEvent pigSound(Player player, boolean hurt) {
        if (pigHurt == null) {
            var sounds = player.level().registryAccess()
                    .lookupOrThrow(Registries.PIG_SOUND_VARIANT)
                    .getValueOrThrow(PigSoundVariants.CLASSIC)
                    .adultSounds();
            pigHurt = sounds.hurtSound().value();
            pigDeath = sounds.deathSound().value();
        }
        return hurt ? pigHurt : pigDeath;
    }
}

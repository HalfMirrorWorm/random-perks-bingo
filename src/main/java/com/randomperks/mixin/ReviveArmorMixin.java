package com.randomperks.mixin;

import com.randomperks.game.ReviveArmorService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "复活甲": 在 {@code LivingEntity.tryUseDeathProtector} 的 HEAD 拦截。
 *
 * <p>原版只在生命值归零后调用这个方法 (随后决定是死亡还是被图腾救下),
 * 正好是"受到致命伤害"的时机。持有词条且冷却就绪时由
 * {@link ReviveArmorService} 复刻图腾结算并返回 true, 跳过死亡。
 */
@Mixin(LivingEntity.class)
public class ReviveArmorMixin {

    @Inject(method = "tryUseDeathProtector", at = @At("HEAD"), cancellable = true)
    private void randomperks$reviveArmor(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        if (!ReviveArmorService.INSTANCE.tryTrigger(player, source)) return;
        cir.setReturnValue(true);
    }
}

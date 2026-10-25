package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "不痛不痒": 最终伤害 (护甲 / 保护附魔 / 抗性之后) 在 2~5 点之间时, 改成 1 点。
 *
 * <p>注入 {@code modifyAppliedDamage} 的 RETURN —— 这个方法正是原版在扣血前计算
 * 最终伤害的一步, 直接修改返回值即可, 吸收值 / 伤害统计等后续逻辑全部保持原版。
 * 0~1 点的小伤害不再改动 (避免把 0.5 变成 1), 无视无敌的伤害也不处理。
 */
@Mixin(LivingEntity.class)
public class SmallDamageGuardMixin {

    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void randomperks$clampSmallDamage(
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Float> cir
    ) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        if (!PerkBoostQuery.INSTANCE.hasSmallDamageGuard(player)) return;
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        float modified = cir.getReturnValue();
        if (modified > 1.0F && modified <= 5.0F) {
            cir.setReturnValue(1.0F);
        }
    }
}

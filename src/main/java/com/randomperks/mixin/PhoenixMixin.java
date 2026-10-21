package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "不死鸟" (紫 · 深掘): 持有词条的玩家**免疫一切火焰类伤害**。
 *
 * <p>覆盖范围就是原版 {@code minecraft:is_fire} 伤害标签 —— 站在火里 / 泡岩浆 /
 * 踩岩浆块 / 营火 / 烈焰人的火球都算。
 *
 * <p>拦截 {@code LivingEntity.damage} 的 HEAD 直接返回 false, 而不是挂一个永久的
 * "抗火" 状态效果: 效果会被牛奶清掉, 还会占一个 HUD 图标; 直接免疫更符合"不死鸟"的语义。
 *
 * <p>方法签名写全描述符: {@code LivingEntity} 上有多个 damage 重载, 只写方法名会有歧义。
 */
@Mixin(LivingEntity.class)
public class PhoenixMixin {

    @Inject(
            method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void randomperks$phoenixFireImmunity(ServerWorld world, DamageSource source, float amount,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        if (!source.isIn(DamageTypeTags.IS_FIRE)) return;
        if (!PerkBoostQuery.INSTANCE.hasPhoenix(player)) return;

        cir.setReturnValue(false);
    }
}

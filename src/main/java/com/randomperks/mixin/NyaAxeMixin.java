package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * "喵人斧": 手持秒人斧命中任意生物 (含玩家) 时, 把这一击的伤害抬到必杀值。
 *
 * 判定 (是否持有词条 / 手持的是不是词条发的那把) 与消耗都在
 * PerkBoostQuery.applyNyaAxe 里完成, 这里只负责把伤害值换掉。
 */
@Mixin(LivingEntity.class)
public class NyaAxeMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float randomperks$lethalStrike(float amount, ServerWorld world, DamageSource source) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity player)) return amount;
        return PerkBoostQuery.INSTANCE.applyNyaAxe(player, amount);
    }
}

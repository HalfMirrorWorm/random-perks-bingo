package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * "贪婪之手"词条的代价: 持有该词条的玩家受到**其他生物 (非玩家)** 的 2 倍伤害。
 */
@Mixin(LivingEntity.class)
public class LivingEntityDamageMixin {

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float randomperks$doubleIncomingDamage(float amount, ServerWorld world, DamageSource source) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return amount;
        if (!PerkBoostQuery.INSTANCE.hasFragileToMobs(player)) return amount;

        Entity attacker = source.getAttacker();
        if (attacker instanceof LivingEntity && !(attacker instanceof PlayerEntity)) {
            return amount * 2.0f;
        }
        return amount;
    }
}

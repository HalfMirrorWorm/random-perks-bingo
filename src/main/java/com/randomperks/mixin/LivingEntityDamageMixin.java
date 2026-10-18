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
 * 玩家受到伤害时的若干词条修正:
 * <ul>
 *   <li>"贪婪之手" —— 受到**其他生物 (非玩家)** 的 2 倍伤害</li>
 *   <li>"Jockey" —— 骑在别人身上时, 受到来自**被骑者**的伤害 -75%</li>
 * </ul>
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

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float randomperks$reduceDamageFromCarrier(float amount, ServerWorld world, DamageSource source) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return amount;
        if (!PerkBoostQuery.INSTANCE.hasJockey(player)) return amount;

        // 只有"被自己骑着的那个玩家"打过来才减伤 (喷溅伤害药水是唯一常见触发方式)
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof ServerPlayerEntity)) return amount;
        if (player.getVehicle() != attacker) return amount;

        return amount * 0.25f;
    }
}

package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.EntityStatuses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "救生桶"词条: 仅在受到**摔落伤害**时, 副手的水桶会像不死图腾一样救你一命
 * (消耗水桶, 留下空桶; 应用与图腾一致的回复/吸收/防火效果与动画)。
 */
@Mixin(LivingEntity.class)
public class FallBucketMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void randomperks$fallBucketTotem(
            ServerWorld world,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        if (!source.isIn(DamageTypeTags.IS_FALL)) return;
        if (!PerkBoostQuery.INSTANCE.hasFallBucket(player)) return;

        ItemStack offhand = player.getOffHandStack();
        if (!offhand.isOf(Items.WATER_BUCKET)) return;

        // 消耗水桶 (留下空桶)
        offhand.decrement(1);
        if (offhand.isEmpty()) {
            player.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.BUCKET));
        }

        // 与不死图腾一致的效果
        player.setHealth(1.0f);
        player.clearStatusEffects();
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 900, 1, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 100, 1, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 800, 0, true, true));
        world.sendEntityStatus(player, EntityStatuses.USE_TOTEM_OF_UNDYING);

        cir.setReturnValue(false);
    }
}

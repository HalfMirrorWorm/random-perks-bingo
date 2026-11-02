package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import com.randomperks.network.PerkNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "落地，水"词条: 受到**致命**摔落伤害时, **背包里任意位置**的水桶代替不死图腾救命。
 *
 * 挂钩原版 LivingEntity.tryUseDeathProtector —— 该方法只在"这一下会致死"时被调用
 * (且在护甲/减伤结算之后), 天然满足"仅致死触发"。
 * 演出与原版图腾一致, 仅把上浮旋转的物品换成水桶 (自定义 S2C → GameRenderer.showFloatingItem)。
 */
@Mixin(LivingEntity.class)
public class FallBucketMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void randomperks$fallBucketTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayer player)) return;
        if (!source.is(DamageTypeTags.IS_FALL)) return;
        if (!PerkBoostQuery.INSTANCE.hasFallBucket(player)) return;

        // 背包里任意位置有水桶即可 (不用非得放副手)
        int bucketSlot = findWaterBucket(player);
        if (bucketSlot < 0) return;

        // 只消耗水: 那一格的水桶 → 空桶 (桶保留在原位)
        player.getInventory().setItem(bucketSlot, new ItemStack(Items.BUCKET));

        // 与不死图腾一致的效果
        player.setHealth(1.0f);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0, true, true));

        // 音效 + 水桶版"图腾演出"
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        PerkNetworking.INSTANCE.sendWaterBucketTotem(player);

        cir.setReturnValue(true);  // 已救命: 不走原版图腾/死亡流程
    }

    /** 背包里第一个水桶所在的格子 (快捷栏 + 主背包 + 护甲 + 副手), 没有返回 -1 */
    private static int findWaterBucket(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(Items.WATER_BUCKET)) return i;
        }
        return -1;
    }
}

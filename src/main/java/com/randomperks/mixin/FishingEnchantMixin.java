package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "渔者之幸" —— 持有词条的玩家钓鱼时, 浮标默认获得 **钓饵 III** 与 **海之眷顾 III**,
 * 不需要鱼竿附魔 (鱼竿本身附魔更高时保留更高等级)。
 *
 * <p>这两个值在浮标创建时就写进 final 字段, 之后只读, 所以在构造函数末尾直接抬到 3。
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingEnchantMixin {

    @Mutable
    @Accessor("luckBonus")
    abstract void randomperks$setLuckBonus(int value);

    @Mutable
    @Accessor("waitTimeReductionTicks")
    abstract void randomperks$setWaitTimeReduction(int value);

    @Inject(
            method = "<init>(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/World;II)V",
            at = @At("TAIL")
    )
    private void randomperks$grantFishingEnchants(PlayerEntity player, World world, int luckBonus,
                                                  int waitTimeReductionTicks, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity owner)) return;
        if (!PerkBoostQuery.INSTANCE.hasLuckyFishing(owner)) return;

        this.randomperks$setLuckBonus(Math.max(luckBonus, 3));
        this.randomperks$setWaitTimeReduction(Math.max(waitTimeReductionTicks, 3));
    }
}

package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Predicate;

/**
 * "老吴!?": 猫**不再害怕**持有该词条的玩家 —— 但依然会怕其他玩家。
 *
 * 做法: CatFleeGoal 构造时把 predicate 传给 FleeEntityGoal (原版是 EXCEPT_CREATIVE_OR_SPECTATOR,
 * 即"猫会躲开所有非创造/旁观玩家")。这里在原 predicate 上再叠加一层"排除持词条的玩家",
 * 于是猫的"害怕名单"里就没有这些人了 —— 其他玩家照常会让它逃跑。
 */
@Mixin(targets = "net.minecraft.entity.passive.CatEntity$CatFleeGoal")
public class CatFleeMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private Predicate<LivingEntity> randomperks$ignoreOldWu(Predicate<LivingEntity> original) {
        return original.and(entity ->
            !(entity instanceof ServerPlayerEntity player && PerkBoostQuery.INSTANCE.hasMeow(player))
        );
    }
}

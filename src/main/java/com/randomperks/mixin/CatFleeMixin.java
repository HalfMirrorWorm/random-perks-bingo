package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * "老吴!?": 猫**不再害怕**持有该词条的玩家 —— 但依然会怕其他玩家。
 *
 * CatFleeGoal 的构造器只接 (cat, Class, float, double, double) —— **没有 Predicate 参数**,
 * predicate 是在字节码里用 EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR + lambda 现造的。
 * 所以不能用 @ModifyVariable(argsOnly) 去改"参数", 只能拦它传给 FleeEntityGoal 构造器的
 * 那个 predicate 实参 (super(...) 的最后一个参数)。
 *
 * handler 必须是 static: 注入点在 super() 之前, 此时 this 尚未初始化。
 */
@Mixin(targets = "net.minecraft.world.entity.animal.feline.Cat$CatAvoidEntityGoal")
public class CatFleeMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/AvoidEntityGoal;<init>(Lnet/minecraft/world/entity/PathfinderMob;Ljava/lang/Class;FDDLjava/util/function/Predicate;)V"
            ),
            index = 5)
    private static Predicate<LivingEntity> randomperks$ignoreOldWu(Predicate<LivingEntity> original) {
        return original.and(entity ->
            !(entity instanceof ServerPlayer player && PerkBoostQuery.INSTANCE.hasYowl(player))
        );
    }
}

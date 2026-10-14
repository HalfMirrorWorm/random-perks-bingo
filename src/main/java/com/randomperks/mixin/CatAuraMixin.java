package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "哈基米"词条: 苦力怕对拥有该词条的玩家等效于对猫 —— 不生成任何猫实体。
 *
 * 苦力怕的"怕猫"goal 在本 tick 找不到真猫时 (targetEntity 仍为 null),
 * 把范围内最近的该玩家设为逃跑目标, 之后原版逻辑会照常计算逃跑路径并执行。
 */
@Mixin(FleeEntityGoal.class)
public abstract class CatAuraMixin {

    @Shadow
    @Final
    protected PathAwareEntity mob;

    @Shadow
    @Final
    protected float fleeDistance;

    @Shadow
    @Final
    protected Class<?> classToFleeFrom;

    @Shadow
    protected LivingEntity targetEntity;

    @Inject(
            method = "canStart",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/ai/goal/FleeEntityGoal;targetEntity:Lnet/minecraft/entity/LivingEntity;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void randomperks$catAuraFlee(CallbackInfoReturnable<Boolean> cir) {
        if (this.targetEntity != null) return;
        if (!(this.mob instanceof CreeperEntity)) return;
        if (this.classToFleeFrom != CatEntity.class) return;

        PlayerEntity nearest = this.mob.getWorld().getClosestPlayer(this.mob, this.fleeDistance);
        if (nearest == null || !nearest.isAlive()) return;
        if (PerkBoostQuery.INSTANCE.hasCatAura(nearest)) {
            this.targetEntity = nearest;
        }
    }
}

package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
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
@Mixin(AvoidEntityGoal.class)
public abstract class CatAuraMixin {

    @Shadow
    @Final
    protected PathfinderMob mob;

    @Shadow
    @Final
    protected float maxDist;

    @Shadow
    @Final
    protected Class<?> avoidClass;

    @Shadow
    protected LivingEntity toAvoid;

    @Inject(
            method = "canUse",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/goal/AvoidEntityGoal;toAvoid:Lnet/minecraft/world/entity/LivingEntity;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void randomperks$catAuraFlee(CallbackInfoReturnable<Boolean> cir) {
        if (this.toAvoid != null) return;
        if (!(this.mob instanceof Creeper)) return;
        if (this.avoidClass != Cat.class) return;

        // 找**范围内最近且持有词条**的玩家 —— 不能先取"最近的玩家"再看有没有词条,
        // 否则旁边站着一个没词条的玩家时, 稍远一点的持有者永远不会被当成逃跑目标
        double maxSqr = (double) this.maxDist * this.maxDist;
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : this.mob.level().players()) {
            if (!player.isAlive() || !PerkBoostQuery.INSTANCE.hasCatAura(player)) continue;
            double dist = player.distanceToSqr(this.mob);
            if (dist <= maxSqr && dist < best) {
                best = dist;
                nearest = player;
            }
        }
        if (nearest != null) {
            this.toAvoid = nearest;
        }
    }
}

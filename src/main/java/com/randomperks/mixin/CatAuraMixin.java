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

        Player nearest = this.mob.level().getNearestPlayer(this.mob, this.maxDist);
        if (nearest == null || !nearest.isAlive()) return;
        if (PerkBoostQuery.INSTANCE.hasCatAura(nearest)) {
            this.toAvoid = nearest;
        }
    }
}

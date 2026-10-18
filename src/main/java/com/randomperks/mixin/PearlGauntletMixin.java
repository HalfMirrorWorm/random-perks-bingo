package com.randomperks.mixin;

import com.randomperks.game.PerkBoostQuery;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "珠光护手" —— 每次攻击都暴击。
 *
 * 原版的暴击判定是 {@code PlayerEntity.attack} 里一长串条件:
 * {@code fallDistance > 0 && !onGround && !isClimbing() && !isTouchingWater()
 *  && !hasStatusEffect(BLINDNESS) && !hasVehicle() && target instanceof LivingEntity
 *  && !isSprinting()}
 * 满足后把伤害 ×1.5 并触发暴击粒子。
 *
 * 与其去改那个局部变量 (要赌 ordinal/变量名, 版本一升就可能错位), 不如把条件里
 * **每个会因人而异的分支**单独 redirect 成"对持词条者恒为真"。
 *
 * 注: target 的 owner 必须写 {@code PlayerEntity} —— javac 用的是静态接收者类型,
 * 字节码里这些调用/字段读的 owner 就是 PlayerEntity (哪怕方法实际声明在父类上)。
 *
 * 没有拦截的两项: 失明 (本来就是削弱攻击的 debuff) 和骑乘 (骑在别人身上挥不了剑)。
 */
@Mixin(PlayerEntity.class)
public class PearlGauntletMixin {

    @Unique
    private boolean randomperks$forceCrit() {
        return PerkBoostQuery.INSTANCE.hasPearlGauntlet((PlayerEntity) (Object) this);
    }

    /** fallDistance > 0 */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;fallDistance:D",
                    opcode = Opcodes.GETFIELD))
    private double randomperks$critFallDistance(PlayerEntity player) {
        return randomperks$forceCrit() ? 1.0 : player.fallDistance;
    }

    /** !isOnGround() */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;isOnGround()Z"))
    private boolean randomperks$critOnGround(PlayerEntity player) {
        return !randomperks$forceCrit() && player.isOnGround();
    }

    /** !isClimbing() */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;isClimbing()Z"))
    private boolean randomperks$critClimbing(PlayerEntity player) {
        return !randomperks$forceCrit() && player.isClimbing();
    }

    /** !isTouchingWater() */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;isTouchingWater()Z"))
    private boolean randomperks$critTouchingWater(PlayerEntity player) {
        return !randomperks$forceCrit() && player.isTouchingWater();
    }

    /** !isSprinting() */
    @Redirect(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;isSprinting()Z"))
    private boolean randomperks$critSprinting(PlayerEntity player) {
        return !randomperks$forceCrit() && player.isSprinting();
    }
}

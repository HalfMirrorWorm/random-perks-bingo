package com.randomperks.game

import net.minecraft.entity.Entity
import net.minecraft.world.explosion.Explosion
import net.minecraft.world.explosion.ExplosionBehavior
import kotlin.math.sqrt

/**
 * "无忧的安眠" 的爆炸伤害行为: 范围翻倍 (power 5 -> 10) 的同时,
 * 伤害按**原版床爆炸 (power 5)** 的公式计算, 再乘 1.5。
 *
 * <p>为什么不直接让 power 翻倍了事: 原版伤害公式是
 * {@code ((e*e + e) / 2) * 7 * (power*2) + 1}, 其中 {@code e = (1 - distance/(power*2)) * exposure}。
 * power 从 5 提到 10 后伤害会变成约 2 倍且衰减更慢 —— 想做到"范围 2 倍、伤害 1.5 倍"，
 * 就得用原来的半径重新算一遍伤害。
 *
 * <p>超出原版半径 (10 格) 的目标伤害为 0（原版本来也打不到），但依然会被击退、
 * 方块也依然会被炸掉 —— 这正是"范围翻倍"的部分。
 */
class BedBombBehavior : ExplosionBehavior() {

    override fun calculateDamage(explosion: Explosion, entity: Entity, amount: Float): Float {
        val vanillaRadius = VANILLA_POWER * 2.0
        val distance = sqrt(entity.squaredDistanceTo(explosion.position)) / vanillaRadius
        val e = (1.0 - distance) * amount
        val vanillaDamage = (e * e + e) / 2.0 * 7.0 * vanillaRadius + 1.0
        return maxOf(0.0, vanillaDamage * DAMAGE_MULTIPLIER).toFloat()
    }

    companion object {
        /** 原版床爆炸的威力 */
        private const val VANILLA_POWER = 5.0f

        /** 伤害倍率 */
        private const val DAMAGE_MULTIPLIER = 1.5
    }
}

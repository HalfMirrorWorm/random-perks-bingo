package com.randomperks.game

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.ExplosionDamageCalculator
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
 * <p>原版半径内 (10 格) 保持 1.5 倍原版伤害; 10~20 格补一段**递减伤害**,
 * 从 10 格处的 1.5 点平滑降到 20 格处的 0, 并保留视线遮挡系数。
 * 这样"范围翻倍"不仅是炸方块/击退, 伤害范围也真正覆盖到 20 格。
 */
class BedBombBehavior : ExplosionDamageCalculator() {

    override fun getEntityDamageAmount(explosion: Explosion, entity: Entity, amount: Float): Float {
        val vanillaRadius = VANILLA_POWER * 2.0
        val distance = sqrt(entity.distanceToSqr(explosion.center()))

        // 原版 power 5 半径内: 完全沿用原版公式, 再乘 1.5
        if (distance <= vanillaRadius) {
            val e = (1.0 - distance / vanillaRadius) * amount
            val vanillaDamage = (e * e + e) / 2.0 * 7.0 * vanillaRadius + 1.0
            return maxOf(0.0, vanillaDamage * DAMAGE_MULTIPLIER).toFloat()
        }

        // 原版半径外 ~ 翻倍半径: 从 1.5 递减到 0。
        // 乘以"距离衰减 × 视线遮挡"的组合系数: 无遮挡时线性衰减,
        // 完全被遮挡时按距离平方衰减, 保证 10 格处仍与内圈连续。
        val t = ((distance - vanillaRadius) / vanillaRadius).coerceIn(0.0, 1.0)
        val visibility = amount.coerceIn(0f, 1f).toDouble()
        val falloff = (1.0 - t) * (visibility + (1.0 - visibility) * (1.0 - t))
        return (DAMAGE_MULTIPLIER * falloff).toFloat()
    }

    companion object {
        /** 原版床爆炸的威力 */
        private const val VANILLA_POWER = 5.0f

        /** 伤害倍率 */
        private const val DAMAGE_MULTIPLIER = 1.5
    }
}

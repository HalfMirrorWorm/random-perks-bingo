package com.randomperks.game

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.tags.DamageTypeTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel

/**
 * "忠心护主" (紫 · 唤法) 的服务端逻辑。
 *
 * 玩家受到伤害时, 找一只附近**属于该玩家**的已驯服生物 (狼/猫/鹦鹉/马等),
 * 让宠物替玩家承受 **2 倍**伤害, 玩家本身免疫这次伤害。
 * 没有宠物在附近 / 伤害无视无敌 (虚空、/kill 等) 时不生效。
 */
object LoyalGuardService {

    const val PERK_ID = "summon_loyal_guard"

    /** 宠物搜索半径 (格) */
    private const val PET_RANGE = 16.0

    /**
     * 尝试把这次伤害转嫁给宠物。
     *
     * @return true 表示已转嫁, 调用方应取消玩家受到的伤害
     */
    fun tryRedirect(player: ServerPlayer, source: DamageSource, amount: Float): Boolean {
        if (amount <= 0.0f) return false
        if (!PerkSession.isEnabled()) return false
        if (!PerkBoostQuery.hasLoyalGuard(player)) return false

        // 与图腾/复活甲一致: 无视无敌的伤害不转嫁
        if (source.`is`(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false

        val pet = findPet(player) ?: return false
        val world = pet.level() as? ServerLevel ?: return false

        // 宠物承受 2 倍伤害; 玩家本次伤害由调用方取消
        pet.hurtServer(world, source, amount * 2.0f)
        return true
    }

    /** 最近的、属于该玩家的存活宠物 */
    private fun findPet(player: ServerPlayer): TamableAnimal? =
        player.level().getEntitiesOfClass(
            TamableAnimal::class.java,
            player.boundingBox.inflate(PET_RANGE),
        ) { pet ->
            pet.isAlive && !pet.isRemoved && pet.owner?.uuid == player.uuid
        }.minByOrNull { it.distanceToSqr(player) }
}

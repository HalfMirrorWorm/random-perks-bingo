package com.randomperks.game

import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.passive.TameableEntity
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld

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
    fun tryRedirect(player: ServerPlayerEntity, source: DamageSource, amount: Float): Boolean {
        if (amount <= 0.0f) return false
        if (!PerkSession.isEnabled()) return false
        if (!PerkBoostQuery.hasLoyalGuard(player)) return false

        // 与图腾/复活甲一致: 无视无敌的伤害不转嫁
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false

        val pet = findPet(player) ?: return false
        val world = pet.world as? ServerWorld ?: return false

        // 宠物承受 2 倍伤害; 玩家本次伤害由调用方取消
        pet.damage(world, source, amount * 2.0f)
        return true
    }

    /** 最近的、属于该玩家的存活宠物 */
    private fun findPet(player: ServerPlayerEntity): TameableEntity? =
        player.world.getEntitiesByClass(
            TameableEntity::class.java,
            player.boundingBox.expand(PET_RANGE),
        ) { pet ->
            pet.isAlive && !pet.isRemoved && pet.owner?.uuid == player.uuid
        }.minByOrNull { it.squaredDistanceTo(player) }
}

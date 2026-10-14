package com.randomperks.state

import java.util.UUID

/**
 * 所有玩家在本局中的词条状态。单例, 局间重置。
 */
object PerkStateManager {
    private val states = mutableMapOf<UUID, PlayerPerkState>()

    fun getOrCreate(uuid: UUID): PlayerPerkState = states.getOrPut(uuid) { PlayerPerkState(uuid) }

    fun get(uuid: UUID): PlayerPerkState? = states[uuid]

    fun all(): Collection<PlayerPerkState> = states.values

    /** 开局/结束/重置时清空所有状态 */
    fun resetAll() {
        states.values.forEach { it.reset() }
    }

    fun forget(uuid: UUID) {
        states.remove(uuid)
    }

    /** 该词条是否已被任意玩家拥有 (用于神话品质的全局唯一性检查) */
    fun isOwnedByAnyone(perkId: String): Boolean = all().any { it.hasPerk(perkId) }

    /** 当前被任意玩家持有的神话词条 id 集合 (发放选项时排除, 避免多人同时刷出) */
    fun globallyOwnedMythicIds(): Set<String> =
        all().flatMap { it.ownedPerks.toList() }
            .filter { it.rarity == com.randomperks.perk.PerkRarity.MYTHIC }
            .mapTo(mutableSetOf()) { it.id }
}

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
}

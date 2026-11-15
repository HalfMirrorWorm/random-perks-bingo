package com.randomperks.game

import com.randomperks.state.PlayerPerkState

/**
 * "刷新"特质的词条刷新次数。
 *
 * <p>存在 [PlayerPerkState.memory] 里, 随现有状态持久化; 每次在三选一界面点击刷新消耗 1 次。
 */
object PerkRefreshCharges {

    const val INITIAL = 5

    private const val KEY = "perk_refresh_charges"

    fun grant(state: PlayerPerkState) {
        state.memory[KEY] = INITIAL.toString()
    }

    fun charges(state: PlayerPerkState): Int =
        state.memory[KEY]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    fun consume(state: PlayerPerkState): Boolean {
        val current = charges(state)
        if (current <= 0) return false
        state.memory[KEY] = (current - 1).toString()
        return true
    }

    fun clear(state: PlayerPerkState) {
        state.memory.remove(KEY)
    }
}

package com.randomperks.roll

import com.randomperks.perk.PerkArchetype
import com.randomperks.state.PlayerPerkState

/**
 * "专精"特质: 玩家选定一个词条流派后, 之后每次普通三选一都至少包含一个该流派的词条。
 *
 * <p>选择存在 [PlayerPerkState.memory], 随现有词条状态存档持久化,
 * 也会被"逆转时间"的快照一起回退。
 */
object PerkRollBias {

    const val KEY = "roll_bias_archetype"

    fun bias(state: PlayerPerkState): PerkArchetype? {
        val name = state.memory[KEY] ?: return null
        val archetype = runCatching { PerkArchetype.valueOf(name) }.getOrNull() ?: return null
        return archetype.takeIf { it != PerkArchetype.TRAIT }
    }

    fun set(state: PlayerPerkState, archetype: PerkArchetype) {
        state.memory[KEY] = archetype.name
    }

    fun clear(state: PlayerPerkState) {
        state.memory.remove(KEY)
    }
}

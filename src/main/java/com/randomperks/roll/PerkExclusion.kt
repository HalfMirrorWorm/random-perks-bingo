package com.randomperks.roll

import com.randomperks.perk.PerkArchetype
import com.randomperks.state.PlayerPerkState

/**
 * "屏蔽"特质: 玩家选定一个流派后, 之后的普通词条选择不再出现该流派词条。
 *
 * <p>选择存在 [PlayerPerkState.memory], 随存档持久化, 也会被"逆转时间"快照回退。
 */
object PerkExclusion {

    const val PERK_ID = "trait_13"

    const val KEY = "excluded_archetype"

    fun archetype(state: PlayerPerkState): PerkArchetype? {
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

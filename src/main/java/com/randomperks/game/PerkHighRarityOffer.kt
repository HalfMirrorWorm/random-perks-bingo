package com.randomperks.game

import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import com.randomperks.roll.PerkRollBias
import com.randomperks.state.PlayerPerkState
import kotlin.random.Random

/**
 * "精选"特质: 让玩家接下来的一次普通词条选择只从**金 / 红**品质里抽 3 个。
 *
 * <p>两个标记:
 * - [NEXT_KEY]: 下一次普通三选一还没兑现;
 * - [CURRENT_KEY]: 当前队首的普通候选就是精选出来的 (刷新时继续保持金/红)。
 *
 * <p>标记存在 [PlayerPerkState.memory], 随现有状态持久化。
 */
object PerkHighRarityOffer {

    private const val NEXT_KEY = "next_high_rarity_offer"
    private const val CURRENT_KEY = "current_high_rarity_offer"

    fun isNextPending(state: PlayerPerkState): Boolean = state.memory[NEXT_KEY] == "1"

    fun markNext(state: PlayerPerkState) {
        state.memory[NEXT_KEY] = "1"
    }

    fun clearNext(state: PlayerPerkState) {
        state.memory.remove(NEXT_KEY)
    }

    fun isCurrentHigh(state: PlayerPerkState): Boolean = state.memory[CURRENT_KEY] == "1"

    fun markCurrent(state: PlayerPerkState) {
        state.memory[CURRENT_KEY] = "1"
    }

    fun clearCurrent(state: PlayerPerkState) {
        state.memory.remove(CURRENT_KEY)
    }

    /**
     * 从金/红池里抽最多 3 个 (排除已拥有、待选队列里的候选和 [extraExcluded])。
     * 池空时返回空列表, 调用方回退到普通抽取。
     */
    fun roll(state: PlayerPerkState, extraExcluded: Set<String> = emptySet()): List<PerkDefinition> {
        val excluded = state.ownedPerks.mapTo(mutableSetOf()) { it.id }
        excluded.addAll(extraExcluded)
        state.pendingOffers.forEach { excluded.addAll(it) }

        val pool = PerkRegistry.all.filter {
            (it.rarity == PerkRarity.GOLD || it.rarity == PerkRarity.RED) && it.id !in excluded
        }
        val options = pool.shuffled(Random.Default).take(3).toMutableList()

        // "专精"保底: 金/红池里若还有选定流派的词条, 保证它出现在候选里
        val archetype = PerkRollBias.bias(state)
        if (archetype != null && options.isNotEmpty() && options.none { it.archetype == archetype }) {
            val candidates = pool.filter { it.archetype == archetype && it !in options }
            if (candidates.isNotEmpty()) options[options.lastIndex] = candidates.random(Random.Default)
        }
        return options
    }
}

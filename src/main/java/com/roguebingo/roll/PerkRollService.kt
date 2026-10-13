package com.roguebingo.roll

import com.roguebingo.perk.PerkDefinition
import com.roguebingo.perk.PerkRarity
import com.roguebingo.perk.PerkRegistry
import com.roguebingo.state.PlayerPerkState
import kotlin.random.Random

/**
 * 三选一抽取逻辑。
 */
object PerkRollService {

    /**
     * 为单个玩家生成候选 (排除该玩家已获得的词条)。
     *
     * 每位玩家独立抽取 —— 每位队员的选项互不相同。
     */
    fun rollOptions(state: PlayerPerkState, count: Int = 3, random: Random = Random.Default): List<PerkDefinition> =
        rollOptionsExcluding(state.ownedPerks.mapTo(mutableSetOf()) { it.id }, count, random)

    private fun rollOptionsExcluding(excluded: Set<String>, count: Int, random: Random): List<PerkDefinition> {
        val pool = PerkRegistry.all.filterNot { it.id in excluded }.toMutableList()
        val result = mutableListOf<PerkDefinition>()

        repeat(minOf(count, pool.size)) {
            val rarity = rollRarity(random)
            val preferred = pool.filter { it.rarity == rarity }
            val candidates = preferred.ifEmpty { pool }
            if (candidates.isEmpty()) return@repeat
            val pick = candidates[random.nextInt(candidates.size)]
            pool.remove(pick)
            result.add(pick)
        }

        return result
    }

    fun rollRarity(random: Random = Random.Default): PerkRarity {
        val entries = PerkRarity.entries
        val total = entries.sumOf { it.weight }
        var roll = random.nextInt(total)
        for (rarity in entries) {
            roll -= rarity.weight
            if (roll < 0) return rarity
        }
        return entries.last()
    }
}

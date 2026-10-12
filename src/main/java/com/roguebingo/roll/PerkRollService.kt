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
     * 为玩家生成 [count] 个候选词条 (默认 3 个, 互不相同, 排除已获得的)。
     *
     * 规则: 每个选项独立按品质权重 roll 品质, 再从该品质的可用池中挑一个;
     * 该品质池为空时退化为从整体可用池中挑。
     */
    fun rollOptions(state: PlayerPerkState, count: Int = 3, random: Random = Random.Default): List<PerkDefinition> {
        val pool = PerkRegistry.all.filterNot { state.hasPerk(it.id) }.toMutableList()
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

package com.randomperks.roll

import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import com.randomperks.state.PlayerPerkState
import kotlin.random.Random

/**
 * 三选一抽取逻辑。
 */
object PerkRollService {

    /**
     * 为单个玩家生成候选 (每位玩家独立抽取)。
     *
     * 排除: 该玩家**已获得**的词条 + **待选队列里已出现**的词条 ——
     * 后者避免攒了多次机会时, 连续两次选择出现重复词条而浪费选项位。
     */
    fun rollOptions(state: PlayerPerkState, count: Int = 3, random: Random = Random.Default): List<PerkDefinition> {
        val excluded = state.ownedPerks.mapTo(mutableSetOf()) { it.id }
        state.pendingOffers.forEach { excluded.addAll(it) }
        return rollOptionsExcluding(excluded, count, random)
    }

    private fun rollOptionsExcluding(excluded: Set<String>, count: Int, random: Random): List<PerkDefinition> {
        val pool = PerkRegistry.all.filterNot { it.id in excluded }.toMutableList()
        val result = mutableListOf<PerkDefinition>()

        repeat(minOf(count, pool.size)) {
            // 只在"仍有可用词条"的品质中按权重抽取,
            // 避免 roll 到已耗尽的品质(如金色被抽完)后从全池乱挑导致概率失真
            val availableRarities = pool.map { it.rarity }.distinct()
            val rarity = rollRarity(availableRarities, random)

            val candidates = pool.filter { it.rarity == rarity }.ifEmpty { pool }
            if (candidates.isEmpty()) return@repeat
            val pick = candidates[random.nextInt(candidates.size)]
            pool.remove(pick)
            result.add(pick)
        }

        return result
    }

    /**
     * 按品质权重抽取。
     *
     * @param available 参与抽取的品质集合 (默认全部); 池中已耗尽的品质应从中剔除,
     *                  其权重会自动重新分配给其余品质
     */
    fun rollRarity(available: List<PerkRarity> = PerkRarity.entries, random: Random = Random.Default): PerkRarity {
        if (available.isEmpty()) return PerkRarity.BLUE
        val total = available.sumOf { it.weight }
        var roll = random.nextInt(total)
        for (rarity in available) {
            roll -= rarity.weight
            if (roll < 0) return rarity
        }
        return available.last()
    }
}

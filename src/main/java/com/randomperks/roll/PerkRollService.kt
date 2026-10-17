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
     * 排除: 该玩家**已获得**的词条 + **待选队列里已出现**的词条 + [extraExcluded]
     * (后者用于排除已被其他玩家持有的全局唯一词条, 如神话品质)。
     */
    fun rollOptions(
        state: PlayerPerkState,
        count: Int = 3,
        random: Random = Random.Default,
        extraExcluded: Set<String> = emptySet(),
    ): List<PerkDefinition> {
        val excluded = state.ownedPerks.mapTo(mutableSetOf()) { it.id }
        excluded.addAll(state.consumedPerks)   // 本局已用掉的一次性词条也不再出现
        excluded.addAll(extraExcluded)
        state.pendingOffers.forEach { excluded.addAll(it) }
        return rollOptionsExcluding(excluded, count, random)
    }

    /** 从排除集合之外随机抽一个词条 (用于"神话被抢后刷新选项位"等场景) */
    fun rollSingle(excluded: Set<String>, random: Random = Random.Default): PerkDefinition? =
        rollOptionsExcluding(excluded, 1, random).firstOrNull()

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

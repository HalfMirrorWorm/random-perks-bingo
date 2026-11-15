package com.randomperks.roll

import com.randomperks.perk.PerkArchetype
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
        excluded.addAll(extraExcluded)
        state.pendingOffers.forEach { excluded.addAll(it) }
        return rollOptionsExcluding(excluded, count, random, PerkRollBias.bias(state))
    }

    /** 从排除集合之外随机抽一个词条 (用于"神话被抢后刷新选项位"等场景) */
    fun rollSingle(excluded: Set<String>, random: Random = Random.Default): PerkDefinition? =
        rollOptionsExcluding(excluded, 1, random, null).firstOrNull()

    private fun rollOptionsExcluding(
        excluded: Set<String>,
        count: Int,
        random: Random,
        bias: PerkArchetype?,
    ): List<PerkDefinition> {
        // 特质只能通过"特质选择"获得, 不进入普通三选一池
        val pool = PerkRegistry.all
            .filterNot { it.id in excluded || it.archetype == PerkArchetype.TRAIT }
            .toMutableList()
        val result = mutableListOf<PerkDefinition>()

        repeat(minOf(count, pool.size)) {
            // 只在"仍有可用词条"的品质中按权重抽取,
            // 避免 roll 到已耗尽的品质(如金色被抽完)后从全池乱挑导致概率失真
            val availableRarities = pool.map { it.rarity }.distinct()
            val rarity = rollRarity(availableRarities, random)

            val candidates = pool.filter { it.rarity == rarity }.ifEmpty { pool }
            if (candidates.isEmpty()) return@repeat
            val pick = if (bias != null) {
                pickWeighted(candidates, bias, random)
            } else {
                candidates[random.nextInt(candidates.size)]
            }
            pool.remove(pick)
            result.add(pick)
        }

        return result
    }

    /** 在候选里按流派加权抽取 (选定的"专精"流派权重更高) */
    private fun pickWeighted(
        candidates: List<PerkDefinition>,
        bias: PerkArchetype,
        random: Random,
    ): PerkDefinition {
        val total = candidates.sumOf { if (it.archetype == bias) PerkRollBias.WEIGHT else 1.0 }
        var roll = random.nextDouble() * total
        for (perk in candidates) {
            roll -= if (perk.archetype == bias) PerkRollBias.WEIGHT else 1.0
            if (roll < 0.0) return perk
        }
        return candidates.last()
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

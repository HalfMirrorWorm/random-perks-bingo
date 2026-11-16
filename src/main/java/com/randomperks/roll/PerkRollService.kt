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
        val excludedArchetype = PerkExclusion.archetype(state)

        val options = rollOptionsExcluding(excluded, count, random, excludedArchetype).toMutableList()
        // "专精": 每次普通三选一至少出现一个选定流派的词条;
        // 该流派已经没有可选词条、或已被"屏蔽"时退化为普通抽取
        val bias = PerkRollBias.bias(state)
        if (bias != null && bias != excludedArchetype && options.isNotEmpty() && options.none { it.archetype == bias }) {
            val replacement = rollSingleOfArchetype(excluded + options.map { it.id }, bias, random)
            if (replacement != null) options[options.lastIndex] = replacement
        }
        return options
    }

    /**
     * 抽取一组**只有蓝色品质**的候选 (用于"蔚蓝"特质)。
     *
     * <p>同样排除已获得、待选队列和 [extraExcluded]; 蓝色池为空时返回空列表。
     * "专精"保底在这个蓝色池里继续生效。
     */
    fun rollBlueOptions(
        state: PlayerPerkState,
        count: Int = 3,
        random: Random = Random.Default,
        extraExcluded: Set<String> = emptySet(),
    ): List<PerkDefinition> {
        val excluded = state.ownedPerks.mapTo(mutableSetOf()) { it.id }
        excluded.addAll(extraExcluded)
        state.pendingOffers.forEach { excluded.addAll(it) }
        val excludedArchetype = PerkExclusion.archetype(state)

        val pool = PerkRegistry.all.filter {
            it.rarity == PerkRarity.BLUE && it.archetype != PerkArchetype.TRAIT &&
                it.archetype != excludedArchetype && it.id !in excluded
        }.toMutableList()
        val options = pool.shuffled(random).take(count).toMutableList()

        val bias = PerkRollBias.bias(state)
        if (bias != null && bias != excludedArchetype && options.isNotEmpty() && options.none { it.archetype == bias }) {
            val replacement = pool.firstOrNull { it.archetype == bias && it !in options }
            if (replacement != null) options[options.lastIndex] = replacement
        }
        return options
    }

    /** 从排除集合之外随机抽一个词条 (用于"神话被抢后刷新选项位"等场景) */
    fun rollSingle(
        excluded: Set<String>,
        random: Random = Random.Default,
        excludedArchetype: PerkArchetype? = null,
    ): PerkDefinition? =
        rollOptionsExcluding(excluded, 1, random, excludedArchetype).firstOrNull()

    /**
     * 从指定流派里按品质权重抽一个词条 (用于"专精"保底)。
     * 该流派没有可选词条时返回 null。
     */
    fun rollSingleOfArchetype(
        excluded: Set<String>,
        archetype: PerkArchetype,
        random: Random = Random.Default,
    ): PerkDefinition? {
        if (archetype == PerkArchetype.TRAIT) return null

        val pool = PerkRegistry.all.filter { it.archetype == archetype && it.id !in excluded }
        if (pool.isEmpty()) return null

        val rarity = rollRarity(pool.map { it.rarity }.distinct(), random)
        val candidates = pool.filter { it.rarity == rarity }.ifEmpty { pool }
        return candidates[random.nextInt(candidates.size)]
    }

    private fun rollOptionsExcluding(
        excluded: Set<String>,
        count: Int,
        random: Random,
        excludedArchetype: PerkArchetype? = null,
    ): List<PerkDefinition> {
        // 特质只能通过"特质选择"获得, 不进入普通三选一池;
        // "屏蔽"的流派也不进入候选
        val pool = PerkRegistry.all
            .filterNot {
                it.id in excluded || it.archetype == PerkArchetype.TRAIT || it.archetype == excludedArchetype
            }
            .toMutableList()
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

package com.randomperks.game

import com.randomperks.perk.PerkRegistry
import com.randomperks.roll.PerkExclusion
import com.randomperks.roll.PerkRollService
import com.randomperks.state.ChoiceKind
import com.randomperks.state.OfferKind
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import com.randomperks.stats.PerkStatsService

/**
 * "屏蔽"特质的服务端辅助: 选定流派后, 立刻重抽所有包含该流派词条的待选机会。
 *
 * <p>只重抽"已经生成但还没打开"的候选, 让屏蔽立即生效; 不改变机会数量、类型和顺序。
 */
object PerkExclusionService {

    fun rerollPendingOffers(state: PlayerPerkState) {
        val excludedArchetype = PerkExclusion.archetype(state) ?: return

        val headIsPerk = state.pendingOrder.firstOrNull() == ChoiceKind.PERK
        val headIsHigh = headIsPerk && PerkHighRarityOffer.isCurrentHigh(state)
        val headIsStreak = headIsPerk && PerkStreakBonus.isCurrent(state)

        for (i in state.pendingOffers.indices) {
            val offer = state.pendingOffers[i]
            val containsExcluded = offer.any { id ->
                PerkRegistry.byId(id)?.archetype == excludedArchetype
            }
            if (!containsExcluded) continue

            val kind = state.pendingOfferKinds.getOrNull(i) ?: OfferKind.NORMAL
            val options = when {
                kind == OfferKind.BLUE -> PerkRollService.rollBlueOptions(state)
                i == 0 && headIsHigh -> PerkHighRarityOffer.roll(
                    state,
                    PerkStateManager.globallyOwnedMythicIds(),
                )
                else -> PerkRollService.rollOptions(
                    state,
                    extraExcluded = PerkStateManager.globallyOwnedMythicIds(),
                )
            }
            if (options.isEmpty()) continue

            state.pendingOffers[i] = options.map { it.id }
            PerkStatsService.recordOffered(options.map { it.id })

            if (i == 0 && headIsStreak && kind != OfferKind.BLUE) {
                val adjusted = options.toMutableList()
                PerkStreakBonus.applyToOffer(state, adjusted)
                state.pendingOffers[0] = adjusted.map { it.id }
            }
        }
    }
}

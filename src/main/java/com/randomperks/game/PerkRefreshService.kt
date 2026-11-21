package com.randomperks.game

import com.randomperks.network.PerkNetworking
import com.randomperks.network.PerkOptionData
import com.randomperks.perk.PerkRegistry
import com.randomperks.roll.PerkRollService
import com.randomperks.state.ChoiceKind
import com.randomperks.state.OfferKind
import com.randomperks.state.PerkStateManager
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * "刷新"特质: 重新抽取当前普通三选一的候选。
 *
 * <p>只替换队首的普通机会, 不消耗选择机会; 已有/待选/全局唯一神话仍会被排除,
 * "专精"的流派保底也会正常参与。"蔚蓝"机会刷新后仍然是蓝色候选。
 */
object PerkRefreshService {

    const val PERK_ID = "trait_03"

    fun handle(player: ServerPlayer) {
        if (!PerkSession.isEnabled()) return
        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.hasPerk(PERK_ID)) return
        if (state.nextChoiceKind() != ChoiceKind.PERK) return
        if (state.currentOffer() == null) return
        if (PerkRefreshCharges.charges(state) <= 0) return

        var options = when {
            // "蔚蓝"机会: 重抽时仍然只从蓝色词条里选
            state.currentOfferKind() == OfferKind.BLUE -> PerkRollService.rollBlueOptions(
                state,
                extraExcluded = PerkStateManager.globallyOwnedMythicIds(),
            )
            PerkHighRarityOffer.isCurrentHigh(state) -> {
                val high = PerkHighRarityOffer.roll(state, PerkStateManager.globallyOwnedMythicIds())
                if (high.isNotEmpty()) {
                    high
                } else {
                    // 金/红池也用完了: 这次刷新回退普通池, 并结束精选状态
                    PerkHighRarityOffer.clearCurrent(state)
                    PerkRollService.rollOptions(
                        state,
                        extraExcluded = PerkStateManager.globallyOwnedMythicIds(),
                    )
                }
            }
            else -> PerkRollService.rollOptions(
                state,
                extraExcluded = PerkStateManager.globallyOwnedMythicIds(),
            )
        }

        // "专研": 重抽后继续保持保底; 蓝机会不参与 (全是蓝色候选)
        if (state.currentOfferKind() == OfferKind.NORMAL &&
            (PerkStreakBonus.isPending(state) || PerkStreakBonus.isCurrent(state))
        ) {
            val adjusted = options.toMutableList()
            PerkStreakBonus.applyToOffer(state, adjusted)
            options = adjusted
        }

        if (options.isEmpty()) {
            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.refresh_empty"),
                true,
            )
            return
        }

        // 只替换队首普通机会; pendingOrder 不变, 仍然只消费 1 次刷新
        state.pendingOffers[0] = options.map { it.id }
        PerkRefreshCharges.consume(state)
        com.randomperks.stats.PerkStatsService.recordOffered(options.map { it.id })

        PerkNetworking.sendOffer(
            player,
            options.map {
                PerkOptionData(it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon(), it.isOneTime, it.maxUses, it.maxUses)
            },
            PerkRefreshCharges.charges(state),
        )
    }
}

package com.roguebingo.game

import com.roguebingo.RogueBingoMod
import com.roguebingo.network.PerkNetworking
import com.roguebingo.network.PerkOptionData
import com.roguebingo.perk.PerkContext
import com.roguebingo.perk.PerkRarity
import com.roguebingo.perk.PerkRegistry
import com.roguebingo.roll.PerkRollService
import com.roguebingo.state.PerkStateManager
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 服务端: 三选一的生成与选择处理。
 */
object PerkOfferService {

    /** 玩家请求打开三选一界面 → 生成并下发候选 */
    fun handleRequest(player: ServerPlayerEntity) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        if (state.pendingChoices <= 0) {
            RogueBingoMod.logger.info("[RogueBingo] {} requested offer but has no pending choices", player.name.string)
            return
        }

        val options = PerkRollService.rollOptions(state)
        if (options.isEmpty()) {
            RogueBingoMod.logger.warn("[RogueBingo] perk pool exhausted for {}", player.name.string)
            return
        }

        state.pendingOffer = options.map { it.id }
        PerkNetworking.sendOffer(
            player,
            options.map { PerkOptionData(it.id, it.rarity.ordinal, it.icon()) },
        )
    }

    /** 玩家选择了第 index 项 */
    fun handlePick(player: ServerPlayerEntity, index: Int) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        val offer = state.pendingOffer
        if (offer == null || state.pendingChoices <= 0) return

        val perkId = offer.getOrNull(index) ?: return
        val perk = PerkRegistry.byId(perkId) ?: return
        if (state.hasPerk(perk.id)) return

        state.pendingChoices--
        state.pendingOffer = null
        state.ownedPerks.add(perk)
        perk.onAcquire(PerkContext(player, state))

        // 反馈
        val rarityName = when (perk.rarity) {
            PerkRarity.BLUE -> Text.translatable("rogue-bingo.rarity.blue").formatted(Formatting.BLUE)
            PerkRarity.PURPLE -> Text.translatable("rogue-bingo.rarity.purple").formatted(Formatting.LIGHT_PURPLE)
            PerkRarity.GOLD -> Text.translatable("rogue-bingo.rarity.gold").formatted(Formatting.GOLD)
        }
        player.sendMessage(
            Text.translatable("rogue-bingo.message.acquired", rarityName, Text.translatable("rogue-bingo.perk.${perk.id}.name")),
            false,
        )

        PerkNetworking.sendNotify(player, state.pendingChoices)
        RogueBingoMod.logger.info(
            "[RogueBingo] {} acquired perk {} ({}), {} choice(s) left",
            player.name.string, perk.id, perk.rarity, state.pendingChoices,
        )
    }
}

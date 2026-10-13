package com.roguebingo.game

import com.roguebingo.RogueBingoMod
import com.roguebingo.network.PerkNetworking
import com.roguebingo.network.PerkOptionData
import com.roguebingo.perk.PerkContext
import com.roguebingo.perk.PerkRarity
import com.roguebingo.perk.PerkRegistry
import com.roguebingo.state.PerkStateManager
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 服务端: 三选一的展示与选择处理。
 *
 * 候选在发放机会时已生成并固定 (队列第一个), 重复打开界面不会重新随机。
 */
object PerkOfferService {

    /** 玩家请求打开三选一界面 → 下发当前(队列第一个)候选, 未选择前保持不变 */
    fun handleRequest(player: ServerPlayerEntity) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        val offer = state.currentOffer()
        if (offer == null) {
            RogueBingoMod.logger.info("[RogueBingo] {} requested offer but has no pending choices", player.name.string)
            return
        }

        val options = offer.mapNotNull { PerkRegistry.byId(it) }
        if (options.isEmpty()) {
            // 数据异常: 丢弃该组, 避免卡住队列
            RogueBingoMod.logger.warn("[RogueBingo] offer for {} contains no valid perks", player.name.string)
            state.consumeOffer()
            return
        }

        PerkNetworking.sendOffer(
            player,
            options.map { PerkOptionData(it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon()) },
        )
    }

    /** 玩家选择了第 index 项 */
    fun handlePick(player: ServerPlayerEntity, index: Int) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        val offer = state.currentOffer() ?: return

        val perkId = offer.getOrNull(index) ?: return
        val perk = PerkRegistry.byId(perkId) ?: return

        if (state.hasPerk(perk.id)) {
            // 已拥有: 不消耗机会, 提示重新选择
            player.sendMessage(
                Text.translatable("rogue-bingo.message.already_owned", Text.translatable("rogue-bingo.perk.${perk.id}.name"))
                    .formatted(Formatting.YELLOW),
                false,
            )
            return
        }

        state.consumeOffer()
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

    /** 玩家请求查看已获得词条列表 */
    fun handleListRequest(player: ServerPlayerEntity) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        PerkNetworking.sendPerkList(
            player,
            state.pendingChoices,
            state.ownedPerks.map { PerkOptionData(it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon()) },
        )
    }
}

package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PerkOptionData
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import com.randomperks.state.PerkStateManager
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
            RandomPerksMod.logger.info("[RandomPerks] {} requested offer but has no pending choices", player.name.string)
            return
        }

        val options = offer.mapNotNull { PerkRegistry.byId(it) }
        if (options.isEmpty()) {
            // 数据异常: 丢弃该组, 避免卡住队列
            RandomPerksMod.logger.warn("[RandomPerks] offer for {} contains no valid perks", player.name.string)
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
                Text.translatable("random-perks-bingo.message.already_owned", Text.translatable("random-perks-bingo.perk.${perk.id}.name"))
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
            PerkRarity.BLUE -> Text.translatable("random-perks-bingo.rarity.blue").formatted(Formatting.BLUE)
            PerkRarity.PURPLE -> Text.translatable("random-perks-bingo.rarity.purple").formatted(Formatting.LIGHT_PURPLE)
            PerkRarity.GOLD -> Text.translatable("random-perks-bingo.rarity.gold").formatted(Formatting.GOLD)
        }
        player.sendMessage(
            Text.translatable("random-perks-bingo.message.acquired", rarityName, Text.translatable("random-perks-bingo.perk.${perk.id}.name")),
            false,
        )

        PerkNetworking.sendNotify(player, state.pendingChoices)
        PerkGrantService.syncAbilities(player)
        RandomPerksMod.logger.info(
            "[RandomPerks] {} acquired perk {} ({}), {} choice(s) left",
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

    /** 玩家请求词条百科: 下发全部词条定义 + 已收集标记 */
    fun handleCatalogRequest(player: ServerPlayerEntity) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        PerkNetworking.sendCatalog(
            player,
            state.ownedPerks.map { it.id },
            PerkRegistry.all.map { PerkOptionData(it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon()) },
        )
    }
}

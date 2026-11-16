package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PerkOptionData
import com.randomperks.network.PlayerPerkList
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.content.TraitPerks
import com.randomperks.roll.PerkRollBias
import com.randomperks.roll.PerkRollService
import com.randomperks.state.ChoiceKind
import com.randomperks.state.OfferKind
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.ChatFormatting

/**
 * 服务端: 三选一的展示与选择处理。
 *
 * 候选在发放机会时已生成并固定 (队列第一个), 重复打开界面不会重新随机。
 */
object PerkOfferService {

    /** 玩家请求打开选择界面 → 严格按获得的先后顺序处理队首 */
    fun handleRequest(player: ServerPlayer) {
        val state = PerkStateManager.getOrCreate(player.uuid)

        when (state.nextChoiceKind()) {
            ChoiceKind.TRAIT -> {
                sendTraitOffer(player, state)
                return
            }
            ChoiceKind.PERK -> {
                val offer = state.currentOffer()
                if (offer == null) {
                    RandomPerksMod.logger.warn("[RandomPerks] pending PERK for {} has no offer", player.name.string)
                    return
                }

                // "精选": 把队首普通机会换成 3 个金/红候选 (只兑换一次)。
                // "蔚蓝"机会是固定蓝色候选, 不消耗精选, 精选留给之后的下一次普通机会。
                if (state.currentOfferKind() == OfferKind.NORMAL && PerkHighRarityOffer.isNextPending(state)) {
                    val high = PerkHighRarityOffer.roll(state, PerkStateManager.globallyOwnedMythicIds())
                    if (high.isNotEmpty()) {
                        state.pendingOffers[0] = high.map { it.id }
                        PerkHighRarityOffer.clearNext(state)
                        PerkHighRarityOffer.markCurrent(state)
                        com.randomperks.stats.PerkStatsService.recordOffered(high.map { it.id })
                    } else {
                        PerkHighRarityOffer.clearNext(state)
                        player.sendSystemMessage(
                            Component.translatable("random-perks-bingo.message.high_offer_empty"),
                            true,
                        )
                    }
                }

                // "专研": 下一次普通机会保底出现一个选定流派的紫/金词条。
                // 如果这是"精选"机会, 只能保底金色, 不能破坏金/红三选一的承诺。
                if (state.currentOfferKind() == OfferKind.NORMAL && PerkStreakBonus.isPending(state)) {
                    val raw = state.currentOffer()
                    val current = raw?.mapNotNull { PerkRegistry.byId(it) }?.toMutableList()
                    if (raw != null && current != null && current.size == raw.size) {
                        PerkStreakBonus.applyToOffer(state, current)
                        state.pendingOffers[0] = current.map { it.id }
                    }
                }

                val finalOffer = state.currentOffer() ?: return
                val options = finalOffer.mapNotNull { PerkRegistry.byId(it) }
                if (options.isEmpty()) {
                    // 数据异常: 丢弃该组, 避免卡住队列
                    RandomPerksMod.logger.warn("[RandomPerks] offer for {} contains no valid perks", player.name.string)
                    state.consumeOffer()
                    PerkHighRarityOffer.clearCurrent(state)
                    PerkStreakBonus.clearCurrent(state)
                    return
                }

                PerkNetworking.sendOffer(
                    player,
                    options.map { PerkOptionData(it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon(), it.isOneTime) },
                    PerkRefreshCharges.charges(state),
                )
                return
            }
            null -> RandomPerksMod.logger.info("[RandomPerks] {} requested offer but has no pending choices", player.name.string)
        }
    }

    /** 下发剩余特质列表 (每次特质选择都从 13 条里挑一条, 已拥有的不再出现) */
    private fun sendTraitOffer(player: ServerPlayer, state: PlayerPerkState) {
        val options = TraitPerks.available(state)
        if (options.isEmpty()) {
            // 特质池已经抽完: 丢掉队首连续的特质机会, 继续后面的机会
            while (state.nextChoiceKind() == ChoiceKind.TRAIT) state.consumeTraitChoice()
            PerkGrantService.notifyChoices(player)
            return
        }

        PerkNetworking.sendTraitOffer(
            player,
            options.map { PerkOptionData(it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon(), it.isOneTime) },
        )
    }

    /** 玩家选择了某个特质 (按 id, 避免界面列表顺序变化导致错位) */
    fun handleTraitPick(player: ServerPlayer, perkId: String) {
        if (!PerkSession.isEnabled()) return
        val state = PerkStateManager.getOrCreate(player.uuid)
        if (state.nextChoiceKind() != ChoiceKind.TRAIT) return

        val perk = PerkRegistry.byId(perkId) ?: return
        if (perk.archetype != PerkArchetype.TRAIT) return
        if (state.hasPerk(perk.id)) return

        state.consumeTraitChoice()
        state.ownedPerks.add(perk)
        com.randomperks.stats.PerkStatsService.recordPicked(perk.id)
        com.randomperks.recap.RecapService.recordPerkPick(player, perk.id)
        perk.onAcquire(PerkContext(player, state))
        PerkGrantService.notifyPerkListChanged(player)

        val rarityName = Component.translatable("random-perks-bingo.rarity.trait").withStyle(ChatFormatting.GRAY)
        player.sendSystemMessage(
            Component.translatable("random-perks-bingo.message.acquired", rarityName, Component.translatable("random-perks-bingo.perk.${perk.id}.name")),
            false,
        )

        PerkGrantService.notifyChoices(player)
        PerkGrantService.syncAbilities(player)
    }

    /** 玩家选择了第 index 项 */
    fun handlePick(player: ServerPlayer, index: Int) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        val offer = state.currentOffer() ?: return

        val perkId = offer.getOrNull(index) ?: return
        val perk = PerkRegistry.byId(perkId) ?: return

        if (state.hasPerk(perk.id)) {
            // 已拥有: 不消耗机会, 提示重新选择
            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.already_owned", Component.translatable("random-perks-bingo.perk.${perk.id}.name"))
                    .withStyle(ChatFormatting.YELLOW),
                false,
            )
            return
        }

        // 全局唯一词条 (神话): 若已被其他玩家抢选 → 拒绝并刷新该选项位
        if (perk.rarity == PerkRarity.MYTHIC && PerkStateManager.isOwnedByAnyone(perk.id)) {
            val rerolled = rerollOfferSlot(state, index)
            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.mythic_taken").withStyle(ChatFormatting.YELLOW),
                false,
            )
            if (rerolled) handleRequest(player) // 重新下发选项 (客户端界面刷新)
            return
        }

        state.consumeOffer()
        PerkHighRarityOffer.clearCurrent(state)
        PerkStreakBonus.clearCurrent(state)
        state.ownedPerks.add(perk)
        com.randomperks.stats.PerkStatsService.recordPicked(perk.id)
        com.randomperks.recap.RecapService.recordPerkPick(player, perk.id)
        perk.onAcquire(PerkContext(player, state))
        // 词条列表已变化: 触发"同流派联动"类词条的即时重算
        PerkGrantService.notifyPerkListChanged(player)
        // "专研"进度: 统计这次选择是否属于选定流派
        PerkStreakBonus.recordPick(player, state, perk)

        // 反馈
        val rarityName = when (perk.rarity) {
            PerkRarity.BLUE -> Component.translatable("random-perks-bingo.rarity.blue").withStyle(ChatFormatting.BLUE)
            PerkRarity.GREEN -> Component.translatable("random-perks-bingo.rarity.green").withStyle(ChatFormatting.GREEN)
            PerkRarity.PURPLE -> Component.translatable("random-perks-bingo.rarity.purple").withStyle(ChatFormatting.LIGHT_PURPLE)
            PerkRarity.GOLD -> Component.translatable("random-perks-bingo.rarity.gold").withStyle(ChatFormatting.GOLD)
            PerkRarity.RED -> Component.translatable("random-perks-bingo.rarity.red").withStyle(ChatFormatting.RED)
            PerkRarity.MYTHIC -> Component.translatable("random-perks-bingo.rarity.mythic").withStyle(ChatFormatting.LIGHT_PURPLE)
            PerkRarity.TRAIT -> Component.translatable("random-perks-bingo.rarity.trait").withStyle(ChatFormatting.GRAY)
        }
        player.sendSystemMessage(
            Component.translatable("random-perks-bingo.message.acquired", rarityName, Component.translatable("random-perks-bingo.perk.${perk.id}.name")),
            false,
        )

        // 金色及以上词条向全服公告 (特质是灰色, 不公告)
        if (perk.rarity == PerkRarity.GOLD || perk.rarity == PerkRarity.RED || perk.rarity == PerkRarity.MYTHIC) {
            val announce = Component.translatable(
                "random-perks-bingo.message.high_rarity_announce",
                player.name,
                rarityName,
                Component.translatable("random-perks-bingo.perk.${perk.id}.name"),
            )
            player.level().getServer().playerList.players.forEach { it.sendSystemMessage(announce, false) }
        }

        PerkGrantService.notifyChoices(player)
        PerkGrantService.syncAbilities(player)
        RandomPerksMod.logger.info(
            "[RandomPerks] {} acquired perk {} ({}), {} choice(s) left",
            player.name.string, perk.id, perk.rarity, state.pendingChoices,
        )
    }

    /** 玩家请求查看词条列表 —— 下发**所有在线玩家**的, 客户端用 tab 页切换 */
    fun handleListRequest(player: ServerPlayer) {
        val server = player.level().getServer()
        val state = PerkStateManager.getOrCreate(player.uuid)

        val players = server.playerList.players.map { other ->
            val otherState = PerkStateManager.get(other.uuid)

            // 所有词条 (含神话) 对所有人可见
            val visiblePerks = otherState?.ownedPerks ?: emptyList()

            val team = other.team
            // 队伍名在 bingo 里是可翻译文本 (如 bingo.team.red); 直接取 .string 会在服务端
            // 按服务端语言解析成英文, 客户端就永远看到 "Red"。这里把 key 一起发过去,
            // 让客户端按玩家自己的语言显示 (红队 / Red)。
            // 临时队伍的名字是 "队伍：%s" 这种带 1 个参数的形式, 参数也一并带上。
            val teamNameText = team?.displayName?.contents as? TranslatableContents
            val teamNameKey: String
            val teamNameArg: String
            if (teamNameText != null && teamNameText.args.size <= 1) {
                teamNameKey = teamNameText.key
                teamNameArg = teamNameText.args.firstOrNull()?.toString() ?: ""
            } else {
                teamNameKey = ""
                teamNameArg = ""
            }

            PlayerPerkList(
                other.uuid,
                other.name.string,
                visiblePerks.map {
                    PerkOptionData(it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon(), it.isOneTime)
                },
                visiblePerks
                    .filter { it.isOneTime && (otherState?.usesRemaining(it) ?: 0) > 0 }
                    .map { it.id },
                team?.name ?: "",
                team?.displayName?.string ?: "",
                teamNameKey,
                teamNameArg,
                team?.color?.orElse(null)?.rgb() ?: 0xFFFFFF,
            )
        }

        PerkNetworking.sendPerkList(player, state.totalPending, players)
    }

    /**
     * 玩家在 V 界面点击了一个一次性词条 → 触发它的使用流程。
     *
     * 这里只负责"打开使用流程"(目标选择器 / 装备选择器 / 偷窃目标);
     * 使用次数由各词条在效果真正生效时通过 [PlayerPerkState.consumeUse] 扣除,
     * 所以中途取消选择不会浪费词条。
     */
    fun handleActivate(player: ServerPlayer, perkId: String) {
        if (!PerkSession.isEnabled()) return

        val state = PerkStateManager.get(player.uuid) ?: return
        val perk = state.ownedPerks.firstOrNull { it.id == perkId } ?: return
        if (!perk.isOneTime || state.usesRemaining(perk) <= 0) return

        perk.onActivate(PerkContext(player, state))
    }

    /**
     * 把当前机会的第 index 个选项刷新为别的词条 (用于神话词条被抢选后补位)。
     * @return 是否成功刷新
     */
    private fun rerollOfferSlot(state: PlayerPerkState, index: Int): Boolean {
        val offer = state.currentOffer()?.toMutableList() ?: return false
        if (index !in offer.indices) return false

        val excluded = mutableSetOf<String>()
        state.ownedPerks.forEach { excluded.add(it.id) }
        offer.forEachIndexed { i, id -> if (i != index) excluded.add(id) }
        excluded.addAll(PerkStateManager.globallyOwnedMythicIds())

        val base = PerkRollService.rollSingle(excluded) ?: return true

        // "专精"保底: 补位后如果整组候选不再有选定流派, 优先从该流派里补
        val bias = PerkRollBias.bias(state)
        val replacement = if (bias != null && base.archetype != bias) {
            val others = offer.filterIndexed { i, _ -> i != index }.mapNotNull { PerkRegistry.byId(it) }
            if (others.any { it.archetype == bias }) {
                base
            } else {
                PerkRollService.rollSingleOfArchetype(excluded + base.id, bias) ?: base
            }
        } else {
            base
        }

        offer[index] = replacement.id
        state.pendingOffers[0] = offer
        // 补位的词条也是玩家实际看到的候选, 计入统计
        com.randomperks.stats.PerkStatsService.recordOffered(listOf(replacement.id))
        return true
    }

    /** 玩家请求词条百科: 下发全部词条定义 + 已收集标记 */
    fun handleCatalogRequest(player: ServerPlayer) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        PerkNetworking.sendCatalog(
            player,
            state.ownedPerks.map { it.id },
            PerkRegistry.all.map { PerkOptionData(it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon(), it.isOneTime) },
        )
    }
}

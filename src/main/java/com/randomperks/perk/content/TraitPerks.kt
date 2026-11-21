package com.randomperks.perk.content

import com.randomperks.RandomPerksMod
import com.randomperks.game.PerkApplicationHistory
import com.randomperks.game.PerkCounterService
import com.randomperks.game.PerkDownsideSuppression
import com.randomperks.game.PerkDownsides
import com.randomperks.game.PerkExclusionService
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PerkHighRarityOffer
import com.randomperks.game.PerkRefreshCharges
import com.randomperks.game.PerkStreakBonus
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PerkOptionData
import com.randomperks.network.PlayerTargetEntry
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import com.randomperks.roll.PerkExclusion
import com.randomperks.roll.PerkRollBias
import com.randomperks.roll.PerkRollService
import com.randomperks.state.ChoiceKind
import com.randomperks.state.OfferKind
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import com.randomperks.stats.PerkStatsService
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

/**
 * 特质 (灰): 只能通过"特质选择"获得的特殊词条。
 *
 * <p>特质不参与普通三选一抽取, 也不计入流派联动 (archetype = TRAIT)。
 * 13 条已全部实现: `trait_01` 回放、`trait_02` 专精、`trait_03` 刷新、`trait_04` 净化、`trait_05` 再蓄、
 * `trait_06` 精选、`trait_07` 蔚蓝、`trait_08` 天赐、`trait_09` 反制、`trait_10` 转移、`trait_11` 升华、
 * `trait_12` 专研、`trait_13` 屏蔽。
 */
object TraitPerks {

    /** 总共有 13 条特质 */
    const val COUNT = 13

    val ALL: List<PerkDefinition> = buildList {
        add(Replay)
        add(ArchetypeBias)
        add(Refresh)
        add(Cleanse)
        add(Recharge)
        add(HighRoll)
        add(BlueChoice)
        add(Windfall)
        add(Counter)
        add(ChoiceTransfer)
        add(Transmute)
        add(ArchetypeStreak)
        add(ArchetypeExclusion)
    }

    /**
     * 回放: 获得本局到现在为止**所有其他玩家对你施加过**的词条。
     *
     * <p>来源是 [PerkApplicationHistory] (由各"选择目标"类词条在成功生效时记录)。
     * 已经拥有的不重复获得; 特质和神话不复制 (神话全局唯一)。
     */
    object Replay : PerkDefinition {
        override val id = "trait_01"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override fun icon(): ItemStack = ItemStack(Items.ECHO_SHARD)

        override fun onAcquire(ctx: PerkContext) {
            for (perkId in PerkApplicationHistory.appliedPerks(ctx.state)) {
                val perk = PerkRegistry.byId(perkId) ?: continue
                if (perk.archetype == PerkArchetype.TRAIT) continue
                if (perk.rarity == PerkRarity.MYTHIC) continue
                if (ctx.state.hasPerk(perk.id)) continue

                ctx.state.ownedPerks.add(perk)
                runCatching { perk.onAcquire(ctx) }
            }
        }
    }

    /**
     * 专精: 选择一种词条流派, 之后每次普通三选一都至少出现一个该流派的词条。
     *
     * <p>选择器打开时如果玩家按 Esc 关掉, 词条会留在 V 列表里闪烁;
     * 点击它可以重新打开选择器。选定后消耗这次使用机会。
     */
    object ArchetypeBias : PerkDefinition {
        override val id = "trait_02"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override val isOneTime = true
        override fun icon(): ItemStack = ItemStack(Items.ENCHANTED_BOOK)

        override fun onAcquire(ctx: PerkContext) {
            PerkNetworking.sendArchetypePick(ctx.player)
        }

        override fun onActivate(ctx: PerkContext) {
            PerkNetworking.sendArchetypePick(ctx.player)
        }

        override fun onRemove(ctx: PerkContext) {
            PerkRollBias.clear(ctx.state)
        }

        /** 客户端选择了一个流派 (ordinal, 排除 TRAIT) */
        fun handlePick(player: ServerPlayer, ordinal: Int) {
            val state = PerkStateManager.get(player.uuid) ?: return
            val perk = state.ownedPerks.firstOrNull { it.id == id } ?: return
            if (state.usesRemaining(perk) <= 0) return

            val archetype = PerkArchetype.entries.getOrNull(ordinal) ?: return
            if (archetype == PerkArchetype.TRAIT) return

            PerkRollBias.set(state, archetype)
            state.consumeUse(perk)
            PerkGrantService.notifyPerkListChanged(player)
            PerkGrantService.syncAbilities(player)
            player.sendSystemMessage(
                Component.translatable(
                    "random-perks-bingo.message.bias_chosen",
                    Component.translatable("random-perks-bingo.archetype.${archetype.id}"),
                ),
                false,
            )
        }
    }

    /**
     * 刷新: 获得 5 次词条刷新机会; 在普通三选一界面点击刷新, 重新抽取当前候选。
     */
    object Refresh : PerkDefinition {
        override val id = "trait_03"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override fun icon(): ItemStack = ItemStack(Items.CLOCK)

        override fun onAcquire(ctx: PerkContext) {
            PerkRefreshCharges.grant(ctx.state)
        }

        override fun onRemove(ctx: PerkContext) {
            PerkRefreshCharges.clear(ctx.state)
        }
    }

    /**
     * 再蓄: 获得时, 把自己**当前拥有**的所有贮藏词条的可用次数全部刷新
     * (包括单次使用和多次使用的贮藏词条)。
     */
    object Recharge : PerkDefinition {
        override val id = "trait_05"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override fun icon(): ItemStack = ItemStack(Items.EXPERIENCE_BOTTLE)

        override fun onAcquire(ctx: PerkContext) {
            var refreshed = 0
            for (perk in ctx.state.ownedPerks.toList()) {
                if (!perk.isOneTime) continue
                // 只刷新普通贮藏词条, 不刷新灰色特质 (专精/净化等)
                if (perk.archetype == PerkArchetype.TRAIT) continue
                val hadUsed = ctx.state.usedPerks.remove(perk.id)
                val hadCount = ctx.state.useCounts.remove(perk.id) != null
                if (hadUsed || hadCount) refreshed++
            }
            ctx.player.sendSystemMessage(
                Component.translatable(
                    if (refreshed > 0) "random-perks-bingo.message.recharge_done"
                    else "random-perks-bingo.message.recharge_none",
                    refreshed,
                ),
                false,
            )
        }
    }
    /**
     * 精选: 你接下来的一次普通词条选择只从 3 个金/红品质里挑。
     */
    object HighRoll : PerkDefinition {
        override val id = "trait_06"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override fun icon(): ItemStack = ItemStack(Items.GOLD_INGOT)

        override fun onAcquire(ctx: PerkContext) {
            PerkHighRarityOffer.markNext(ctx.state)
        }

        override fun onRemove(ctx: PerkContext) {
            PerkHighRarityOffer.clearNext(ctx.state)
            PerkHighRarityOffer.clearCurrent(ctx.state)
        }
    }

    /**
     * 蔚蓝: 额外获得一次普通词条选择, 候选全部为蓝色品质。
     */
    object BlueChoice : PerkDefinition {
        override val id = "trait_07"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override fun icon(): ItemStack = ItemStack(Items.LAPIS_LAZULI)

        override fun onAcquire(ctx: PerkContext) {
            PerkGrantService.grantBlueChoice(ctx.player)
        }
    }

    /**
     * 天赐: 随机获得两个随机词条 (品质按普通抽取权重, 不重复)。
     */
    object Windfall : PerkDefinition {
        override val id = "trait_08"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override fun icon(): ItemStack = ItemStack(Items.CHEST)

        override fun onAcquire(ctx: PerkContext) {
            PerkGrantService.grantRandomPerks(ctx.player, 2)
        }
    }

    /**
     * 反制: 消耗一次充能, 无效化其他玩家下一次对你施加的负面词条效果。
     */
    object Counter : PerkDefinition {
        override val id = "trait_09"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override fun icon(): ItemStack = ItemStack(Items.SHIELD)

        override fun onAcquire(ctx: PerkContext) {
            PerkCounterService.arm(ctx.state)
            ctx.player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.counter_armed"),
                true,
            )
        }

        override fun onRemove(ctx: PerkContext) {
            PerkCounterService.disarm(ctx.state)
        }
    }

    /**
     * 转移: 将自己的最后两次普通词条选择机会转给一名队友。
     *
     * <p>获得后作为贮藏词条留在 V 列表, **不会自动打开选择器**; 等自己攒够两次待选普通机会后,
     * 在 V 界面点击它再选择队友。转移的是"机会"而不是具体候选: 移除自己队尾两次普通机会,
     * 再按队友自己的词条池重新抽两组候选给他。
     */
    object ChoiceTransfer : PerkDefinition {
        override val id = "trait_10"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override val isOneTime = true
        override fun icon(): ItemStack = ItemStack(Items.ENDER_CHEST)

        private const val PURPOSE = "transfer_choices"
        private const val TRANSFER_COUNT = 2

        override fun onActivate(ctx: PerkContext) = openPicker(ctx.player)

        private fun openPicker(player: ServerPlayer) {
            val state = PerkStateManager.get(player.uuid) ?: return
            if (state.pendingOrder.count { it == ChoiceKind.PERK } < TRANSFER_COUNT) {
                player.sendSystemMessage(
                    Component.translatable("random-perks-bingo.message.transfer_choices_no_choices"),
                    true,
                )
                return
            }

            val candidates = candidates(player)
            if (candidates.isEmpty()) {
                player.sendSystemMessage(
                    Component.translatable("random-perks-bingo.message.transfer_choices_no_target"),
                    true,
                )
                return
            }
            PerkNetworking.sendActionTargets(
                player,
                PURPOSE,
                candidates.map { PlayerTargetEntry(it.uuid, it.name.string) },
            )
        }

        /** 客户端选中队友: 扣掉自己的两次普通机会, 按对方的词条池补两组新候选 */
        fun handleTargetPick(player: ServerPlayer, uuid: UUID) {
            val state = PerkStateManager.get(player.uuid) ?: return
            val perk = state.ownedPerks.firstOrNull { it.id == id } ?: return
            if (state.usesRemaining(perk) <= 0) return

            val target = candidates(player).firstOrNull { it.uuid == uuid } ?: return
            if (state.pendingOrder.count { it == ChoiceKind.PERK } < TRANSFER_COUNT) {
                player.sendSystemMessage(
                    Component.translatable("random-perks-bingo.message.transfer_choices_no_choices"),
                    true,
                )
                return
            }

            val targetState = PerkStateManager.getOrCreate(target.uuid)
            val baseExcluded = PerkStateManager.globallyOwnedMythicIds()
            val first = PerkRollService.rollOptions(targetState, extraExcluded = baseExcluded)
            val second = PerkRollService.rollOptions(
                targetState,
                extraExcluded = baseExcluded + first.map { it.id },
            )
            if (first.isEmpty() || second.isEmpty()) {
                player.sendSystemMessage(
                    Component.translatable("random-perks-bingo.message.transfer_choices_target_empty"),
                    true,
                )
                return
            }

            // 如果队首的普通机会正好是被移走的最后一次 (队里只有这两次), 清掉"精选"标记;
            // "专研"保底则重新挂到下一次普通机会上
            val removingHeadHigh = state.pendingOrder.firstOrNull() == ChoiceKind.PERK &&
                state.pendingOrder.count { it == ChoiceKind.PERK } == TRANSFER_COUNT &&
                PerkHighRarityOffer.isCurrentHigh(state)
            val removingHeadStreak = state.pendingOrder.firstOrNull() == ChoiceKind.PERK &&
                state.pendingOrder.count { it == ChoiceKind.PERK } == TRANSFER_COUNT &&
                PerkStreakBonus.isCurrent(state)

            if (!state.removeLastPerkChoices(TRANSFER_COUNT)) return
            if (removingHeadHigh) PerkHighRarityOffer.clearCurrent(state)
            if (removingHeadStreak) PerkStreakBonus.requeueCurrent(state)

            for (offer in listOf(first, second)) {
                val ids = offer.map { it.id }
                targetState.pendingOffers.add(ids)
                targetState.pendingOfferKinds.add(OfferKind.NORMAL)
                targetState.pendingOrder.add(ChoiceKind.PERK)
                PerkStatsService.recordOffered(ids)
            }

            state.consumeUse(perk)
            PerkGrantService.notifyChoices(player)
            PerkGrantService.notifyChoices(target)
            PerkGrantService.notifyPerkListChanged(player)
            PerkGrantService.syncAbilities(player)

            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.transfer_choices_sent", target.name),
                false,
            )
            target.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.transfer_choices_received", player.name),
                false,
            )
        }

        /** 候选: 同队、在线、存活、非旁观, 且不含自己 */
        private fun candidates(player: ServerPlayer): List<ServerPlayer> {
            val team = player.team?.name ?: return emptyList()
            return player.level().getServer().playerList.players.filter {
                it !== player && it.isAlive && !it.isSpectator && it.team?.name == team
            }
        }
    }

    /**
     * 升华: 选择一个自己拥有的蓝色词条, 随机变成紫色词条。
     *
     * <p>替换时先执行旧词条的 onRemove 清理效果, 再执行新词条的 onAcquire;
     * 新词条从当前未拥有、未在待选队列中的紫色词条里随机抽取。
     */
    object Transmute : PerkDefinition {
        override val id = "trait_11"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override val isOneTime = true
        override fun icon(): ItemStack = ItemStack(Items.AMETHYST_SHARD)

        override fun onAcquire(ctx: PerkContext) = openPicker(ctx.player)

        override fun onActivate(ctx: PerkContext) = openPicker(ctx.player)

        private fun openPicker(player: ServerPlayer) {
            val state = PerkStateManager.get(player.uuid) ?: return
            val bluePerks = state.ownedPerks.filter { it.rarity == PerkRarity.BLUE }
            if (bluePerks.isEmpty()) {
                player.sendSystemMessage(
                    Component.translatable("random-perks-bingo.message.transmute_no_blue"),
                    true,
                )
                return
            }
            if (purpleCandidates(state).isEmpty()) {
                player.sendSystemMessage(
                    Component.translatable("random-perks-bingo.message.transmute_purple_empty"),
                    true,
                )
                return
            }

            PerkNetworking.sendTransmuteOpen(
                player,
                bluePerks.map {
                    PerkOptionData(
                        it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon(), it.isOneTime,
                        it.maxUses,
                        if (it.isOneTime) state.usesRemaining(it) else it.maxUses,
                    )
                },
            )
        }

        /** 客户端选择了要升华的蓝色词条 id */
        fun handlePick(player: ServerPlayer, perkId: String) {
            val state = PerkStateManager.get(player.uuid) ?: return
            val traitPerk = state.ownedPerks.firstOrNull { it.id == id } ?: return
            if (state.usesRemaining(traitPerk) <= 0) return

            val oldPerk = state.ownedPerks.firstOrNull { it.id == perkId } ?: return
            if (oldPerk.rarity != PerkRarity.BLUE) return

            val candidates = purpleCandidates(state)
            if (candidates.isEmpty()) {
                player.sendSystemMessage(
                    Component.translatable("random-perks-bingo.message.transmute_purple_empty"),
                    true,
                )
                return
            }
            val newPerk = candidates.random()

            state.ownedPerks.remove(oldPerk)
            state.usedPerks.remove(oldPerk.id)
            state.useCounts.remove(oldPerk.id)
            state.cooldowns.remove(oldPerk.id)
            runCatching { oldPerk.onRemove(PerkContext(player, state)) }
                .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} onRemove failed", oldPerk.id, it) }

            state.ownedPerks.add(newPerk)
            runCatching { newPerk.onAcquire(PerkContext(player, state)) }
                .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} onAcquire failed", newPerk.id, it) }

            state.consumeUse(traitPerk)
            PerkGrantService.notifyPerkListChanged(player)
            PerkGrantService.syncAbilities(player)

            player.sendSystemMessage(
                Component.translatable(
                    "random-perks-bingo.message.transmute_done",
                    Component.translatable("random-perks-bingo.perk.${oldPerk.id}.name"),
                    Component.translatable("random-perks-bingo.perk.${newPerk.id}.name"),
                ),
                false,
            )
        }

        /** 未拥有、未在待选队列里的紫色词条 */
        private fun purpleCandidates(state: PlayerPerkState): List<PerkDefinition> {
            val excluded = state.ownedPerks.mapTo(mutableSetOf()) { it.id }
            state.pendingOffers.forEach { excluded.addAll(it) }
            return PerkRegistry.all.filter {
                it.rarity == PerkRarity.PURPLE && it.id !in excluded
            }
        }
    }

    /**
     * 专研: 选择一个流派, 之后每选择 3 个该流派词条,
     * 下一次普通三选一保底出现一个该流派的紫色或金色词条。
     */
    object ArchetypeStreak : PerkDefinition {
        override val id = "trait_12"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override val isOneTime = true
        override fun icon(): ItemStack = ItemStack(Items.BOOK)

        override fun onAcquire(ctx: PerkContext) = openPicker(ctx.player)

        override fun onActivate(ctx: PerkContext) = openPicker(ctx.player)

        private fun openPicker(player: ServerPlayer) {
            PerkNetworking.sendArchetypePick(player, "streak")
        }

        /** 客户端选择了一个流派 (ordinal, 排除 TRAIT) */
        fun handlePick(player: ServerPlayer, ordinal: Int) {
            val state = PerkStateManager.get(player.uuid) ?: return
            val perk = state.ownedPerks.firstOrNull { it.id == id } ?: return
            if (state.usesRemaining(perk) <= 0) return

            val archetype = PerkArchetype.entries.getOrNull(ordinal) ?: return
            if (archetype == PerkArchetype.TRAIT) return

            PerkStreakBonus.setArchetype(state, archetype)
            state.consumeUse(perk)
            PerkGrantService.notifyPerkListChanged(player)
            PerkGrantService.syncAbilities(player)
            player.sendSystemMessage(
                Component.translatable(
                    "random-perks-bingo.message.streak_archetype_chosen",
                    Component.translatable("random-perks-bingo.archetype.${archetype.id}"),
                ),
                false,
            )
        }

        override fun onRemove(ctx: PerkContext) {
            PerkStreakBonus.clearAll(ctx.state)
        }
    }

    /**
     * 屏蔽: 选择一个流派, 之后的词条选择不再出现该流派词条。
     */
    object ArchetypeExclusion : PerkDefinition {
        override val id = "trait_13"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override val isOneTime = true
        override fun icon(): ItemStack = ItemStack(Items.BARRIER)

        override fun onAcquire(ctx: PerkContext) = openPicker(ctx.player)

        override fun onActivate(ctx: PerkContext) = openPicker(ctx.player)

        private fun openPicker(player: ServerPlayer) {
            PerkNetworking.sendArchetypePick(player, "exclude")
        }

        /** 客户端选择了一个流派 (ordinal, 排除 TRAIT) */
        fun handlePick(player: ServerPlayer, ordinal: Int) {
            val state = PerkStateManager.get(player.uuid) ?: return
            val perk = state.ownedPerks.firstOrNull { it.id == id } ?: return
            if (state.usesRemaining(perk) <= 0) return

            val archetype = PerkArchetype.entries.getOrNull(ordinal) ?: return
            if (archetype == PerkArchetype.TRAIT) return

            PerkExclusion.set(state, archetype)
            state.consumeUse(perk)
            PerkExclusionService.rerollPendingOffers(state)
            PerkGrantService.notifyPerkListChanged(player)
            PerkGrantService.syncAbilities(player)
            player.sendSystemMessage(
                Component.translatable(
                    "random-perks-bingo.message.exclusion_archetype_chosen",
                    Component.translatable("random-perks-bingo.archetype.${archetype.id}"),
                ),
                false,
            )
        }

        override fun onRemove(ctx: PerkContext) {
            PerkExclusion.clear(ctx.state)
        }
    }

    /**
     * 净化: 选择一个自己已拥有的词条, 使其**自身的负面效果**无效。
     *
     * <p>可选择范围见 [PerkDownsideSuppression.ELIGIBLE]:
     * 永久虚弱、失明/缓慢、挖掘减速、不能横扫、陆地不能疾跑、受伤翻倍、
     * 移速降低、额外饥饿、掉落消失、猫主动攻击、主背包自转化等。
     * 选择器可以 Esc 关掉, 之后在 V 界面点击特质重新打开。
     */
    object Cleanse : PerkDefinition {
        override val id = "trait_04"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override val isOneTime = true
        override fun icon(): ItemStack = ItemStack(Items.MILK_BUCKET)

        override fun onAcquire(ctx: PerkContext) = openPicker(ctx.player)

        override fun onActivate(ctx: PerkContext) = openPicker(ctx.player)

        override fun onRemove(ctx: PerkContext) {
            PerkDownsideSuppression.clear(ctx.state)
            PerkDownsides.refresh(ctx.player, ctx.state)
        }

        private fun openPicker(player: ServerPlayer) {
            val state = PerkStateManager.get(player.uuid) ?: return
            val options = PerkDownsideSuppression.eligibleOwned(state)
            if (options.isEmpty()) {
                player.sendSystemMessage(
                    Component.translatable("random-perks-bingo.message.downside_none"),
                    true,
                )
                return
            }
            PerkNetworking.sendDownsideOpen(
                player,
                options.map {
                    PerkOptionData(
                        it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon(), it.isOneTime,
                        it.maxUses,
                        if (it.isOneTime) state.usesRemaining(it) else it.maxUses,
                    )
                },
            )
        }

        /** 客户端选择了要净化的词条 id */
        fun handlePick(player: ServerPlayer, perkId: String) {
            val state = PerkStateManager.get(player.uuid) ?: return
            val perk = state.ownedPerks.firstOrNull { it.id == id } ?: return
            if (state.usesRemaining(perk) <= 0) return
            if (perkId !in PerkDownsideSuppression.ELIGIBLE) return
            if (!state.hasPerk(perkId)) return

            PerkDownsideSuppression.set(state, perkId)
            state.consumeUse(perk)
            PerkDownsides.refresh(player, state)
            PerkGrantService.notifyPerkListChanged(player)
            PerkGrantService.syncAbilities(player)
            player.sendSystemMessage(
                Component.translatable(
                    "random-perks-bingo.message.downside_suppressed",
                    Component.translatable("random-perks-bingo.perk.$perkId.name"),
                ),
                false,
            )
        }
    }

    /** 当前还能选的特质 (排除已经拥有的) */
    fun available(state: PlayerPerkState): List<PerkDefinition> =
        ALL.filterNot { state.hasPerk(it.id) }

    fun registerAll() {
        ALL.forEach { PerkRegistry.register(it) }
    }
}

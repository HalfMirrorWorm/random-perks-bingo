package com.randomperks.perk.content

import com.randomperks.game.PerkApplicationHistory
import com.randomperks.game.PerkDownsideSuppression
import com.randomperks.game.PerkDownsides
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PerkHighRarityOffer
import com.randomperks.game.PerkRefreshCharges
import com.randomperks.network.PerkNetworking
import com.randomperks.network.PerkOptionData
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import com.randomperks.roll.PerkRollBias
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * 特质 (灰): 只能通过"特质选择"获得的特殊词条。
 *
 * <p>特质不参与普通三选一抽取, 也不计入流派联动 (archetype = TRAIT)。
 * 13 条中 `trait_01` 回放、`trait_02` 专精、`trait_03` 刷新、`trait_04` 净化、`trait_05` 再蓄、`trait_06` 精选,
 * 其余 7 条仍是预留占位 (名称/描述在语言文件里, 效果待填充)。
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
        for (i in 7..COUNT) add(Trait(i))
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

    private class Trait(index: Int) : PerkDefinition {
        override val id = "trait_${index.toString().padStart(2, '0')}"
        override val rarity = PerkRarity.TRAIT
        override val archetype = PerkArchetype.TRAIT
        override fun icon(): ItemStack = ItemStack(Items.DYE.gray())
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
     * 净化: 选择一个自己已拥有的词条, 使其**自身的负面效果**无效。
     *
     * <p>可选择范围见 [PerkDownsideSuppression.ELIGIBLE]:
     * 喵人斧的永久虚弱、开摆的失明/缓慢、老矿工/贪吃蛇的挖掘减速、
     * 珠光护手/老玩家的不能横扫、古拉鲨鱼的陆地不能疾跑。
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
                options.map { PerkOptionData(it.id, it.rarity.ordinal, it.archetype.ordinal, it.icon(), it.isOneTime) },
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

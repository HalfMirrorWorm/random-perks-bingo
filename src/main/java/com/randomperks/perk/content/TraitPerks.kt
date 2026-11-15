package com.randomperks.perk.content

import com.randomperks.game.PerkApplicationHistory
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import com.randomperks.state.PlayerPerkState
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * 特质 (灰): 只能通过"特质选择"获得的特殊词条。
 *
 * <p>特质不参与普通三选一抽取, 也不计入流派联动 (archetype = TRAIT)。
 * 13 条中 `trait_01` 是「回放」, 其余 12 条仍是预留占位
 * (名称/描述在语言文件里, 效果待填充)。
 */
object TraitPerks {

    /** 总共有 13 条特质 */
    const val COUNT = 13

    val ALL: List<PerkDefinition> = buildList {
        add(Replay)
        for (i in 2..COUNT) add(Trait(i))
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

    /** 当前还能选的特质 (排除已经拥有的) */
    fun available(state: PlayerPerkState): List<PerkDefinition> =
        ALL.filterNot { state.hasPerk(it.id) }

    fun registerAll() {
        ALL.forEach { PerkRegistry.register(it) }
    }
}

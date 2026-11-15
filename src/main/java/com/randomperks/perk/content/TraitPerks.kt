package com.randomperks.perk.content

import com.randomperks.perk.PerkArchetype
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
 * 当前 13 条全部是预留占位 (名称/描述在语言文件里, 效果待填充),
 * 先把抽取、选择、持久化、网络和界面流程跑通。
 */
object TraitPerks {

    /** 总共有 13 条特质 */
    const val COUNT = 13

    val ALL: List<PerkDefinition> = (1..COUNT).map { Trait(it) }

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

package com.randomperks.perk.content

import com.randomperks.game.CreativeFlashService
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

/**
 * 神话品质词条 (全局唯一: 同时只能被一名玩家拥有)。
 */
object MythicPerks {

    /** 神话 · 匠造 · 全局唯一: 获得 1.5 秒创造模式 */
    object CreativeFlash : PerkDefinition {
        override val id = "mythic_creative_flash"
        override val rarity = PerkRarity.MYTHIC
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.COMMAND_BLOCK)

        override fun onAcquire(ctx: PerkContext) {
            CreativeFlashService.flash(ctx.player)
        }
    }

    fun registerAll() {
        PerkRegistry.register(CreativeFlash)
    }
}

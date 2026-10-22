package com.randomperks.perk.content

import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.text.Text

/**
 * 唤法 SUMMON —— 召唤 / 随从 / 驭兽。
 */
object SummonPerks {

    /** 金 · 唤法: **驯龙高手** —— 右键任意生物即可骑乘 (玩家与 Boss 除外);
     *  骑乘时用 WASD 控制移动, 右键让坐骑使用它的能力 (远程攻击 / 近战 / 火球等),
     *  潜行下马。由 [com.randomperks.game.DragonTamerService] 实现。 */
    object DragonTamer : PerkDefinition {
        override val id = "summon_dragon_tamer"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.SUMMON
        override fun icon() = ItemStack(Items.DRAGON_HEAD)

        override fun onAcquire(ctx: PerkContext) {
            ctx.player.sendMessage(
                Text.translatable("random-perks-bingo.message.dragon_tamer_hint"),
                false,
            )
        }

        /** 对局结束/词条被清掉时别把人留在坐骑上 */
        override fun onRemove(ctx: PerkContext) {
            if (ctx.player.hasVehicle()) ctx.player.stopRiding()
        }
    }

    fun registerAll() {
        PerkRegistry.register(DragonTamer)
    }
}

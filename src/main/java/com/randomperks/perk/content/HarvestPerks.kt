package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRegistry
import com.randomperks.perk.PerkRarity
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

/**
 * 丰饶 HARVEST —— 作物 / 树木 / 动物。
 */
object HarvestPerks {

    /** 蓝 · 园艺家: 获得 16 面包 + 16 小麦种子 */
    object Gardener : PerkDefinition {
        override val id = "harvest_gardener"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.WHEAT)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.BREAD, 16))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.WHEAT_SEEDS, 16))
        }
    }

    /** 蓝 · 伐木工: 获得效率 III 的石斧 */
    object Lumberjack : PerkDefinition {
        override val id = "harvest_lumberjack"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.STONE_AXE)

        override fun onAcquire(ctx: PerkContext) {
            val axe = ItemStack(Items.STONE_AXE)
            PerkEffects.enchant(ctx.player, axe, Enchantments.EFFICIENCY, 3)
            PerkEffects.giveStack(ctx.player, axe)
        }
    }

    /** 蓝 · 牧羊人: 获得剪刀 + 16 羊毛 */
    object Herder : PerkDefinition {
        override val id = "harvest_herder"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.SHEARS)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.SHEARS))
            PerkEffects.giveStack(ctx.player, ItemStack(Items.WHITE_WOOL, 16))
        }
    }

    /** 紫 · 樵夫之手: 破坏原木时 50% 概率额外掉落一个原木 */
    object Timber : PerkDefinition {
        override val id = "harvest_timber"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.OAK_LOG)

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.util.math.BlockPos, blockState: net.minecraft.block.BlockState) {
            if (!blockState.isIn(net.minecraft.registry.tag.BlockTags.LOGS)) return
            if (ctx.player.random.nextFloat() >= 0.50f) return
            PerkEffects.giveStack(ctx.player, ItemStack(blockState.block))
        }
    }

    /** 紫 · 丰收之息: 获得 32 胡萝卜 + 锄头(效率III) + 永久饱和 (减少觅食压力) */
    object Bounty : PerkDefinition {
        override val id = "harvest_bounty"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.CARROT)

        override fun onAcquire(ctx: PerkContext) {
            PerkEffects.giveStack(ctx.player, ItemStack(Items.CARROT, 32))
            val hoe = ItemStack(Items.IRON_HOE)
            PerkEffects.enchant(ctx.player, hoe, Enchantments.EFFICIENCY, 3)
            PerkEffects.giveStack(ctx.player, hoe)
        }
    }

    /** 蓝 · 丰饶: 饱食度全满时, 随机获得一种"谜之炖菜"的正面效果 */
    object BountifulMeal : PerkDefinition {
        override val id = "harvest_bountiful_meal"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.HARVEST
        override fun icon() = ItemStack(Items.SUSPICIOUS_STEW)

        /** 谜之炖菜的纯正面效果 */
        private val STEW_EFFECTS = listOf(
            StatusEffects.SATURATION,
            StatusEffects.NIGHT_VISION,
            StatusEffects.JUMP_BOOST,
            StatusEffects.FIRE_RESISTANCE,
            StatusEffects.REGENERATION,
        )

        override fun onTick(ctx: PerkContext) {
            val player = ctx.player
            // 仅在饱食度全满时生效
            if (player.hungerManager.foodLevel < 20) return
            // 已有任一炖菜效果时等待其结束 (结束后会自动重新随机)
            if (STEW_EFFECTS.any { player.hasStatusEffect(it) }) return

            val pick = STEW_EFFECTS[player.random.nextInt(STEW_EFFECTS.size)]
            player.addStatusEffect(
                StatusEffectInstance(pick, 30 * 20, 0, true, false, true)
            )
        }
    }

    fun registerAll() {
        PerkRegistry.register(Gardener)
        PerkRegistry.register(Lumberjack)
        PerkRegistry.register(Herder)
        PerkRegistry.register(Timber)
        PerkRegistry.register(Bounty)
        PerkRegistry.register(BountifulMeal)
    }
}

package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import net.minecraft.block.Blocks
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.Identifier

/**
 * 深掘 DELVER —— 挖矿 / 地下探索。
 */
object DelverPerks {

    private val MOD_MINING = Identifier.of("random-perks-bingo", "delver_mining")

    /** 蓝 · 矿工手腕: 挖掘速度 +20% */
    object Mining : PerkDefinition {
        override val id = "delver_mining"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.IRON_PICKAXE)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.BLOCK_BREAK_SPEED, MOD_MINING, 0.20, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.BLOCK_BREAK_SPEED, MOD_MINING)
    }

    /** 蓝 · 地下视野: 永久夜视 */
    object NightVision : PerkDefinition {
        override val id = "delver_night_vision"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.GOLDEN_HELMET)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) = PerkEffects.permanentEffect(ctx.player, StatusEffects.NIGHT_VISION, 0)

        override fun onRemove(ctx: PerkContext) = PerkEffects.removeEffect(ctx.player, StatusEffects.NIGHT_VISION)
    }

    /** 蓝 · 坚韧: 抗性提升 I */
    object Toughness : PerkDefinition {
        override val id = "delver_toughness"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.SHIELD)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) = PerkEffects.permanentEffect(ctx.player, StatusEffects.RESISTANCE, 0)

        override fun onRemove(ctx: PerkContext) = PerkEffects.removeEffect(ctx.player, StatusEffects.RESISTANCE)
    }

    /** 紫 · 深层专注: 永久急迫 II */
    object Haste : PerkDefinition {
        override val id = "delver_haste"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.DIAMOND_PICKAXE)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) = PerkEffects.permanentEffect(ctx.player, StatusEffects.HASTE, 1)

        override fun onRemove(ctx: PerkContext) = PerkEffects.removeEffect(ctx.player, StatusEffects.HASTE)
    }

    /** 紫 · 矿脉嗅觉: 挖掘石头类方块时有 8% 概率额外掉落一份 (含矿石) */
    object VeinScent : PerkDefinition {
        override val id = "delver_vein_scent"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.RAW_IRON)

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.util.math.BlockPos, blockState: net.minecraft.block.BlockState) {
            // 只对石头/深板岩生效, 避免刷木头刷土
            val block = blockState.block
            if (block != Blocks.STONE && block != Blocks.DEEPSLATE && block != Blocks.COBBLESTONE && block != Blocks.COBBLED_DEEPSLATE) return
            if (ctx.player.random.nextFloat() >= 0.08f) return

            val drop = if (block == Blocks.DEEPSLATE || block == Blocks.COBBLED_DEEPSLATE) {
                ItemStack(Blocks.COBBLED_DEEPSLATE)
            } else {
                ItemStack(Blocks.COBBLESTONE)
            }
            PerkEffects.giveStack(ctx.player, drop)
        }
    }

    /** 蓝 · 深掘: 未穿护甲时获得 +10 护甲值 (鞘翅不算护甲) */
    object IronSkin : PerkDefinition {
        override val id = "delver_iron_skin"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.IRON_CHESTPLATE)

        private val MOD_IRON_SKIN = Identifier.of("random-perks-bingo", "delver_iron_skin")

        override fun onAcquire(ctx: PerkContext) = refresh(ctx)
        override fun onRespawn(ctx: PerkContext) = refresh(ctx)

        /** 每秒校验装备状态, 穿/脱护甲时自动生效或失效 */
        override fun onTick(ctx: PerkContext) = refresh(ctx)

        private fun refresh(ctx: PerkContext) {
            if (PerkEffects.hasNoArmor(ctx.player)) {
                PerkEffects.setAttribute(
                    ctx.player, EntityAttributes.ARMOR, MOD_IRON_SKIN,
                    10.0, EntityAttributeModifier.Operation.ADD_VALUE,
                )
            } else {
                PerkEffects.removeAttribute(ctx.player, EntityAttributes.ARMOR, MOD_IRON_SKIN)
            }
        }

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.ARMOR, MOD_IRON_SKIN)
    }

    fun registerAll() {
        PerkRegistry.register(Mining)
        PerkRegistry.register(NightVision)
        PerkRegistry.register(Toughness)
        PerkRegistry.register(Haste)
        PerkRegistry.register(VeinScent)
        PerkRegistry.register(IronSkin)
    }
}

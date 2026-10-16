package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.PerkBoostQuery
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.server.world.ServerWorld
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

    /** 紫 · 矿脉嗅觉: 挖掘矿石时有 8% 概率额外掉落一份 —— 以**掉落物**形态落在地上,
     *  因此会参与"透视之眼"的 50% 消失惩罚、"收藏家"的复制加成等一切掉落物交互 */
    object VeinScent : PerkDefinition {
        override val id = "delver_vein_scent"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.RAW_IRON)

        /** 矿石判定: 与原版矿石标签一致; 远古残骸与 modded 矿石按名称兜底 */
        private val ORE_TAGS = listOf(
            BlockTags.COAL_ORES,
            BlockTags.IRON_ORES,
            BlockTags.COPPER_ORES,
            BlockTags.GOLD_ORES,
            BlockTags.REDSTONE_ORES,
            BlockTags.LAPIS_ORES,
            BlockTags.DIAMOND_ORES,
            BlockTags.EMERALD_ORES,
        )

        private fun isOre(state: BlockState): Boolean {
            if (ORE_TAGS.any { state.isIn(it) }) return true
            val path = Registries.BLOCK.getId(state.block).path
            return path == "ancient_debris" || path.contains("ore")
        }

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.util.math.BlockPos, blockState: net.minecraft.block.BlockState) {
            if (!isOre(blockState)) return
            // 联动"透视之眼"代价: 本 tick 该玩家的挖块掉落若被抑制, 额外份一并抑制
            val suppressTick = ctx.state.memory[PerkBoostQuery.XRAY_SUPPRESS_TICK_KEY]
            if (suppressTick != null && suppressTick == (ctx.player.server?.ticks ?: -1).toString()) return
            if (ctx.player.random.nextFloat() >= 0.08f) return

            val world = ctx.player.world as? ServerWorld ?: return
            // "该方块的真实掉落" (自动包含时运/精准采集的结果), 以掉落物形态落在方块位置
            val drops = Block.getDroppedStacks(
                blockState, world, pos, null,
                ctx.player, ctx.player.mainHandStack,
            )
            for (drop in drops) {
                val entity = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, drop.copy())
                entity.setToDefaultPickupDelay()
                world.spawnEntity(entity)
            }
        }
    }

    /** 蓝 · 深掘: 未穿护甲时获得 +7 护甲值 (鞘翅不算护甲) */
    object IronSkin : PerkDefinition {
        override val id = "delver_iron_skin"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.IRON_CHESTPLATE)

        private val MOD_IRON_SKIN = Identifier.of("random-perks-bingo", "delver_iron_skin")

        override fun onAcquire(ctx: PerkContext) = refresh(ctx)
        override fun onRespawn(ctx: PerkContext) = refresh(ctx)

        /** 穿/脱护甲的瞬间即时重算 (由 EquipmentChangeMixin 触发) */
        override fun onEquipmentChange(ctx: PerkContext) = refresh(ctx)

        private fun refresh(ctx: PerkContext) {
            if (PerkEffects.hasNoArmor(ctx.player)) {
                PerkEffects.setAttribute(
                    ctx.player, EntityAttributes.ARMOR, MOD_IRON_SKIN,
                    7.0, EntityAttributeModifier.Operation.ADD_VALUE,
                )
            } else {
                PerkEffects.removeAttribute(ctx.player, EntityAttributes.ARMOR, MOD_IRON_SKIN)
            }
        }

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.ARMOR, MOD_IRON_SKIN)
    }

    /** 紫 · 深掘: 20 格内矿石透视 (客户端渲染); 代价: 用镐子挖到的物品 50% 概率消失
     *  (由 XrayDropSuppressMixin 在掉落生成处直接抑制 —— 不生成, 而非事后删除) */
    object XrayVision : PerkDefinition {
        override val id = "delver_xray_vision"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.ENDER_EYE)
        // 透视渲染在客户端 (XrayRenderer); 掉落抑制在 XrayDropSuppressMixin
    }

    /** 紫 · 深掘: 用镐子挖方块时, 按镐子等级概率获得随机物品 (木0.1% 石0.5% 铁1% 钻/下界合金10%) */
    object LuckyDigger : PerkDefinition {
        override val id = "delver_lucky_digger"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.GOLDEN_PICKAXE)

        private val LOOT_POOL = listOf(
            ItemStack(Items.IRON_INGOT, 2),
            ItemStack(Items.GOLD_INGOT),
            ItemStack(Items.DIAMOND),
            ItemStack(Items.EMERALD),
            ItemStack(Items.REDSTONE, 4),
            ItemStack(Items.LAPIS_LAZULI, 4),
            ItemStack(Items.COPPER_INGOT, 4),
            ItemStack(Items.FLINT),
            ItemStack(Items.BLAZE_POWDER, 2),
            ItemStack(Items.ENDER_PEARL),
            ItemStack(Items.GOLDEN_APPLE),
        )

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.util.math.BlockPos, blockState: BlockState) {
            val chance = when (ctx.player.mainHandStack.item) {
                Items.WOODEN_PICKAXE -> 0.001f
                Items.STONE_PICKAXE -> 0.005f
                Items.IRON_PICKAXE -> 0.01f
                Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE -> 0.10f
                else -> return
            }
            if (ctx.player.random.nextFloat() >= chance) return

            val prize = LOOT_POOL[ctx.player.random.nextInt(LOOT_POOL.size)]
            PerkEffects.giveStack(ctx.player, prize.copy())
        }
    }

    /** 紫 · 深掘: 挖掘时相当于时运 II (40% 概率额外掉落一份), 但挖掘速度降低 50% (不影响攻速) */
    object Collector : PerkDefinition {
        override val id = "delver_collector"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.DIAMOND)

        private val MOD_MINING = Identifier.of("random-perks-bingo", "collector_mining")
        private const val EXTRA_DROP_CHANCE = 0.40f

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        private fun apply(ctx: PerkContext) = PerkEffects.setAttribute(
            ctx.player, EntityAttributes.BLOCK_BREAK_SPEED, MOD_MINING,
            -0.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
        )

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, EntityAttributes.BLOCK_BREAK_SPEED, MOD_MINING)

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.util.math.BlockPos, blockState: BlockState) {
            if (ctx.player.random.nextFloat() >= EXTRA_DROP_CHANCE) return
            // 模拟时运 II: 复制一份刚生成的掉落物
            val world = ctx.player.world as? ServerWorld ?: return
            val box = net.minecraft.util.math.Box(pos).expand(1.5)
            world.getEntitiesByClass(ItemEntity::class.java, box) { it.age <= 1 }
                .forEach { world.spawnEntity(ItemEntity(world, it.x, it.y, it.z, it.stack.copy())) }
        }
    }

    fun registerAll() {
        PerkRegistry.register(Mining)
        PerkRegistry.register(NightVision)
        PerkRegistry.register(Toughness)
        PerkRegistry.register(Haste)
        PerkRegistry.register(VeinScent)
        PerkRegistry.register(IronSkin)
        PerkRegistry.register(XrayVision)
        PerkRegistry.register(LuckyDigger)
        PerkRegistry.register(Collector)
    }
}

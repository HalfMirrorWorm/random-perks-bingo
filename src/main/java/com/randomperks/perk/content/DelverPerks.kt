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
import net.minecraft.block.Blocks
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.ItemTags
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

    /** 紫 · 矿脉嗅觉: 挖掘矿石时有概率额外掉落一份 (基础 8%; 每拥有一个其他深掘词条 +3%)
     *  —— 以**掉落物**形态落在地上, 因此会参与"透视之眼"的 50% 消失惩罚等一切掉落物交互 */
    object VeinScent : PerkDefinition {
        override val id = "delver_vein_scent"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.RAW_IRON)

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.util.math.BlockPos, blockState: net.minecraft.block.BlockState) {
            if (!isOreBlock(blockState)) return
            // 联动"透视之眼"代价: 本 tick 该玩家的挖块掉落若被抑制, 额外份一并抑制
            val suppressTick = ctx.state.memory[PerkBoostQuery.XRAY_SUPPRESS_TICK_KEY]
            if (suppressTick != null && suppressTick == (ctx.player.server?.ticks ?: -1).toString()) return
            // 流派联动: 每拥有一个其他深掘词条, 概率 +3% (触发时实时计算, 无需缓存/事件)
            val allies = (ctx.countArchetype(PerkArchetype.DELVER) - 1).coerceAtLeast(0)
            val chance = 0.08f + 0.03f * allies
            if (ctx.player.random.nextFloat() >= chance) return

            val world = ctx.player.world as? ServerWorld ?: return
            spawnBlockDropsOnce(ctx.player, world, pos, blockState)
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

    /** 紫 · 深掘: 用镐子挖方块时, 按**方块所需的镐等级**概率掉落一件随机物品
     *  (需求木/石/铁/钻石镐的方块分别为 0.1% / 0.5% / 1% / 10%); 奖品取自全部原版物品,
     *  排除只能通过指令/创造获取的物品。以掉落物形态落地 —— 与"矿脉嗅觉"同范式,
     *  参与透视之眼的惩罚与收藏家的复制等一切掉落物交互。 */
    object LuckyDigger : PerkDefinition {
        override val id = "delver_lucky_digger"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.GOLDEN_PICKAXE)

        /** 不可作为奖品的物品: 只能指令/创造获取的, 以及"无数据组件即为废品"的模板物品 */
        private val EXCLUDED_ITEMS = setOf(
            Items.AIR,
            Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK, Items.REPEATING_COMMAND_BLOCK,
            Items.COMMAND_BLOCK_MINECART, Items.BARRIER, Items.STRUCTURE_VOID,
            Items.STRUCTURE_BLOCK, Items.JIGSAW, Items.LIGHT, Items.DEBUG_STICK,
            Items.KNOWLEDGE_BOOK, Items.PLAYER_HEAD,
            Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION,  // 裸药水无效果
            Items.TIPPED_ARROW, Items.ENCHANTED_BOOK, Items.FIREWORK_ROCKET, // 无组件即废品
            Items.FILLED_MAP, Items.WRITTEN_BOOK,
        )

        /** 全部原版物品的奖池 (排除清单 + 刷怪蛋), 首次使用时构建 */
        private val LOOT_POOL: List<Item> by lazy {
            Registries.ITEM.filter { item ->
                Registries.ITEM.getId(item).let { id ->
                    id.namespace == "minecraft" &&
                        item !in EXCLUDED_ITEMS &&
                        !id.path.endsWith("_spawn_egg")
                }
            }
        }

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.util.math.BlockPos, blockState: BlockState) {
            if (!ctx.player.mainHandStack.isIn(ItemTags.PICKAXES)) return   // 用镐子挖

            // 按"方块的需求镐等级"分档 (从高到低判; 1.21 没有"木镐"需求标签,
            // 以"掉落需要正确工具"兜底为木档 —— 石头/煤等)
            val chance = when {
                blockState.isIn(BlockTags.NEEDS_DIAMOND_TOOL) -> 0.10f
                blockState.isIn(BlockTags.NEEDS_IRON_TOOL) -> 0.01f
                blockState.isIn(BlockTags.NEEDS_STONE_TOOL) -> 0.005f
                blockState.isToolRequired -> 0.001f
                else -> return   // 无需工具的方块 (土/木/沙等) 不触发
            }
            if (ctx.player.random.nextFloat() >= chance) return

            // 联动"透视之眼"代价: 本 tick 该玩家的挖块掉落若被抑制, 奖品一并抑制 (与矿脉嗅觉同范式)
            val suppressTick = ctx.state.memory[PerkBoostQuery.XRAY_SUPPRESS_TICK_KEY]
            if (suppressTick != null && suppressTick == (ctx.player.server?.ticks ?: -1).toString()) return

            val world = ctx.player.world as? ServerWorld ?: return
            val prize = LOOT_POOL[ctx.player.random.nextInt(LOOT_POOL.size)]
            val entity = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, ItemStack(prize))
            entity.setToDefaultPickupDelay()
            world.spawnEntity(entity)
        }
    }

    /** 紫 · 深掘: 破坏矿石或石头时 40% 概率额外生成一份该方块的掉落 (时运 II 风味),
     *  但挖掘速度降低 50% (不影响攻速); 额外份为掉落物形态, 参与透视之眼惩罚等一切掉落物交互 */
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
            // 只对矿石与石头生效
            if (!isOreBlock(blockState) && !isStoneBlock(blockState)) return
            // 联动"透视之眼"代价: 本 tick 该玩家的挖块掉落若被抑制, 额外份一并抑制 (与矿脉嗅觉同范式)
            val suppressTick = ctx.state.memory[PerkBoostQuery.XRAY_SUPPRESS_TICK_KEY]
            if (suppressTick != null && suppressTick == (ctx.player.server?.ticks ?: -1).toString()) return
            if (ctx.player.random.nextFloat() >= EXTRA_DROP_CHANCE) return

            val world = ctx.player.world as? ServerWorld ?: return
            spawnBlockDropsOnce(ctx.player, world, pos, blockState)
        }
    }

    /** 矿石判定 (矿脉嗅觉/收藏家共用): 与原版矿石标签一致; 远古残骸与 modded 矿石按名称兜底 */
    private val ORE_CHECK_TAGS = listOf(
        BlockTags.COAL_ORES,
        BlockTags.IRON_ORES,
        BlockTags.COPPER_ORES,
        BlockTags.GOLD_ORES,
        BlockTags.REDSTONE_ORES,
        BlockTags.LAPIS_ORES,
        BlockTags.DIAMOND_ORES,
        BlockTags.EMERALD_ORES,
    )

    private fun isOreBlock(state: BlockState): Boolean {
        if (ORE_CHECK_TAGS.any { state.isIn(it) }) return true
        val path = Registries.BLOCK.getId(state.block).path
        return path == "ancient_debris" || path.contains("ore")
    }

    /** "石头"判定: 自然石头 (石头/花岗岩/闪长岩/安山岩/凝灰岩/深板岩) + 两种圆石 */
    private fun isStoneBlock(state: BlockState): Boolean =
        state.isIn(BlockTags.BASE_STONE_OVERWORLD) ||
            state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.COBBLED_DEEPSLATE)

    /** 生成一份"该方块的真实掉落"作为掉落物 (自动含时运/精准采集结果; 供矿脉嗅觉/收藏家复用) */
    private fun spawnBlockDropsOnce(
        player: net.minecraft.server.network.ServerPlayerEntity,
        world: ServerWorld,
        pos: net.minecraft.util.math.BlockPos,
        blockState: BlockState,
    ) {
        val drops = Block.getDroppedStacks(blockState, world, pos, null, player, player.mainHandStack)
        for (drop in drops) {
            val entity = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, drop.copy())
            entity.setToDefaultPickupDelay()
            world.spawnEntity(entity)
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

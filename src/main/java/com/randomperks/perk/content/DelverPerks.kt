package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.game.OnionCutService
import com.randomperks.game.PerkBoostQuery
import com.randomperks.game.SoulOutService
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.EquipmentSlot
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
import net.minecraft.util.math.BlockPos
import kotlin.math.abs

/**
 * 深掘 DELVER —— 挖矿 / 地下探索。
 */
object DelverPerks {

    private val MOD_MINING = Identifier.of("random-perks-bingo", "delver_mining")

    /** 蓝 · 矿工手腕: 挖掘速度 +15%; 挖**矿石**时总计 +40%
     *  (矿石部分由 MinerWristMixin 在 PlayerEntity.getBlockBreakingSpeed 里补足到 1.40 倍) */
    object Mining : PerkDefinition {
        override val id = "delver_mining"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.IRON_PICKAXE)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, EntityAttributes.BLOCK_BREAK_SPEED, MOD_MINING, 0.15, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE)

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

    /** 蓝 · 匠造: 抗性提升 I */
    object Toughness : PerkDefinition {
        override val id = "delver_toughness"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
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
     *  —— 以**掉落物**形态落在地上, 因此会参与"透视之眼"的 35% 消失惩罚等一切掉落物交互 */
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
            PerkEffects.spawnBlockDrops(ctx.player, world, pos, blockState)
        }
    }

    /** 蓝 · 匠造: 未穿护甲时获得 +7 护甲值 (鞘翅不算护甲) */
    object IronSkin : PerkDefinition {
        override val id = "delver_iron_skin"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
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

    /** 金 · 深掘: 10 格内矿石透视 (客户端渲染); 代价: 用镐子挖到的物品 35% 概率消失
     *  (由 XrayDropSuppressMixin 在掉落生成处直接抑制 —— 不生成, 而非事后删除) */
    object XrayVision : PerkDefinition {
        override val id = "delver_xray_vision"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.ENDER_EYE)
        // 透视渲染在客户端 (XrayRenderer); 掉落抑制在 XrayDropSuppressMixin
    }

    /** 蓝 · 深掘: 用镐子挖方块时, 按**方块所需的镐等级**概率掉落一件随机物品
     *  (需求木/石/铁/钻石镐的方块分别为 0.1% / 0.5% / 1% / 10%); 奖品取自全部原版物品
     *  (**含刷怪蛋**), 只排除纯指令方块/工具与"无组件即废品"的模板物品。
     *  以掉落物形态落地 —— 与"矿脉嗅觉"同范式, 参与透视之眼的惩罚与收藏家的复制等一切掉落物交互。 */
    object LuckyDigger : PerkDefinition {
        override val id = "delver_lucky_digger"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.GOLDEN_PICKAXE)

        /** 不可作为奖品的物品: 纯指令方块/工具, 以及"无数据组件即为废品"的模板物品。
         *  刷怪蛋**在奖池里** (原版只能创造/指令获取, 这里刻意当奖品) */
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

        /** 全部原版物品的奖池 (排除清单), 首次使用时构建 */
        private val LOOT_POOL: List<Item> by lazy {
            Registries.ITEM.filter { item ->
                Registries.ITEM.getId(item).let { id ->
                    id.namespace == "minecraft" && item !in EXCLUDED_ITEMS
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
            PerkEffects.spawnBlockDrops(ctx.player, world, pos, blockState)
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

    /** "石头"判定: 自然石头 (石头/花岗岩/闪长岩/安山岩/凝灰岩/深板岩) +
     *  下界石头 (下界岩/玄武岩/黑石) + 两种圆石 */
    private fun isStoneBlock(state: BlockState): Boolean =
        state.isIn(BlockTags.BASE_STONE_OVERWORLD) ||
            state.isIn(BlockTags.BASE_STONE_NETHER) ||
            state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.COBBLED_DEEPSLATE)

    /** 蓝 · 深掘: **八十** —— 用镐挖石头类方块时, 顺着你正在看的那个面,
     *  把以该方块为中心的 3×3 **同种**方块一并挖掉 (每块额外消耗 1 点耐久)。
     *  面朝向取玩家视线的主轴: 挖墙 → 竖直平面, 挖地面/天花板 → 水平平面。
     *
     *  支持主世界石头与下界石头 (下界岩/玄武岩/黑石)。
     *  掉落与主方块**共同结算**: 透视之眼的 35% 判定只对主方块掷一次,
     *  额外方块沿用结果, 且掉落统一生成在主方块的位置。 */
    object Eighty : PerkDefinition {
        override val id = "delver_eighty"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.IRON_PICKAXE)

        override fun onBlockBreak(ctx: PerkContext, pos: BlockPos, blockState: BlockState) {
            val player = ctx.player
            val tool = player.mainHandStack
            if (!tool.isIn(ItemTags.PICKAXES)) return          // 只认镐
            if (!isStoneBlock(blockState)) return              // 只认石头类方块
            val world = player.world as? ServerWorld ?: return

            val look = player.rotationVector
            val ax = abs(look.x); val ay = abs(look.y); val az = abs(look.z)
            val axis = when {
                ax >= ay && ax >= az -> 0                       // 面向 X 轴 → YZ 平面
                ay >= az -> 1                                   // 面向 Y 轴 → XZ 平面
                else -> 2                                       // 面向 Z 轴 → XY 平面
            }

            // 与主方块共同结算: 主方块本 tick 被透视惩罚 → 额外方块也整批不掉落
            val currentTick = (player.server?.ticks ?: 0).toString()
            val suppressed = ctx.state.memory[PerkBoostQuery.XRAY_SUPPRESS_TICK_KEY] == currentTick

            val broken = blockState.block
            for (a in -1..1) {
                for (b in -1..1) {
                    if (a == 0 && b == 0) continue              // 中心块已经挖掉了
                    val target = when (axis) {
                        0 -> pos.add(0, a, b)
                        1 -> pos.add(a, 0, b)
                        else -> pos.add(a, b, 0)
                    }
                    val targetState = world.getBlockState(target)
                    if (targetState.block !== broken) continue  // 同种类
                    if (tool.isEmpty) return                    // 耐久挖断了就停

                    // 只移除方块, 掉落统一按主方块的结算结果处理
                    if (!world.breakBlock(target, false, player)) continue
                    if (!suppressed) {
                        // 掉落在主方块位置生成 (自动含时运/精准采集)
                        PerkEffects.spawnBlockDrops(player, world, pos, targetState)
                        // 挖掘经验仍按每块结算
                        targetState.onStacksDropped(world, target, tool, true)
                    }
                    if (!player.isCreative) tool.damage(1, player, EquipmentSlot.MAINHAND)
                }
            }
        }
    }

    /** 紫 · 深掘: **连锁挖矿** —— 用镐挖矿石时, 把与它相连的**同种**矿石一并挖掉 (上限 64 块)。
     *  额外方块每块消耗 1 点耐久。
     *
     *  掉落与主矿石**共同结算**:
     *  - 透视之眼的 35% 不掉落只对主矿石判定一次, 连锁块沿用这个结果 (整批掉 / 整批没);
     *  - 连锁块的掉落统一生成在主矿石的位置, 和主矿石的掉落堆在一起;
     *  - 连锁块不再走 Block.dropStacks, 避免每块单独掷透视概率、再触发其他联动。 */
    object ChainMining : PerkDefinition {
        override val id = "delver_chain_mining"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.DIAMOND_ORE)

        /** 单次连锁最多额外挖掉的方块数 (防止巨型矿脉一次刷爆) */
        private const val MAX_CHAIN = 64

        override fun onBlockBreak(ctx: PerkContext, pos: BlockPos, blockState: BlockState) {
            val player = ctx.player
            val tool = player.mainHandStack
            if (!tool.isIn(ItemTags.PICKAXES)) return          // 只认镐
            if (!isOreBlock(blockState)) return                // 只认矿石
            val world = player.world as? ServerWorld ?: return

            // 主矿石的掉落已经由原版结算完毕: 若本 tick 的透视惩罚标记命中,
            // 说明这次挖矿被判定为"不生成掉落", 连锁块沿用同一结果, 不再逐块掷 35%。
            val currentTick = (player.server?.ticks ?: 0).toString()
            val suppressed = ctx.state.memory[PerkBoostQuery.XRAY_SUPPRESS_TICK_KEY] == currentTick

            val ore = blockState.block
            val visited = HashSet<BlockPos>()
            val queue = ArrayDeque<BlockPos>()
            visited.add(pos)
            queue.add(pos)

            var broken = 0
            while (queue.isNotEmpty() && broken < MAX_CHAIN) {
                val cur = queue.removeFirst()
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        for (dz in -1..1) {
                            if (dx == 0 && dy == 0 && dz == 0) continue
                            val next = cur.add(dx, dy, dz)
                            if (!visited.add(next)) continue

                            val chainState = world.getBlockState(next)
                            if (chainState.block !== ore) continue                   // 同种矿石
                            if (tool.isEmpty) return                                 // 挖断了就停
                            // 只移除方块, 不在这里生成掉落 —— 掉落统一在下面按主矿石的结算结果处理
                            if (!world.breakBlock(next, false, player)) continue
                            if (!suppressed) {
                                // 掉落在主矿石的位置生成 (自动含时运/精准采集), 与主矿石的掉落堆在一起
                                PerkEffects.spawnBlockDrops(player, world, pos, chainState)
                                // 挖矿经验仍按每块结算 (原版 dropStacks 被跳过, 这里补上)
                                chainState.onStacksDropped(world, next, tool, true)
                            }
                            if (!player.isCreative) tool.damage(1, player, EquipmentSlot.MAINHAND)
                            broken++
                            if (broken >= MAX_CHAIN) return
                            queue.add(next)
                        }
                    }
                }
            }
        }
    }

    /** 蓝 · 深掘: **无忧的安眠** —— 在地狱 / 末地睡床时, 床的爆炸**范围翻倍、伤害 1.5 倍**
     *  (由 BedBombMixin 接管 BedBlock.onUse 里的 createExplosion 调用)。 */
    object CarefreeSleep : PerkDefinition {
        override val id = "delver_carefree_sleep"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.RED_BED)
    }

    /** 绿 · 深掘: **切洋葱** —— 手持镐右键黑曜石 / 哭泣的黑曜石, 在两者之间来回切换
     *  (交互由 OnionCutService 接管; 原版里哭泣的黑曜石无法合成, 只能靠废墟/交易获取) */
    object OnionCut : PerkDefinition {
        override val id = OnionCutService.PERK_ID
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.CRYING_OBSIDIAN)
    }

    /** 紫 · 深掘: **不死鸟** —— 免疫火焰伤害; 处于岩浆中时每秒恢复 2 点生命。
     *  免疫由 PhoenixMixin 在 LivingEntity.damage 的 HEAD 拦截 (仅 IS_FIRE 类伤害)。 */
    object Phoenix : PerkDefinition {
        override val id = "delver_phoenix"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.MAGMA_BLOCK)

        /** 每秒恢复的生命值 (onTick 每 20 tick 驱动一次) */
        private const val LAVA_HEAL = 2.0f

        override fun onTick(ctx: PerkContext) {
            if (!ctx.player.isInLava) return
            ctx.player.heal(LAVA_HEAL)
        }
    }

    /** 金 · 深掘: **灵魂出窍** (贮藏) —— 参考 FreeCam 类 mod: 在 V 界面点击使用后
     *  灵魂 (相机) 离体 60 秒, 可自由飞行并穿过方块; 身体留在原地、仍会受伤,
     *  期间无法移动/攻击/使用物品。 */
    object SoulOut : PerkDefinition {
        override val id = "delver_soul_out"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.SOUL_LANTERN)

        override val isOneTime = true

        override fun onActivate(ctx: PerkContext) = SoulOutService.activate(ctx)
        override fun onTick(ctx: PerkContext) = SoulOutService.tick(ctx)

        /** 词条被移除 / 对局结束时结束灵魂状态 (客户端相机会自动归位) */
        override fun onRemove(ctx: PerkContext) =
            SoulOutService.end(ctx.player, ctx.state)

        /** 重生时清理 (身体在灵魂状态下仍然会受伤, 死亡后要结束状态) */
        override fun onRespawn(ctx: PerkContext) =
            SoulOutService.end(ctx.player, ctx.state)
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
        PerkRegistry.register(Eighty)
        PerkRegistry.register(ChainMining)
        PerkRegistry.register(CarefreeSleep)
        PerkRegistry.register(OnionCut)
        PerkRegistry.register(Phoenix)
        PerkRegistry.register(SoulOut)
    }
}

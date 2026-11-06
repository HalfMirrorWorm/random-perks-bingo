package com.randomperks.perk.content

import com.randomperks.effect.PerkEffects
import com.randomperks.effect.PermanentEffects
import com.randomperks.game.OnionCutService
import com.randomperks.game.PerkBoostQuery
import com.randomperks.game.SoulOutService
import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.Blocks
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.tags.BlockTags
import net.minecraft.tags.ItemTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.resources.Identifier
import net.minecraft.util.Unit
import net.minecraft.core.BlockPos
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.Level
import kotlin.math.abs

/**
 * 深掘 DELVER —— 挖矿 / 地下探索。
 */
object DelverPerks {

    private val MOD_MINING = Identifier.fromNamespaceAndPath("random-perks-bingo", "delver_mining")

    /** 蓝 · 矿工手腕: 挖掘速度 +15%; 挖**矿石**时再额外 ×1.25 (合计约 ×1.4375)
     *  (矿石部分由 MinerWristMixin 在 PlayerEntity.getBlockBreakingSpeed 里乘算) */
    object Mining : PerkDefinition {
        override val id = "delver_mining"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.IRON_PICKAXE)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) =
            PerkEffects.setAttribute(ctx.player, Attributes.BLOCK_BREAK_SPEED, MOD_MINING, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, Attributes.BLOCK_BREAK_SPEED, MOD_MINING)
    }

    /** 紫 · 深掘: **贪吃蛇** —— 基础挖掘速度 -20%; 每挖掉一个矿石 +1%, **没有上限**。
     *  计数存在词条 memory 里, 重生保留, 对局结束清空。 */
    object GluttonousSnake : PerkDefinition {
        override val id = "delver_gluttonous_snake"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.RAW_GOLD)

        private val MOD_SPEED = Identifier.fromNamespaceAndPath("random-perks-bingo", "gluttonous_snake_speed")
        private const val KEY_STACKS = "gluttonous_snake_stacks"
        private const val BASE_PENALTY = -0.20
        private const val PER_ORE = 0.01

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        override fun onBlockBreak(ctx: PerkContext, pos: BlockPos, blockState: BlockState) {
            if (ctx.player.isCreative) return
            if (!PerkBoostQuery.isOreBlock(blockState)) return

            ctx.state.memory[KEY_STACKS] = (stacks(ctx) + 1).toString()
            apply(ctx)
        }

        private fun apply(ctx: PerkContext) {
            PerkEffects.setAttribute(
                ctx.player, Attributes.BLOCK_BREAK_SPEED, MOD_SPEED,
                BASE_PENALTY + PER_ORE * stacks(ctx), AttributeModifier.Operation.ADD_VALUE,
            )
        }

        private fun stacks(ctx: PerkContext): Int =
            (ctx.state.memory[KEY_STACKS]?.toIntOrNull() ?: 0).coerceAtLeast(0)

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, Attributes.BLOCK_BREAK_SPEED, MOD_SPEED)
    }

    /** 蓝 · 深掘: **矿洞幽灵** —— 身处矿洞时隐身。
     *
     *  "在矿洞"沿用原版洞穴环境音效的判定 (`BiomeEffectSoundPlayer` 每 tick 看玩家位置的
     *  **天空光**: 有天空光就衰减、为 0 才累积 mood 播 `ambient.cave`), 这里直接取
     *  `getLightLevel(LightType.SKY, 脚下)` == 0 —— 即**头顶看不到天空**。
     *
     *  每秒续一次 2 秒的隐身 (留 1 秒容错), 走出矿洞后最多 2 秒自然失效;
     *  不用 removeEffect 主动摘, 免得把玩家自己喝的隐身药水一起清掉。
     *
     *  只算主世界: 下界/末地本来就没有天空光, 套同一判定等于永久隐身。 */
    object CavePhantom : PerkDefinition {
        override val id = "delver_cave_phantom"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.ECHO_SHARD)

        /** 隐身续期时长 (tick): onTick 每秒一次, 40 tick 足够覆盖到下个周期 */
        private const val INVISIBILITY_TICKS = 40

        override fun onTick(ctx: PerkContext) {
            val player = ctx.player
            if (!isInCave(player)) return
            // 不能带粒子 —— 隐身粒子会直接暴露位置
            player.addEffect(
                MobEffectInstance(
                    MobEffects.INVISIBILITY, INVISIBILITY_TICKS, 0,
                    false, false, true,
                )
            )
        }

        /** 是否在矿洞里: 主世界 + 脚下的天空光为 0 */
        private fun isInCave(player: ServerPlayer): Boolean {
            val world = player.level()
            if (world.dimension() != Level.OVERWORLD) return false
            return world.getBrightness(LightLayer.SKY, player.blockPosition()) == 0
        }
    }

    /** 紫 · 深掘: **永生难忘** —— 获得一把**不可破坏的金镐**。
     *  只在获得词条时发一次, 死亡掉落/烧毁后不补发 (与千锤百炼、喵人斧一致)。 */
    object Unforgettable : PerkDefinition {
        override val id = "delver_unforgettable"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.GOLDEN_PICKAXE)

        override fun onAcquire(ctx: PerkContext) {
            val pickaxe = ItemStack(Items.GOLDEN_PICKAXE)
            // 1.21.7 的 unbreakable 是无值组件 (Unit), 设置后耐久不再消耗
            pickaxe.set(DataComponents.UNBREAKABLE, Unit.INSTANCE)
            PerkEffects.giveStack(ctx.player, pickaxe)
        }
    }

    /** 蓝 · 地下视野: 永久夜视 */
    object NightVision : PerkDefinition {
        override val id = "delver_night_vision"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.GOLDEN_HELMET)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) = PerkEffects.permanentEffect(ctx.player, MobEffects.NIGHT_VISION, 0)

        override fun onRemove(ctx: PerkContext) =
            PermanentEffects.removePermanent(ctx.player, MobEffects.NIGHT_VISION, 0)
    }

    /** 蓝 · 匠造: 抗性提升 I */
    object Toughness : PerkDefinition {
        override val id = "artisan_toughness"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.SHIELD)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) = PerkEffects.permanentEffect(ctx.player, MobEffects.RESISTANCE, 0)

        override fun onRemove(ctx: PerkContext) =
            PermanentEffects.removePermanent(ctx.player, MobEffects.RESISTANCE, 0)
    }

    /** 紫 · 深层专注: 永久急迫 II */
    object Haste : PerkDefinition {
        override val id = "delver_haste"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.DIAMOND_PICKAXE)

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)
        private fun apply(ctx: PerkContext) = PerkEffects.permanentEffect(ctx.player, MobEffects.HASTE, 1)

        override fun onRemove(ctx: PerkContext) =
            PermanentEffects.removePermanent(ctx.player, MobEffects.HASTE, 1)
    }

    /** 蓝 · 矿脉嗅觉: 挖掘矿石时 18% 概率额外掉落一份, 每拥有一个其他深掘词条 +3%
     *  —— 判定与掉落由 MiningDropMixin 在 Block.dropStacks 统一处理 (与老矿工合并概率,
     *  命中时把整份掉落翻倍, 而不是额外重掷一次时运) */
    object VeinScent : PerkDefinition {
        override val id = "delver_vein_scent"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.RAW_IRON)
    }

    /** 蓝 · 匠造: 未穿护甲时获得 +7 护甲值 (鞘翅不算护甲) */
    object IronSkin : PerkDefinition {
        override val id = "artisan_iron_skin"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.ARTISAN
        override fun icon() = ItemStack(Items.IRON_CHESTPLATE)

        private val MOD_IRON_SKIN = Identifier.fromNamespaceAndPath("random-perks-bingo", "artisan_iron_skin")

        /** 旧 id 留下的属性修饰符: 换前缀后要顺手摘掉, 否则会一直挂在玩家身上 */
        private val LEGACY_MOD_IRON_SKIN =
            Identifier.fromNamespaceAndPath("random-perks-bingo", "delver_iron_skin")

        override fun onAcquire(ctx: PerkContext) = refresh(ctx)
        override fun onRespawn(ctx: PerkContext) = refresh(ctx)

        /** 穿/脱护甲的瞬间即时重算 (由 EquipmentChangeMixin 触发) */
        override fun onEquipmentChange(ctx: PerkContext) = refresh(ctx)

        private fun refresh(ctx: PerkContext) {
            // 迁移: 旧版本用 delver_iron_skin 作为修饰符 id
            PerkEffects.removeAttribute(ctx.player, Attributes.ARMOR, LEGACY_MOD_IRON_SKIN)
            if (PerkEffects.hasNoArmor(ctx.player)) {
                PerkEffects.setAttribute(
                    ctx.player, Attributes.ARMOR, MOD_IRON_SKIN,
                    7.0, AttributeModifier.Operation.ADD_VALUE,
                )
            } else {
                PerkEffects.removeAttribute(ctx.player, Attributes.ARMOR, MOD_IRON_SKIN)
            }
        }

        override fun onRemove(ctx: PerkContext) {
            PerkEffects.removeAttribute(ctx.player, Attributes.ARMOR, MOD_IRON_SKIN)
            PerkEffects.removeAttribute(ctx.player, Attributes.ARMOR, LEGACY_MOD_IRON_SKIN)
        }
    }

    /** 金 · 深掘: 10 格内矿石透视 (客户端渲染); 代价: 用镐子挖到的物品 35% 概率消失
     *  (XrayDecisionMixin 在破坏流程开头掷判定, MiningDropMixin 在掉落生成处直接抑制 —— 不生成, 而非事后删除) */
    object XrayVision : PerkDefinition {
        override val id = "delver_xray_vision"
        override val rarity = PerkRarity.GOLD
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.ENDER_EYE)
        // 透视渲染在客户端 (XrayRenderer); 掉落抑制在 MiningDropMixin
    }

    /** 蓝 · 深掘: 使用镐挖掘方块时, 按**方块所需的挖掘等级**概率掉落一件随机物品
     *  (需求木/石/铁/钻石镐的方块分别为 1% / 2% / 5% / 30%); 奖品取自全部原版物品
     *  (**含刷怪蛋**), 只排除纯指令方块/工具与"无组件即废品"的模板物品。
     *  以掉落物形态落地 —— 参与透视之眼的惩罚; 老矿工只复制方块本身的掉落, 不复制奖品。 */
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
            BuiltInRegistries.ITEM.filter { item ->
                BuiltInRegistries.ITEM.getKey(item).let { id ->
                    id.namespace == "minecraft" && item !in EXCLUDED_ITEMS
                }
            }
        }

        override fun onBlockBreak(ctx: PerkContext, pos: net.minecraft.core.BlockPos, blockState: BlockState) {
            if (!ctx.player.mainHandItem.`is`(ItemTags.PICKAXES)) return   // 用镐子挖
            if (!ctx.player.hasCorrectToolForDrops(blockState)) return                   // 挖了也不掉落的方块不触发

            // 按"方块的需求镐等级"分档 (从高到低判; 1.21 没有"木镐"需求标签,
            // 以"掉落需要正确工具"兜底为木档 —— 石头/煤等)
            val chance = when {
                blockState.`is`(BlockTags.NEEDS_DIAMOND_TOOL) -> 0.30f
                blockState.`is`(BlockTags.NEEDS_IRON_TOOL) -> 0.05f
                blockState.`is`(BlockTags.NEEDS_STONE_TOOL) -> 0.02f
                blockState.requiresCorrectToolForDrops() -> 0.01f
                else -> return   // 无需工具的方块 (土/木/沙等) 不触发
            }
            if (ctx.player.random.nextFloat() >= chance) return

            // 联动"透视之眼"代价: 本次破坏被判定为不掉落 → 奖品一并抑制
            if (PerkBoostQuery.isXraySuppressed(ctx.player, pos)) return

            val world = ctx.player.level() as? ServerLevel ?: return
            val prize = LOOT_POOL[ctx.player.random.nextInt(LOOT_POOL.size)]
            val entity = ItemEntity(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, ItemStack(prize))
            entity.setDefaultPickUpDelay()
            world.addFreshEntity(entity)
        }
    }

    /** 紫 · 深掘: **老矿工** —— 破坏矿石或石头时 40% 概率额外获得一份掉落,
     *  挖掘等级 +1, 挖掘速度降低 50%。
     *  等级提升由 OldMinerMixin 实现 (空手或任何非镐物品视为木镐);
     *  概率判定与掉落由 MiningDropMixin 统一处理 (与矿脉嗅觉合并概率)。 */
    object Collector : PerkDefinition {
        override val id = "delver_collector"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.DIAMOND)

        private val MOD_MINING = Identifier.fromNamespaceAndPath("random-perks-bingo", "collector_mining")

        override fun onAcquire(ctx: PerkContext) = apply(ctx)
        override fun onRespawn(ctx: PerkContext) = apply(ctx)

        private fun apply(ctx: PerkContext) = PerkEffects.setAttribute(
            ctx.player, Attributes.BLOCK_BREAK_SPEED, MOD_MINING,
            -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
        )

        override fun onRemove(ctx: PerkContext) =
            PerkEffects.removeAttribute(ctx.player, Attributes.BLOCK_BREAK_SPEED, MOD_MINING)
    }

    /** 矿石判定 (矿脉嗅觉/老矿工共用): 与原版矿石标签一致; 远古残骸与 modded 矿石按名称兜底 */
    private val ORE_CHECK_TAGS = listOf(
        BlockTags.ORES,
    )

    private fun isOreBlock(state: BlockState): Boolean {
        if (ORE_CHECK_TAGS.any { state.`is`(it) }) return true
        val path = BuiltInRegistries.BLOCK.getKey(state.block).path
        return path == "ancient_debris" || path.endsWith("_ore")
    }

    /** "石头"判定: 自然石头 (石头/花岗岩/闪长岩/安山岩/凝灰岩/深板岩) +
     *  下界石头 (下界岩/玄武岩/黑石) + 两种圆石 */
    private fun isStoneBlock(state: BlockState): Boolean =
        state.`is`(BlockTags.BASE_STONE_OVERWORLD) ||
            state.`is`(BlockTags.BASE_STONE_NETHER) ||
            state.`is`(Blocks.COBBLESTONE) || state.`is`(Blocks.COBBLED_DEEPSLATE)

    /** 蓝 · 深掘: **八十** —— 用镐挖石头类方块时, 顺着你正在看的那个面,
     *  把以该方块为中心的 3×3 **同种**方块一并挖掉 (每块额外消耗 1 点耐久)。
     *  面朝向取玩家视线的主轴: 挖墙 → 竖直平面, 挖地面/天花板 → 水平平面。
     *
     *  支持主世界石头与下界石头 (下界岩/玄武岩/黑石)。
     *  掉落与主方块**共同结算**: 透视之眼与额外掉落的判定都由 BreakDecisionMixin
     *  在破坏流程开头掷一次, 3×3 整批共用; 命中额外掉落时整批翻倍。 */
    object Eighty : PerkDefinition {
        override val id = "delver_eighty"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.IRON_PICKAXE)

        override fun onBlockBreak(ctx: PerkContext, pos: BlockPos, blockState: BlockState) {
            val player = ctx.player
            val tool = player.mainHandItem
            if (!tool.`is`(ItemTags.PICKAXES)) return          // 只认镐
            if (!isStoneBlock(blockState)) return              // 只认石头类方块
            val world = player.level() as? ServerLevel ?: return

            val look = player.lookAngle
            val ax = abs(look.x); val ay = abs(look.y); val az = abs(look.z)
            val axis = when {
                ax >= ay && ax >= az -> 0                       // 面向 X 轴 → YZ 平面
                ay >= az -> 1                                   // 面向 Y 轴 → XZ 平面
                else -> 2                                       // 面向 Z 轴 → XY 平面
            }

            // 与主方块共同结算: 本次破坏的透视/额外掉落判定由 BreakDecisionMixin 在流程开头掷好
            val suppressed = PerkBoostQuery.isXraySuppressed(player, pos)
            val doubled = PerkBoostQuery.isExtraDropDoubled(player, pos)
            val dropCopies = if (doubled) 2 else 1

            val broken = blockState.block
            for (a in -1..1) {
                for (b in -1..1) {
                    if (a == 0 && b == 0) continue              // 中心块已经挖掉了
                    val target = when (axis) {
                        0 -> pos.offset(0, a, b)
                        1 -> pos.offset(a, 0, b)
                        else -> pos.offset(a, b, 0)
                    }
                    val targetState = world.getBlockState(target)
                    if (targetState.block !== broken) continue  // 同种类
                    if (tool.isEmpty) return                    // 耐久挖断了就停

                    // 只移除方块, 掉落统一按主方块的结算结果处理
                    if (!world.destroyBlock(target, false, player)) continue
                    // 创造模式不掉落 (与原版一致); 透视抑制时整批不掉落
                    if (!suppressed && !player.isCreative) {
                        // 掉落在主方块位置生成 (自动含时运/精准采集); 整批翻倍时按同一份结果生成两份
                        PerkEffects.spawnBlockDrops(player, world, pos, targetState, dropCopies)
                        // 挖掘经验仍按每块结算 (翻倍只翻物品)
                        targetState.spawnAfterBreak(world, target, tool, true)
                    }
                    if (!player.isCreative) tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND)
                }
            }
        }
    }

    /** 紫 · 深掘: **连锁挖矿** —— 用镐挖矿石时, 把与它相连的**同种**矿石一并挖掉 (上限 64 块)。
     *  额外方块每块消耗 1 点耐久。
     *
     *  掉落与主矿石**共同结算**:
     *  - 透视之眼与额外掉落的判定都由 BreakDecisionMixin 在流程开头掷一次, 整条矿脉共用;
     *  - 连锁块的掉落统一生成在主矿石的位置, 和主矿石的掉落堆在一起;
     *  - 连锁块不再走 Block.dropStacks, 避免每块单独掷骰、再触发其他联动;
     *  - 命中额外掉落时整条矿脉一起翻倍; 创造模式不掉落。 */
    object ChainMining : PerkDefinition {
        override val id = "delver_chain_mining"
        override val rarity = PerkRarity.PURPLE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.DIAMOND_ORE)

        /** 单次连锁最多额外挖掉的方块数 (防止巨型矿脉一次刷爆) */
        private const val MAX_CHAIN = 64

        override fun onBlockBreak(ctx: PerkContext, pos: BlockPos, blockState: BlockState) {
            val player = ctx.player
            val tool = player.mainHandItem
            if (!tool.`is`(ItemTags.PICKAXES)) return          // 只认镐
            if (!isOreBlock(blockState)) return                // 只认矿石
            if (!player.hasCorrectToolForDrops(blockState)) return         // 挖了也不掉落的矿石不触发
            val world = player.level() as? ServerLevel ?: return

            // 本次破坏的判定由 BreakDecisionMixin 在流程开头掷好, 整条矿脉共用
            val suppressed = PerkBoostQuery.isXraySuppressed(player, pos)
            val doubled = PerkBoostQuery.isExtraDropDoubled(player, pos)
            val dropCopies = if (doubled) 2 else 1

            // 按"矿石家族"比较: coal_ore 与 deepslate_coal_ore 是同一矿脉的两截,
            // 直接用方块实例比较会在深板岩交界处断开
            val oreKey = oreKey(blockState.block)
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
                            val next = cur.offset(dx, dy, dz)
                            if (!visited.add(next)) continue

                            val chainState = world.getBlockState(next)
                            if (oreKey(chainState.block) != oreKey) continue         // 同一矿脉 (含深板岩变种)
                            if (tool.isEmpty) return                                 // 挖断了就停
                            // 只移除方块, 不在这里生成掉落 —— 掉落统一在下面按主矿石的结算结果处理
                            if (!world.destroyBlock(next, false, player)) continue
                            // 创造模式不掉落 (与原版一致); 透视抑制时整批不掉落
                            if (!suppressed && !player.isCreative) {
                                // 掉落在主矿石的位置生成 (自动含时运/精准采集), 与主矿石的掉落堆在一起
                                PerkEffects.spawnBlockDrops(player, world, pos, chainState, dropCopies)
                                // 挖矿经验仍按每块结算 (翻倍只翻物品)
                                chainState.spawnAfterBreak(world, next, tool, true)
                            }
                            if (!player.isCreative) tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND)
                            broken++
                            if (broken >= MAX_CHAIN) return
                            queue.add(next)
                        }
                    }
                }
            }
        }

        /** 矿石"家族"键: 去掉 deepslate_ 前缀, 让原矿与深板岩变种算同一种 */
        private fun oreKey(block: net.minecraft.world.level.block.Block): String =
            BuiltInRegistries.BLOCK.getKey(block).path.removePrefix("deepslate_")
    }

    /** 蓝 · 深掘: **破壁人** —— 手持能挖黑曜石的镐时, 基岩可以像黑曜石一样被挖掉。
     *  (由 BedrockBreakMixin 在 BlockState.getDestroyProgress 里实现) */
    object Wallbreaker : PerkDefinition {
        override val id = "delver_wallbreaker"
        override val rarity = PerkRarity.BLUE
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.BEDROCK)
    }

    /** 绿 · 深掘: **无忧的安眠** —— 在地狱 / 末地睡床时, 床的爆炸**范围翻倍、伤害 1.5 倍**
     *  (由 BedBombMixin 接管 BedBlock.onUse 里的 createExplosion 调用)。 */
    object CarefreeSleep : PerkDefinition {
        override val id = "delver_carefree_sleep"
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.BED.red())
    }

    /** 绿 · 深掘: **切洋葱** —— 手持镐右键黑曜石 / 哭泣的黑曜石, 在两者之间来回切换
     *  (交互由 OnionCutService 接管; 原版里哭泣的黑曜石无法合成, 只能靠废墟/交易获取) */
    object OnionCut : PerkDefinition {
        override val id = OnionCutService.PERK_ID
        override val rarity = PerkRarity.GREEN
        override val archetype = PerkArchetype.DELVER
        override fun icon() = ItemStack(Items.CRYING_OBSIDIAN)
    }

    /** 金 · 深掘: **不死鸟** —— 免疫火焰伤害; 处于岩浆中时每秒恢复 2 点生命。
     *  免疫由 PhoenixMixin 在 LivingEntity.damage 的 HEAD 拦截 (仅 IS_FIRE 类伤害)。 */
    object Phoenix : PerkDefinition {
        override val id = "delver_phoenix"
        override val rarity = PerkRarity.GOLD
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
        PerkRegistry.register(GluttonousSnake)
        PerkRegistry.register(Unforgettable)
        PerkRegistry.register(NightVision)
        PerkRegistry.register(CavePhantom)
        PerkRegistry.register(Toughness)
        PerkRegistry.register(Haste)
        PerkRegistry.register(VeinScent)
        PerkRegistry.register(IronSkin)
        PerkRegistry.register(XrayVision)
        PerkRegistry.register(LuckyDigger)
        PerkRegistry.register(Collector)
        PerkRegistry.register(Eighty)
        PerkRegistry.register(ChainMining)
        PerkRegistry.register(Wallbreaker)
        PerkRegistry.register(CarefreeSleep)
        PerkRegistry.register(OnionCut)
        PerkRegistry.register(Phoenix)
        PerkRegistry.register(SoulOut)
    }
}


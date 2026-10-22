package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.state.PerkStateManager
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.loot.LootTable
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.loot.context.LootWorldContext
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.UUID

/**
 * "暖水考古" —— 用刷子刷珊瑚块, 按颜色刷出对应的矿物。
 *
 * <p>节奏与**原版刷可疑沙**一致: 按住右键, 刷子每 10 tick 刷一下, 刷满 [BRUSH_STEPS] 次
 * （约 5 秒）才完成 —— 不是右键一次立刻出结果。刷动期间原版 [net.minecraft.item.BrushItem]
 * 自己会播放刷子音效与方块碎屑粒子, 这里只负责计数与结算。
 *
 * <p>完成时:
 *  - 珊瑚块变成**失活**的同色珊瑚块 (不是消失);
 *  - 10% 概率掉该珊瑚颜色对应的矿物, 否则从**暖水海洋废墟的考古战利品表**里抽;
 *  - 每刷一下消耗 1 点耐久 (与原版一致)。
 */
object WarmArchaeologyService {

    const val PERK_ID = "seeker_warm_archaeology"

    /** 矿物的占比: 完成时先掷 10%, 命中就掉对应颜色的矿物, 否则走考古战利品表 */
    private const val MINERAL_CHANCE = 0.10f

    /** 刷满多少次才算完成 —— 原版刷可疑沙也是 10 次 (每次间隔 10 tick) */
    private const val BRUSH_STEPS = 10

    /** 两次刷动之间允许的最大间隔; 超过就当作重新开始 */
    private const val BRUSH_TIMEOUT_TICKS = 20L

    /** 单个玩家当前的刷动进度 */
    private data class BrushProgress(val pos: BlockPos, var steps: Int, var lastTick: Long)

    private val brushing = mutableMapOf<UUID, BrushProgress>()

    /** 暖水海洋废墟的考古战利品表 —— "暖水考古"这个名字就来自这里 */
    private val LOOT_TABLE: RegistryKey<LootTable> = RegistryKey.of(
        RegistryKeys.LOOT_TABLE,
        Identifier.ofVanilla("archaeology/ocean_ruin_warm"),
    )

    /**
     * 和 {@code archaeology/ocean_ruin_warm} 内容一致的备用池 (权重用重复次数表示)。
     * 战利品表取不到或生成 0 件时用它兜底, 免得刷珊瑚变成纯亏。
     */
    private val FALLBACK_LOOT: List<Item> = buildList {
        add(Items.ANGLER_POTTERY_SHERD)
        add(Items.SHELTER_POTTERY_SHERD)
        add(Items.SNORT_POTTERY_SHERD)
        add(Items.SNIFFER_EGG)
        add(Items.IRON_AXE)
        repeat(2) { add(Items.EMERALD) }
        repeat(2) { add(Items.WHEAT) }
        repeat(2) { add(Items.WOODEN_HOE) }
        repeat(2) { add(Items.COAL) }
        repeat(2) { add(Items.GOLD_NUGGET) }
    }

    /** 珊瑚块 → (失活后的方块, 颜色对应的矿物) */
    private data class CoralInfo(val deadBlock: Block, val mineral: Item)

    private val CORALS: Map<Block, CoralInfo> = mapOf(
        Blocks.TUBE_CORAL_BLOCK to CoralInfo(Blocks.DEAD_TUBE_CORAL_BLOCK, Items.LAPIS_LAZULI),       // 蓝
        Blocks.BRAIN_CORAL_BLOCK to CoralInfo(Blocks.DEAD_BRAIN_CORAL_BLOCK, Items.DIAMOND),          // 粉
        Blocks.BUBBLE_CORAL_BLOCK to CoralInfo(Blocks.DEAD_BUBBLE_CORAL_BLOCK, Items.AMETHYST_SHARD), // 紫
        Blocks.FIRE_CORAL_BLOCK to CoralInfo(Blocks.DEAD_FIRE_CORAL_BLOCK, Items.REDSTONE),           // 红
        Blocks.HORN_CORAL_BLOCK to CoralInfo(Blocks.DEAD_HORN_CORAL_BLOCK, Items.GOLD_NUGGET),        // 黄
    )

    /**
     * 每 10 tick 由 BrushItemMixin 调用一次 (与原版刷子的节奏一致)。
     *
     * @return true 表示这次刷动被"暖水考古"接管了
     */
    fun handleBrushTick(
        player: ServerPlayerEntity,
        world: ServerWorld,
        pos: BlockPos,
        stack: ItemStack,
    ): Boolean {
        if (!PerkSession.isEnabled()) return false

        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(PERK_ID)) return false
        if (!stack.isOf(Items.BRUSH)) return false

        val coral = CORALS[world.getBlockState(pos).block] ?: return false

        val now = world.server?.ticks?.toLong() ?: return false
        var progress = brushing[player.uuid]
        if (progress == null || progress.pos != pos || now - progress.lastTick > BRUSH_TIMEOUT_TICKS) {
            progress = BrushProgress(pos, 0, now)
            brushing[player.uuid] = progress
        }

        progress.lastTick = now
        progress.steps++

        // 和原版刷子一样, 每刷一下消耗 1 点耐久 (归零时自动损坏)
        stack.damage(1, player, EquipmentSlot.MAINHAND)

        if (progress.steps < BRUSH_STEPS) return true

        brushing.remove(player.uuid)
        completeBrush(player, world, pos, coral, stack)
        return true
    }

    /** 刷满之后: 变失活珊瑚 + 结算掉落 */
    private fun completeBrush(
        player: ServerPlayerEntity,
        world: ServerWorld,
        pos: BlockPos,
        coral: CoralInfo,
        tool: ItemStack,
    ) {
        // 刷完变失活 (活珊瑚被"刷掉"了), 而不是直接消失
        world.setBlockState(pos, coral.deadBlock.defaultState)
        world.playSound(
            null, pos,
            SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC,
            SoundCategory.BLOCKS,
            1.0f, 1.0f,
        )

        if (player.random.nextFloat() < MINERAL_CHANCE) {
            Block.dropStack(world, pos, ItemStack(coral.mineral))
        } else {
            dropArchaeologyLoot(player, world, pos, tool)
        }
    }

    /** 从暖水考古战利品表抽一次, 掉在方块所在位置 */
    private fun dropArchaeologyLoot(
        player: ServerPlayerEntity,
        world: ServerWorld,
        pos: BlockPos,
        tool: ItemStack,
    ) {
        val lookup = player.server?.reloadableRegistries
        if (lookup == null) {
            RandomPerksMod.logger.warn("[RandomPerks] coral loot: reloadableRegistries unavailable")
            return dropFallback(player, world, pos)
        }

        val table = lookup.getLootTable(LOOT_TABLE)
        if (table === LootTable.EMPTY) {
            RandomPerksMod.logger.warn("[RandomPerks] coral loot: table {} not found (EMPTY)", LOOT_TABLE)
            return dropFallback(player, world, pos)
        }

        // 原版 ARCHAEOLOGY 上下文要求 origin / this_entity / tool 三个参数**缺一不可**,
        // 少传任何一个 Builder.build() 都会抛 IllegalArgumentException。
        val params = LootWorldContext.Builder(world)
            .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
            .add(LootContextParameters.THIS_ENTITY, player)
            .add(LootContextParameters.TOOL, tool)
            .build(LootContextTypes.ARCHAEOLOGY)

        var dropped = 0
        try {
            table.generateLoot(params, player.random.nextLong()) { loot ->
                dropped++
                Block.dropStack(world, pos, loot)
            }
        } catch (e: Exception) {
            RandomPerksMod.logger.warn("[RandomPerks] coral loot: generation failed", e)
        }

        // 兜底: 表没取到 / 生成失败 / 生成为空时, 自己按同一张表的权重抽一件, 至少不会白刷
        if (dropped == 0) {
            dropFallback(player, world, pos)
        }
    }

    private fun dropFallback(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos) {
        RandomPerksMod.logger.warn("[RandomPerks] coral loot: using fallback pool")
        Block.dropStack(world, pos, ItemStack(FALLBACK_LOOT[player.random.nextInt(FALLBACK_LOOT.size)]))
    }
}

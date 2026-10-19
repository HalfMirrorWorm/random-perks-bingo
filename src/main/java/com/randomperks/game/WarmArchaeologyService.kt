package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.loot.LootTable
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.loot.context.LootWorldContext
import net.minecraft.loot.context.LootContextTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * "暖水考古" —— 用刷子刷珊瑚块, 按颜色刷出对应的矿物。
 *
 * 原版刷子只认可疑的沙/沙砾 (BrushableBlockEntity), 珊瑚不在其列; 所以这里自己接管
 * "手持刷子右键珊瑚块" 这个交互 (由 [PerkWorldEventService] 注册 UseBlockCallback)。
 *
 * 每刷一次:
 *  - 珊瑚块变成**失活**的同色珊瑚块 (不是消失);
 *  - 掉落从**暖水海洋废墟的考古战利品表**里抽一件, 其中 10% 换成该珊瑚颜色对应的矿物;
 *  - 刷子消耗 1 点耐久。
 */
object WarmArchaeologyService {

    const val PERK_ID = "harvest_warm_archaeology"

    /** 矿物的占比: 每刷一次先掷 10%, 命中就掉对应颜色的矿物, 否则走考古战利品表 */
    private const val MINERAL_CHANCE = 0.10f

    /** 暖水海洋废墟的考古战利品表 —— "暖水考古"这个名字就来自这里 */
    private val LOOT_TABLE: RegistryKey<LootTable> = RegistryKey.of(
        RegistryKeys.LOOT_TABLE,
        Identifier.ofVanilla("archaeology/ocean_ruin_warm"),
    )

    /** 珊瑚块 → (失活后的方块, 颜色对应的矿物) */
    private data class CoralInfo(val deadBlock: Block, val mineral: Item)

    private val CORALS: Map<Block, CoralInfo> = mapOf(
        Blocks.TUBE_CORAL_BLOCK to CoralInfo(Blocks.DEAD_TUBE_CORAL_BLOCK, Items.LAPIS_LAZULI),       // 蓝
        Blocks.BRAIN_CORAL_BLOCK to CoralInfo(Blocks.DEAD_BRAIN_CORAL_BLOCK, Items.DIAMOND),          // 粉
        Blocks.BUBBLE_CORAL_BLOCK to CoralInfo(Blocks.DEAD_BUBBLE_CORAL_BLOCK, Items.AMETHYST_SHARD), // 紫
        Blocks.FIRE_CORAL_BLOCK to CoralInfo(Blocks.DEAD_FIRE_CORAL_BLOCK, Items.REDSTONE),           // 红
        Blocks.HORN_CORAL_BLOCK to CoralInfo(Blocks.DEAD_HORN_CORAL_BLOCK, Items.GOLD_NUGGET),        // 黄
    )

    /** 手持刷子右键珊瑚块时调用; 返回 PASS 表示"这不是我管的事", 交还原版 */
    fun handleBrush(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos): ActionResult {
        if (!PerkSession.isEnabled()) return ActionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return ActionResult.PASS
        if (!state.hasPerk(PERK_ID)) return ActionResult.PASS

        val stack = player.getStackInHand(Hand.MAIN_HAND)
        if (!stack.isOf(Items.BRUSH)) return ActionResult.PASS

        val coral = CORALS[world.getBlockState(pos).block] ?: return ActionResult.PASS

        // 刷完变失活 (活珊瑚被"刷掉"了), 而不是直接消失
        world.setBlockState(pos, coral.deadBlock.defaultState)
        world.playSound(
            null, pos,
            SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC,
            SoundCategory.BLOCKS,
            1.0f, 1.0f,
        )

        // 10% 出颜色对应的矿物; 其余从暖水海洋废墟的考古战利品表里抽一件
        if (player.random.nextFloat() < MINERAL_CHANCE) {
            Block.dropStack(world, pos, ItemStack(coral.mineral))
        } else {
            dropArchaeologyLoot(player, world, pos)
        }

        // 和原版刷子一样消耗 1 点耐久 (归零时自动损坏)
        stack.damage(1, player, EquipmentSlot.MAINHAND)

        return ActionResult.SUCCESS
    }

    /** 从暖水考古战利品表抽一次, 掉在方块所在位置 */
    private fun dropArchaeologyLoot(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos) {
        val table = player.server?.reloadableRegistries?.getLootTable(LOOT_TABLE) ?: return

        val params = LootWorldContext.Builder(world)
            .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
            .build(LootContextTypes.ARCHAEOLOGY)

        table.generateLoot(params, player.random.nextLong()) { loot ->
            Block.dropStack(world, pos, loot)
        }
    }
}

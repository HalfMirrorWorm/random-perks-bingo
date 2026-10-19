package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

/**
 * "暖水考古" —— 用刷子刷珊瑚块, 按颜色刷出对应的矿物。
 *
 * 原版刷子只认可疑的沙/沙砾 (BrushableBlockEntity), 珊瑚不在其列; 所以这里自己接管
 * "手持刷子右键珊瑚块" 这个交互 (由 [PerkWorldEventService] 注册 UseBlockCallback)。
 *
 * 每刷一次: 珊瑚块消失、10% 概率掉一颗颜色对应的矿物、刷子消耗 1 点耐久。
 */
object WarmArchaeologyService {

    const val PERK_ID = "harvest_warm_archaeology"

    /**
     * 出矿物的概率。
     *
     * 需求里的"正常考古的 10%"按原版刷可疑方块理解: 那边每刷一下累加 1 点进度、
     * 攒满 10 点才掉一件东西 —— 也就是**每一下 10%**。这里直接取同一个数。
     */
    private const val MINERAL_CHANCE = 0.10f

    /** 珊瑚块 → 它颜色对应的矿物 (五种各不相同) */
    private val CORAL_MINERALS: Map<Block, Item> = mapOf(
        Blocks.TUBE_CORAL_BLOCK to Items.LAPIS_LAZULI,      // 蓝 → 青金石
        Blocks.BRAIN_CORAL_BLOCK to Items.DIAMOND,          // 粉 → 钻石
        Blocks.BUBBLE_CORAL_BLOCK to Items.AMETHYST_SHARD,  // 紫 → 紫水晶碎片
        Blocks.FIRE_CORAL_BLOCK to Items.REDSTONE,          // 红 → 红石
        Blocks.HORN_CORAL_BLOCK to Items.GOLD_NUGGET,       // 黄 → 金粒
    )

    /** 手持刷子右键珊瑚块时调用; 返回 PASS 表示"这不是我管的事", 交还原版 */
    fun handleBrush(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos): ActionResult {
        if (!PerkSession.isEnabled()) return ActionResult.PASS

        val state = PerkStateManager.get(player.uuid) ?: return ActionResult.PASS
        if (!state.hasPerk(PERK_ID)) return ActionResult.PASS

        val stack = player.getStackInHand(Hand.MAIN_HAND)
        if (!stack.isOf(Items.BRUSH)) return ActionResult.PASS

        val mineral = CORAL_MINERALS[world.getBlockState(pos).block] ?: return ActionResult.PASS

        // 珊瑚块被"刷掉" —— 不掉珊瑚本体, 刷子只负责把里面的东西取出来
        world.removeBlock(pos, false)
        world.playSound(
            null, pos,
            SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC,
            SoundCategory.BLOCKS,
            1.0f, 1.0f,
        )

        if (player.random.nextFloat() < MINERAL_CHANCE) {
            Block.dropStack(world, pos, ItemStack(mineral))
        }

        // 和原版刷子一样消耗 1 点耐久 (归零时自动损坏并播放音效)
        stack.damage(1, player, EquipmentSlot.MAINHAND)

        return ActionResult.SUCCESS
    }
}

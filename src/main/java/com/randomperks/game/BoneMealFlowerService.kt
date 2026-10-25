package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.BoneMealItem
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

/**
 * "骨粉撒花" —— 对**草方块**使用骨粉时, 在周围额外长出 2 朵随机花 (必定触发)。
 *
 * 只认草方块 (对作物 / 花 / 苔藓等使用骨粉不触发); 附近找不到可放置的位置时,
 * 剩余的花会直接以掉落物形式掉在骨粉使用的位置。
 */
object BoneMealFlowerService {

    const val PERK_ID = "harvest_bone_meal_flowers"

    /** 额外长出的花的数量 */
    private const val FLOWER_COUNT = 2

    /** 放置尝试次数 (周围找不到位置时转为掉落物) */
    private const val MAX_ATTEMPTS = 24

    /** 可开出的花 (原版小型花; 不含两格高的花与凋灵玫瑰) */
    private val FLOWERS = listOf(
        Blocks.DANDELION,
        Blocks.POPPY,
        Blocks.BLUE_ORCHID,
        Blocks.ALLIUM,
        Blocks.AZURE_BLUET,
        Blocks.RED_TULIP,
        Blocks.ORANGE_TULIP,
        Blocks.WHITE_TULIP,
        Blocks.PINK_TULIP,
        Blocks.OXEYE_DAISY,
        Blocks.CORNFLOWER,
        Blocks.LILY_OF_THE_VALLEY,
    )

    fun handleUse(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos) {
        if (!PerkSession.isEnabled()) return
        if (!PerkBoostQuery.hasBoneMealFlowers(player)) return
        if (!world.getBlockState(pos).isOf(Blocks.GRASS_BLOCK)) return

        val random = world.random
        var placed = 0
        var attempts = 0

        while (placed < FLOWER_COUNT && attempts < MAX_ATTEMPTS) {
            attempts++

            val target = pos.add(
                random.nextInt(5) - 2,
                random.nextInt(3) - 1,
                random.nextInt(5) - 2,
            )
            if (target == pos || !world.isAir(target)) continue

            val flower = FLOWERS[random.nextInt(FLOWERS.size)].defaultState
            if (!flower.canPlaceAt(world, target)) continue

            world.setBlockState(target, flower, Block.NOTIFY_ALL)
            BoneMealItem.createParticles(world, target, 5)
            placed++
        }

        // 周围放不下 (或草方块上已经长满) → 直接掉花, 保证收益不丢
        repeat(FLOWER_COUNT - placed) {
            Block.dropStack(world, pos, ItemStack(FLOWERS[random.nextInt(FLOWERS.size)]))
        }
    }
}

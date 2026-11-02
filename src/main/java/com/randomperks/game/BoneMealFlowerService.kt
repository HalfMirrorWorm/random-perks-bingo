package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.BoneMealItem
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos

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

    fun handleUse(player: ServerPlayer, world: ServerLevel, pos: BlockPos) {
        if (!PerkSession.isEnabled()) return
        if (!PerkBoostQuery.hasBoneMealFlowers(player)) return
        if (!world.getBlockState(pos).`is`(Blocks.GRASS_BLOCK)) return

        val random = world.random
        var placed = 0
        var attempts = 0

        while (placed < FLOWER_COUNT && attempts < MAX_ATTEMPTS) {
            attempts++

            val target = pos.offset(
                random.nextInt(5) - 2,
                random.nextInt(3) - 1,
                random.nextInt(5) - 2,
            )
            if (target == pos || !world.isEmptyBlock(target)) continue

            val flower = FLOWERS[random.nextInt(FLOWERS.size)].defaultBlockState()
            if (!flower.canSurvive(world, target)) continue

            world.setBlock(target, flower, Block.UPDATE_ALL)
            BoneMealItem.addGrowthParticles(world, target, 5)
            placed++
        }

        // 周围放不下 (或草方块上已经长满) → 直接掉花, 保证收益不丢
        repeat(FLOWER_COUNT - placed) {
            Block.popResource(world, pos, ItemStack(FLOWERS[random.nextInt(FLOWERS.size)]))
        }
    }
}

package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 「传送时钟」词条的右键逻辑:
 *
 * <p>手持时钟右键 → **消耗 1 个时钟**，随机传送到当前维度 2000 格以内、
 * 该 x/z 列最高可站立方块上方的安全位置（避开岩浆/火/仙人掌/细雪/水等）。
 */
object WaypointService {

    private const val PERK_ID = "artisan_waypoint_clock"

    /** 随机传送半径（格） */
    private const val MAX_DISTANCE = 2000.0

    /** 最多尝试多少个随机列 */
    private const val MAX_ATTEMPTS = 32

    /** 两次使用之间的冷却: 5 分钟 */
    private const val COOLDOWN_KEY = "waypoint_clock_cooldown"
    private const val COOLDOWN_TICKS = 5 * 60 * 20L

    /** 即使有碰撞也不能站上去的方块 */
    private val UNSAFE_BLOCKS = setOf(
        Blocks.LAVA,
        Blocks.FIRE,
        Blocks.SOUL_FIRE,
        Blocks.MAGMA_BLOCK,
        Blocks.CACTUS,
        Blocks.POWDER_SNOW,
        Blocks.SWEET_BERRY_BUSH,
        Blocks.CAMPFIRE,
        Blocks.SOUL_CAMPFIRE,
        Blocks.WITHER_ROSE,
        Blocks.COBWEB,
    )

    fun register() {
        UseItemCallback.EVENT.register { player, _, hand ->
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            if (!player.mainHandItem.`is`(Items.CLOCK)) return@register InteractionResult.PASS

            if (handleUse(player)) InteractionResult.SUCCESS else InteractionResult.PASS
        }
    }

    private fun handleUse(player: ServerPlayer): Boolean {
        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(PERK_ID)) return false

        val level = player.level()
        if (level.dimension() != Level.OVERWORLD) {
            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.waypoint_wrong_dimension"),
                true,
            )
            return true
        }

        val now = level.getServer().tickCount.toLong()
        val next = state.cooldowns[COOLDOWN_KEY] ?: 0L
        if (now < next) {
            val remainSeconds = ((next - now) / 20).toInt()
            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.waypoint_cooldown", remainSeconds),
                true,
            )
            return true
        }

        val spot = findSafeSpot(player, level)
        if (spot == null) {
            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.waypoint_no_safe_spot"),
                true,
            )
            return true
        }

        player.teleportTo(
            level,
            spot.x + 0.5,
            spot.y.toDouble(),
            spot.z + 0.5,
            emptySet(),
            player.yRot,
            player.xRot,
            false,
        )
        consumeClock(player)
        state.cooldowns[COOLDOWN_KEY] = now + COOLDOWN_TICKS
        player.sendSystemMessage(
            Component.translatable("random-perks-bingo.message.waypoint_teleport"),
            true,
        )
        return true
    }

    /** 在当前维度随机找一列，返回该列最高可站立方块上方的安全落脚点 */
    private fun findSafeSpot(player: ServerPlayer, level: ServerLevel): BlockPos? {
        val origin = player.blockPosition()
        val random = player.random

        repeat(MAX_ATTEMPTS) {
            // 圆内均匀取点: 半径用 sqrt 分布, 避免都挤在中心
            val angle = random.nextDouble() * PI * 2.0
            val distance = sqrt(random.nextDouble()) * MAX_DISTANCE
            val x = origin.x + (cos(angle) * distance).toInt()
            val z = origin.z + (sin(angle) * distance).toInt()

            if (!level.worldBorder.isWithinBounds(x.toDouble(), z.toDouble())) return@repeat

            // 确保该 x/z 所在区块已生成，否则高度图不可靠
            level.getChunk(x shr 4, z shr 4)

            // 忽略树叶/花草，取最高可站立方块；脚正好落在它上方的空气里
            val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
            val feet = BlockPos(x, y, z)
            if (isSafe(level, feet)) return feet
        }
        return null
    }

    private fun isSafe(level: ServerLevel, feet: BlockPos): Boolean {
        val head = feet.above()
        if (!level.isInsideBuildHeight(feet) || !level.isInsideBuildHeight(head)) return false

        val belowPos = feet.below()
        val below = level.getBlockState(belowPos)
        val atFeet = level.getBlockState(feet)
        val atHead = level.getBlockState(head)

        // 下面必须是实心、可站立的方块
        if (below.isAir) return false
        if (below.getCollisionShape(level, belowPos).isEmpty) return false
        if (below.block in UNSAFE_BLOCKS) return false

        // 脚和头的位置必须是空的、没有流体、不是危险方块
        if (!atFeet.getCollisionShape(level, feet).isEmpty) return false
        if (!atHead.getCollisionShape(level, head).isEmpty) return false
        if (!level.getFluidState(feet).isEmpty) return false
        if (!level.getFluidState(head).isEmpty) return false
        if (atFeet.block in UNSAFE_BLOCKS || atHead.block in UNSAFE_BLOCKS) return false

        return true
    }

    /** 消耗主手 1 个时钟 */
    private fun consumeClock(player: ServerPlayer) {
        val held = player.mainHandItem
        if (!held.`is`(Items.CLOCK)) return

        if (held.count > 1) {
            held.shrink(1)
        } else {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        }
        player.inventoryMenu.sendAllDataToRemote()
    }
}

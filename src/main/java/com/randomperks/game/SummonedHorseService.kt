package com.randomperks.game

import com.randomperks.perk.content.HarvestPerks
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.animal.equine.Horse
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.level.pathfinder.PathfindingContext
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator
import kotlin.math.abs

/**
 * "马来"召唤出来的马会像驯服的狼一样跟着主人:
 * 离得太远、或主人换了维度时, 自动传送到主人身边。
 *
 * 只认带 [HarvestPerks.SummonHorse.TAG] 命令标签的马 —— 其他马不受影响;
 * 有乘客时不动 (避免把骑手一起拽走, 玩家自己骑的马距离本来就为 0)。
 *
 * 传送落点按原版 [net.minecraft.world.entity.TamableAnimal] (狗/猫) 的规则:
 * 在主人周围水平 ±3 格、垂直 ±1 格内最多试 10 次, 只接受可行走 (WALKABLE)、
 * 碰撞箱放得下、脚下不是树叶的位置; 找不到就不传送。
 */
object SummonedHorseService {

    /** 检查间隔: 每 10 tick (0.5 秒) 一次 */
    private const val INTERVAL_TICKS = 10

    /** 与原版驯服动物一致: 距离达到 12 格 (144 的平方) 才传送 */
    private const val TELEPORT_DISTANCE_SQ = 144.0

    /** 原版 TamableAnimal.teleportToAroundBlockPos 的搜索参数 */
    private const val TELEPORT_ATTEMPTS = 10
    private const val HORIZONTAL_RANGE = 3
    private const val MIN_HORIZONTAL_DISTANCE = 2
    private const val VERTICAL_RANGE = 1

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.tickCount % INTERVAL_TICKS != 0) return@register

            // 先收集再传送: 跨维度传送会改动世界实体表, 不能在遍历时直接改
            val pending = mutableListOf<Horse>()
            for (world in server.allLevels) {
                for (entity in world.getAllEntities()) {
                    if (entity !is Horse) continue
                    if (!entity.isAlive) continue
                    if (!entity.entityTags().contains(HarvestPerks.SummonHorse.TAG)) continue
                    if (entity.isVehicle()) continue
                    pending += entity
                }
            }

            for (horse in pending) {
                val owner = horse.owner as? ServerPlayer ?: continue
                if (!owner.isAlive || owner.isSpectator) continue
                // 原版驯服动物被拴住时不传送
                if (horse.mayBeLeashed()) continue

                val sameWorld = horse.level() === owner.level()
                if (sameWorld && horse.distanceToSqr(owner) < TELEPORT_DISTANCE_SQ) continue

                teleportNextTo(horse, owner)
            }
        }
    }

    /**
     * 按原版 [net.minecraft.world.entity.TamableAnimal] 的落点规则传送到主人附近。
     */
    private fun teleportNextTo(horse: Horse, owner: ServerPlayer) {
        val level = owner.level()
        val center = owner.blockPosition()
        val context = pathfindingContext(horse, level) ?: return

        repeat(TELEPORT_ATTEMPTS) {
            val dx = horse.random.nextIntBetweenInclusive(-HORIZONTAL_RANGE, HORIZONTAL_RANGE)
            val dz = horse.random.nextIntBetweenInclusive(-HORIZONTAL_RANGE, HORIZONTAL_RANGE)
            // 原版要求水平方向至少拉开 2 格, 避免和马主叠在同一个方块里
            if (abs(dx) < MIN_HORIZONTAL_DISTANCE && abs(dz) < MIN_HORIZONTAL_DISTANCE) return@repeat

            val dy = horse.random.nextIntBetweenInclusive(-VERTICAL_RANGE, VERTICAL_RANGE)
            val pos = center.offset(dx, dy, dz)
            if (!canTeleportTo(horse, level, context, pos)) return@repeat

            horse.navigation.stop()
            horse.teleportTo(
                level,
                pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5,
                emptySet(), horse.yRot, horse.xRot, true,
            )
            return
        }
    }

    /**
     * 取目标维度的 [PathfindingContext]。
     *
     * 同维度直接用马; 跨维度时马还在旧维度, `PathfindingContext` 会拿旧维度的
     * `PathTypeCache`, 因此造一只不进入世界的临时马, 只为拿到目标维度的 context。
     */
    private fun pathfindingContext(horse: Horse, level: ServerLevel): PathfindingContext? {
        if (horse.level() === level) return PathfindingContext(level, horse)
        val probe = EntityTypes.HORSE.create(level, EntitySpawnReason.COMMAND) ?: return null
        return PathfindingContext(level, probe)
    }

    /** 与 TamableAnimal#canTeleportTo 相同的检查 */
    private fun canTeleportTo(
        horse: Horse,
        level: ServerLevel,
        context: PathfindingContext,
        pos: BlockPos,
    ): Boolean {
        if (WalkNodeEvaluator.getPathTypeStatic(context, pos.mutable()) != PathType.WALKABLE) return false
        if (level.getBlockState(pos.below()).block is LeavesBlock) return false
        val offset = pos.subtract(horse.blockPosition())
        return level.noCollision(horse, horse.boundingBox.move(offset))
    }
}

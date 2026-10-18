package com.randomperks.game

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnReason
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import java.util.UUID

/**
 * "!?区区!?" 的蠹虫骚扰: 对被选中的玩家持续 [DURATION_TICKS], 每 [INTERVAL_TICKS] 在他附近刷一只蠹虫。
 *
 * 这是一个**跨玩家**的持续效果 —— 施法者只负责点名, 后续刷新与计时都在服务端独立推进,
 * 因此不能挂在施法者自己的 onTick 上。
 */
object VerminSwarmService {

    /** 持续时间: 2 分钟 */
    const val DURATION_TICKS = 2 * 60 * 20

    /** 刷新间隔: 6 秒 */
    private const val INTERVAL_TICKS = 6 * 20

    /** 刷新半径 (格) */
    private const val RADIUS = 5

    private class Swarm(val endTick: Long, var nextSpawnTick: Long)

    private val active = mutableMapOf<UUID, Swarm>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (active.isEmpty()) return@register
            val now = server.ticks.toLong()

            for ((uuid, swarm) in active.toList()) {
                if (now >= swarm.endTick) {
                    active.remove(uuid)
                    continue
                }
                // 玩家掉线就停掉 (重新上线不恢复, 避免"离线期间攒一堆")
                val player = server.playerManager.getPlayer(uuid) ?: run {
                    active.remove(uuid)
                    null
                } ?: continue

                if (now < swarm.nextSpawnTick) continue
                swarm.nextSpawnTick = now + INTERVAL_TICKS
                spawnVermin(player)
            }
        }
    }

    /** 让 [target] 被蠹虫缠上 (重复施加会重置持续时间与计时) */
    fun start(target: ServerPlayerEntity) {
        val server = target.server ?: return
        val now = server.ticks.toLong()
        active[target.uuid] = Swarm(now + DURATION_TICKS, now)
    }

    /** 对局结束 / 重置时清理 */
    fun clear() = active.clear()

    private fun spawnVermin(player: ServerPlayerEntity) {
        val world = player.world as? ServerWorld ?: return
        val pos = findSpawnPos(world, player.blockPos) ?: return

        val vermin = EntityType.SILVERFISH.create(world, SpawnReason.EVENT) ?: return
        vermin.refreshPositionAndAngles(
            pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5,
            world.random.nextFloat() * 360f, 0f,
        )
        world.spawnEntity(vermin)
    }

    /**
     * 在玩家 [RADIUS] 格内找一块"自身是空气、脚下是实心"的落脚点。
     *
     * 不用 getTopPosition —— 那样在洞穴/矿洞里会一路找到地表, 蠹虫会刷到头顶几百格之外。
     * 改为以玩家所在高度为基准上下各扫几格, 地面与洞穴都能覆盖。
     */
    private fun findSpawnPos(world: ServerWorld, center: BlockPos): BlockPos? {
        val random = world.random
        for (attempt in 0 until 24) {
            val dx = random.nextInt(RADIUS * 2 + 1) - RADIUS
            val dz = random.nextInt(RADIUS * 2 + 1) - RADIUS

            for (dy in 2 downTo -2) {
                val pos = center.add(dx, dy, dz)
                if (!world.getBlockState(pos).isAir) continue
                val below = pos.down()
                if (world.getBlockState(below).isSolidBlock(world, below)) return pos
            }
        }
        return null
    }
}

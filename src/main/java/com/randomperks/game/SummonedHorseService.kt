package com.randomperks.game

import com.randomperks.perk.content.HarvestPerks
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.passive.HorseEntity
import net.minecraft.server.network.ServerPlayerEntity

/**
 * "马来"召唤出来的马会像驯服的狼一样跟着主人:
 * 离得太远、或主人换了维度时, 自动传送到主人身边。
 *
 * 只认带 [HarvestPerks.SummonHorse.TAG] 命令标签的马 —— 其他马不受影响;
 * 有乘客时不动 (避免把骑手一起拽走, 玩家自己骑的马距离本来就为 0)。
 */
object SummonedHorseService {

    /** 检查间隔: 每 10 tick (0.5 秒) 一次 */
    private const val INTERVAL_TICKS = 10

    /** 同维度内超过这个距离 (格) 就传送 */
    private const val TELEPORT_DISTANCE_SQ = 24.0 * 24.0

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.ticks % INTERVAL_TICKS != 0) return@register

            // 先收集再传送: 跨维度传送会改动世界实体表, 不能在遍历时直接改
            val pending = mutableListOf<HorseEntity>()
            for (world in server.worlds) {
                for (entity in world.iterateEntities()) {
                    if (entity !is HorseEntity) continue
                    if (!entity.isAlive) continue
                    if (!entity.commandTags.contains(HarvestPerks.SummonHorse.TAG)) continue
                    if (entity.hasPassengers()) continue
                    pending += entity
                }
            }

            for (horse in pending) {
                val owner = horse.owner as? ServerPlayerEntity ?: continue
                if (!owner.isAlive) continue

                val sameWorld = horse.world === owner.world
                if (sameWorld && horse.squaredDistanceTo(owner) <= TELEPORT_DISTANCE_SQ) continue

                // 落在主人身边 1 格内的随机位置, 避免和主人卡在同一个方块
                val dx = (owner.random.nextDouble() - 0.5) * 2.0
                val dz = (owner.random.nextDouble() - 0.5) * 2.0
                horse.teleport(
                    owner.world,
                    owner.x + dx, owner.y, owner.z + dz,
                    emptySet(), horse.yaw, horse.pitch, true,
                )
            }
        }
    }
}

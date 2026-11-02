package com.randomperks.game

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.world.entity.item.ItemEntity

/**
 * "磁力升级" (蓝 · 秘藏) —— 把拾取范围扩大到 5 格。
 *
 * 原版拾取是物品自己每 tick 检测身边 0.5 格内有没有玩家; 这里反过来由服务端定期扫描
 * 玩家周围 5 格内的掉落物, 直接调 [ItemEntity.onPlayerCollision] —— 走的是同一条拾取路径,
 * 所以 pickupDelay、背包合并、拾取音效等行为全都和原版一致。
 */
object MagnetService {

    /** 拾取半径 (格) */
    private const val RANGE = 5.0

    /** 扫描间隔: 5 tick = 0.25 秒 (每 tick 扫一次没必要, 也省点开销) */
    private const val INTERVAL_TICKS = 5

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.tickCount % INTERVAL_TICKS != 0) return@register

            for (player in server.playerList.players) {
                if (!PerkBoostQuery.hasMagnetUpgrade(player)) continue

                val items = player.level().getEntitiesOfClass(
                    ItemEntity::class.java,
                    player.boundingBox.inflate(RANGE),
                ) { it.isAlive && !it.hasPickUpDelay() && it.distanceToSqr(player) <= RANGE * RANGE }

                for (item in items) {
                    // onPlayerCollision 内部会再检查 pickupDelay / 背包是否有空间
                    item.playerTouch(player)
                }
            }
        }
    }
}

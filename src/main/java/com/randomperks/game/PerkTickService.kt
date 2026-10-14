package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.perk.PerkContext
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

/**
 * 驱动词条的周期回调 (onTick) 与重生回调 (onRespawn)。
 */
object PerkTickService {

    fun register() {
        // 每 20 tick (1 秒) 驱动一次 onTick
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (server.ticks % 20 != 0) return@register

            for (state in PerkStateManager.all()) {
                if (state.ownedPerks.isEmpty()) continue
                val player = server.playerManager.getPlayer(state.uuid) ?: continue
                val ctx = PerkContext(player, state)
                for (perk in state.ownedPerks.toList()) {
                    runCatching { perk.onTick(ctx) }
                        .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} onTick failed", perk.id, it) }
                }
            }
        }

        // 死亡重生后重新应用效果 (属性修改器等在实体重建时丢失)
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            val state = PerkStateManager.get(newPlayer.uuid) ?: return@register
            if (state.ownedPerks.isEmpty()) return@register

            val ctx = PerkContext(newPlayer, state)
            for (perk in state.ownedPerks.toList()) {
                runCatching { perk.onRespawn(ctx) }
                    .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} onRespawn failed", perk.id, it) }
            }
        }
    }
}

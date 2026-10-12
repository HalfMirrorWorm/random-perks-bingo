package com.roguebingo.game

import com.roguebingo.RogueBingoMod
import com.roguebingo.perk.PerkContext
import com.roguebingo.perk.PerkDefinition
import com.roguebingo.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.server.network.ServerPlayerEntity

/**
 * 把世界事件 (击杀 / 破坏方块) 分发给玩家已获得的词条。
 */
object PerkWorldEventService {

    fun register() {
        // 击杀生物
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, damageSource ->
            val killer = damageSource.attacker as? ServerPlayerEntity ?: return@register
            dispatch(killer) { ctx, perk -> perk.onKill(ctx, entity) }
        }

        // 破坏方块
        PlayerBlockBreakEvents.AFTER.register { _, player, pos, state, _ ->
            if (player !is ServerPlayerEntity) return@register
            dispatch(player) { ctx, perk -> perk.onBlockBreak(ctx, pos, state) }
        }
    }

    private inline fun dispatch(
        player: ServerPlayerEntity,
        action: (PerkContext, PerkDefinition) -> Unit,
    ) {
        val state = PerkStateManager.get(player.uuid) ?: return
        if (state.ownedPerks.isEmpty()) return

        val ctx = PerkContext(player, state)
        for (perk in state.ownedPerks.toList()) {
            runCatching { action(ctx, perk) }
                .onFailure { RogueBingoMod.logger.error("[RogueBingo] perk {} world-event failed", perk.id, it) }
        }
    }
}

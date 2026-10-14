package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkDefinition
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.server.network.ServerPlayerEntity

/**
 * 把世界事件 (击杀 / 破坏方块 / 装备变化) 分发给玩家已获得的词条。
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

    /** 装备变化 (由 EquipmentChangeMixin 注入原版 equipStack 调用) —— 即时重算装备相关词条 */
    fun dispatchEquipmentChange(player: ServerPlayerEntity) {
        dispatch(player) { ctx, perk -> perk.onEquipmentChange(ctx) }
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
                .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} world-event failed", perk.id, it) }
        }
    }
}

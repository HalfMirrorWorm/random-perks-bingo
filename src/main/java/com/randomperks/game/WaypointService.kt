package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand

/**
 * "传送时钟"词条的右键逻辑: 传送到**上次死亡地点** (5 分钟冷却)。
 *
 * 死亡地点在 ServerLivingEntityEvents.AFTER_DEATH 里自动记录 —— 只记录持有词条的玩家;
 * 之后再死一次会覆盖旧记录。跨维度也可以传送 (否则死在下界/末地就废了)。
 */
object WaypointService {

    private const val PERK_ID = "artisan_waypoint_clock"
    private const val DEATH_POINT_KEY = "waypoint_death_point"
    private const val COOLDOWN_KEY = "waypoint_clock_cooldown"
    private const val COOLDOWN_TICKS = 5 * 60 * 20L

    fun register() {
        // 记录"上次死亡地点" (只记录持有词条的玩家)
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (entity !is ServerPlayer) return@register
            val state = PerkStateManager.get(entity.uuid) ?: return@register
            if (!state.hasPerk(PERK_ID)) return@register
            val worldId = entity.level().dimension().identifier().toString()
            state.memory[DEATH_POINT_KEY] = "$worldId|${entity.x}|${entity.y}|${entity.z}"
        }

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

        val server = player.level().getServer()
        val now = server.tickCount.toLong()

        val point = state.memory[DEATH_POINT_KEY]
        if (point == null) {
            player.sendSystemMessage(Component.translatable("random-perks-bingo.message.waypoint_no_death"), true)
            return true
        }

        // 冷却检查
        val next = state.cooldowns[COOLDOWN_KEY] ?: 0L
        if (now < next) {
            val remainSeconds = ((next - now) / 20).toInt()
            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.waypoint_cooldown", remainSeconds),
                true,
            )
            return true
        }

        // 解析死亡点
        val parts = point.split("|")
        if (parts.size != 4) {
            state.memory.remove(DEATH_POINT_KEY)
            return true
        }
        val worldId = parts[0]
        val x = parts[1].toDoubleOrNull() ?: return false
        val y = parts[2].toDoubleOrNull() ?: return false
        val z = parts[3].toDoubleOrNull() ?: return false

        val targetWorld = server.allLevels.firstOrNull { it.dimension().identifier().toString() == worldId }
        if (targetWorld == null) {
            player.sendSystemMessage(Component.translatable("random-perks-bingo.message.waypoint_world_gone"), true)
            return true
        }

        // 传送 (保留视角; 跨维度同样允许)
        player.teleportTo(targetWorld, x, y, z, emptySet(), player.yRot, player.xRot, false)
        state.cooldowns[COOLDOWN_KEY] = now + COOLDOWN_TICKS
        player.sendSystemMessage(Component.translatable("random-perks-bingo.message.waypoint_teleport"), true)
        return true
    }
}

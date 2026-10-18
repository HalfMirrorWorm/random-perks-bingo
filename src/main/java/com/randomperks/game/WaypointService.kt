package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

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
            if (entity !is ServerPlayerEntity) return@register
            val state = PerkStateManager.get(entity.uuid) ?: return@register
            if (!state.hasPerk(PERK_ID)) return@register
            val worldId = entity.world.registryKey.value.toString()
            state.memory[DEATH_POINT_KEY] = "$worldId|${entity.x}|${entity.y}|${entity.z}"
        }

        UseItemCallback.EVENT.register { player, _, hand ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (!player.mainHandStack.isOf(Items.CLOCK)) return@register ActionResult.PASS

            if (handleUse(player)) ActionResult.SUCCESS else ActionResult.PASS
        }
    }

    private fun handleUse(player: ServerPlayerEntity): Boolean {
        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(PERK_ID)) return false

        val server = player.server ?: return false
        val now = server.ticks.toLong()

        val point = state.memory[DEATH_POINT_KEY]
        if (point == null) {
            player.sendMessage(Text.translatable("random-perks-bingo.message.waypoint_no_death"), true)
            return true
        }

        // 冷却检查
        val next = state.cooldowns[COOLDOWN_KEY] ?: 0L
        if (now < next) {
            val remainSeconds = ((next - now) / 20).toInt()
            player.sendMessage(
                Text.translatable("random-perks-bingo.message.waypoint_cooldown", remainSeconds),
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

        val targetWorld = server.worlds.firstOrNull { it.registryKey.value.toString() == worldId }
        if (targetWorld == null) {
            player.sendMessage(Text.translatable("random-perks-bingo.message.waypoint_world_gone"), true)
            return true
        }

        // 传送 (保留视角; 跨维度同样允许)
        player.teleport(targetWorld, x, y, z, emptySet(), player.yaw, player.pitch, false)
        state.cooldowns[COOLDOWN_KEY] = now + COOLDOWN_TICKS
        player.sendMessage(Text.translatable("random-perks-bingo.message.waypoint_teleport"), true)
        return true
    }
}

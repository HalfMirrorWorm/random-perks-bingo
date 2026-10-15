package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

/**
 * "传送时钟"词条的右键逻辑:
 * - 首次右键: 设置传送点 (记录维度与坐标)
 * - 之后右键: 传送回传送点 (10 分钟冷却)
 */
object WaypointService {

    private const val PERK_ID = "artisan_waypoint_clock"
    private const val POINT_KEY = "waypoint_clock_point"
    private const val COOLDOWN_KEY = "waypoint_clock_cooldown"
    private const val COOLDOWN_TICKS = 10 * 60 * 20L

    fun register() {
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
        val point = state.memory[POINT_KEY]

        // 尚未设置 → 设置传送点 (设置后不可重设)
        if (point == null) {
            val worldId = player.world.registryKey.value.toString()
            state.memory[POINT_KEY] = "$worldId|${player.x}|${player.y}|${player.z}"
            player.sendMessage(Text.translatable("random-perks-bingo.message.waypoint_set"), true)
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

        // 解析传送点
        val parts = point.split("|")
        if (parts.size != 4) {
            state.memory.remove(POINT_KEY)
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

        // 距离限制: 仅允许 2000 格以内传送; 不在同一世界视为超出
        if (targetWorld !== player.world) {
            player.sendMessage(Text.translatable("random-perks-bingo.message.waypoint_cross_world"), true)
            return true
        }
        val distance = player.pos.distanceTo(net.minecraft.util.math.Vec3d(x, y, z))
        if (distance > 2000.0) {
            player.sendMessage(
                Text.translatable("random-perks-bingo.message.waypoint_too_far", distance.toInt()),
                true,
            )
            return true
        }

        // 传送 (保留视角)
        player.teleport(targetWorld, x, y, z, emptySet(), player.yaw, player.pitch, false)
        state.cooldowns[COOLDOWN_KEY] = now + COOLDOWN_TICKS
        player.sendMessage(Text.translatable("random-perks-bingo.message.waypoint_teleport"), true)
        return true
    }
}

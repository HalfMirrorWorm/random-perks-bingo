package com.randomperks.game

import com.randomperks.RandomPerksMod
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.world.GameMode
import java.util.UUID

/**
 * "神话·创造闪光"的服务端逻辑: 给玩家 1 秒创造模式, 然后恢复原游戏模式。
 */
object CreativeFlashService {

    private const val FLASH_TICKS = 20L

    private data class Flash(
        val originalMode: GameMode,
        val wasFlying: Boolean,
        val endTick: Long,
    )

    private val active = mutableMapOf<UUID, Flash>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (active.isEmpty()) return@register

            val now = server.ticks.toLong()
            val expired = active.entries.filter { it.value.endTick <= now }.map { it.key }
            for (uuid in expired) {
                val flash = active.remove(uuid) ?: continue
                val player = server.playerManager.getPlayer(uuid) ?: continue
                restore(player, flash)
            }
        }

        // ★ 离线兜底: 闪光期间退出/被踢时**立刻**恢复原模式 —— 否则那 1 秒内断线会以创造模式存盘
        // (游戏模式是玩家存档的一部分), 下次上线仍是创造, 等于白送作弊权限。
        // Fabric 的 LEAVE 注入在 PlayerManager.remove 的 HEAD, 玩家对象此时完全有效,
        // 且早于 savePlayerData, 所以恢复结果会被正确写回存档。
        ServerPlayerEvents.LEAVE.register { player ->
            val flash = active.remove(player.uuid) ?: return@register
            restore(player, flash)
        }
    }

    /** 恢复闪光前的状态 (游戏模式 + 飞行开关) */
    private fun restore(player: ServerPlayerEntity, flash: Flash) {
        player.changeGameMode(flash.originalMode)
        player.abilities.flying = flash.wasFlying
        player.sendAbilitiesUpdate()
    }

    /** 给玩家 1 秒创造模式 (神话词条效果) */
    fun flash(player: ServerPlayerEntity) {
        val server = player.server ?: return
        val original = player.interactionManager.gameMode
        if (original == GameMode.CREATIVE) return

        val wasFlying = player.abilities.flying
        active[player.uuid] = Flash(original, wasFlying, server.ticks + FLASH_TICKS)

        player.changeGameMode(GameMode.CREATIVE)
        player.sendMessage(
            Text.translatable("random-perks-bingo.message.creative_flash"),
            true,
        )
        RandomPerksMod.logger.info("[RandomPerks] creative flash for {} (1s)", player.name.string)
    }
}

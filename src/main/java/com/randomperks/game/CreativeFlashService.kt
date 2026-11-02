package com.randomperks.game

import com.randomperks.RandomPerksMod
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType
import java.util.UUID

/**
 * "神话·创造闪光"的服务端逻辑: 给玩家 1.5 秒创造模式, 然后恢复原游戏模式。
 */
object CreativeFlashService {

    private const val FLASH_TICKS = 30L

    private data class Flash(
        val originalMode: GameType,
        val wasFlying: Boolean,
        val endTick: Long,
    )

    private val active = mutableMapOf<UUID, Flash>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (active.isEmpty()) return@register

            val now = server.tickCount.toLong()
            val expired = active.entries.filter { it.value.endTick <= now }.map { it.key }
            for (uuid in expired) {
                val flash = active.remove(uuid) ?: continue
                val player = server.playerList.getPlayer(uuid) ?: continue
                restore(player, flash)
            }
        }

        // ★ 离线兜底: 闪光期间退出/被踢时**立刻**恢复原模式 —— 否则那 1.5 秒内断线会以创造模式存盘
        // (游戏模式是玩家存档的一部分), 下次上线仍是创造, 等于白送作弊权限。
        // Fabric 的 LEAVE 注入在 PlayerManager.remove 的 HEAD, 玩家对象此时完全有效,
        // 且早于 savePlayerData, 所以恢复结果会被正确写回存档。
        ServerPlayerEvents.LEAVE.register { player ->
            val flash = active.remove(player.uuid) ?: return@register
            restore(player, flash)
        }
    }

    /** 恢复闪光前的状态 (游戏模式 + 飞行开关) */
    private fun restore(player: ServerPlayer, flash: Flash) {
        player.setGameMode(flash.originalMode)
        player.abilities.flying = flash.wasFlying
        player.onUpdateAbilities()
    }

    /** 给玩家 1.5 秒创造模式 (神话词条效果) */
    fun flash(player: ServerPlayer) {
        val server = player.level().getServer()
        val original = player.gameMode.getGameModeForPlayer()
        if (original == GameType.CREATIVE) return

        val wasFlying = player.abilities.flying
        active[player.uuid] = Flash(original, wasFlying, server.tickCount + FLASH_TICKS)

        player.setGameMode(GameType.CREATIVE)
        player.sendSystemMessage(
            Component.translatable("random-perks-bingo.message.creative_flash"),
            true,
        )
        RandomPerksMod.logger.info("[RandomPerks] creative flash for {} (1.5s)", player.name.string)
    }
}

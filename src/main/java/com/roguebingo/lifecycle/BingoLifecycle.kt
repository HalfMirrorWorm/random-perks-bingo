package com.roguebingo.lifecycle

import com.roguebingo.RogueBingoMod
import com.roguebingo.network.PerkNetworking
import com.roguebingo.perk.PerkContext
import com.roguebingo.server.ServerHolder
import com.roguebingo.state.PerkStateManager
import me.jfenn.bingo.api.BingoEvents

/**
 * 对接 bingo 的游戏生命周期 (通过官方公开 API)。
 *
 * - 开局: 清空状态, 每人免费送 1 次选择机会
 * - 结束 / 重置: 清理词条效果与状态
 */
object BingoLifecycle {

    fun register() {
        BingoEvents.GAME_STARTED.register { onGameStarted() }
        BingoEvents.GAME_ENDED.register { onGameEnded() }
        BingoEvents.GAME_RESET.register { PerkStateManager.resetAll() }
        BingoEvents.CLOSE.register { PerkStateManager.resetAll() }
    }

    private fun onGameStarted() {
        PerkStateManager.resetAll()

        val server = ServerHolder.server ?: return
        for (player in server.playerManager.playerList) {
            val state = PerkStateManager.getOrCreate(player.uuid)
            state.pendingChoices = 1 // 开局免费送 1 次
            PerkNetworking.sendNotify(player, state.pendingChoices)
            player.sendMessage(
                net.minecraft.text.Text.translatable("rogue-bingo.message.game_started"),
                false,
            )
        }

        RogueBingoMod.logger.info(
            "[RogueBingo] game started → granted 1 free choice to {} player(s)",
            server.playerManager.playerList.size,
        )
    }

    private fun onGameEnded() {
        val server = ServerHolder.server
        if (server != null) {
            for (state in PerkStateManager.all()) {
                val player = server.playerManager.getPlayer(state.uuid) ?: continue
                val ctx = PerkContext(player, state)
                for (perk in state.ownedPerks.toList()) {
                    runCatching { perk.onRemove(ctx) }
                        .onFailure { RogueBingoMod.logger.error("[RogueBingo] perk {} onRemove failed", perk.id, it) }
                }
            }
        }
        PerkStateManager.resetAll()
        RogueBingoMod.logger.info("[RogueBingo] game ended → perk state cleared")
    }
}

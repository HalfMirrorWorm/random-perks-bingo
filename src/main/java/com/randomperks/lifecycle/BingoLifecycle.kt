package com.randomperks.lifecycle

import com.randomperks.RandomPerksMod
import com.randomperks.config.PerkConfig
import com.randomperks.game.PerkGrantService
import com.randomperks.perk.PerkContext
import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import me.jfenn.bingo.api.BingoEvents

/**
 * 对接 bingo 的游戏生命周期 (通过官方公开 API)。
 *
 * - 开局: 清空状态, 每人免费送 1 次选择机会 (词条系统关闭时跳过)
 * - 结束 / 重置: 清理词条效果与状态
 */
object BingoLifecycle {

    fun register() {
        BingoEvents.GAME_STARTED.register { onGameStarted() }
        BingoEvents.GAME_ENDED.register { clearAllPerkState() }
        BingoEvents.GAME_RESET.register { PerkStateManager.resetAll() }
        BingoEvents.CLOSE.register { PerkStateManager.resetAll() }
    }

    private fun onGameStarted() {
        PerkStateManager.resetAll()
        if (!PerkConfig.enabled) return

        val server = ServerHolder.server ?: return
        val players = server.playerManager.playerList
        if (players.isEmpty()) return

        // 开局赠送: 每位玩家一次选择机会 (各自独立随机)
        val granted = PerkGrantService.grantOffer(players)
        if (granted) {
            for (player in players) {
                player.sendMessage(
                    net.minecraft.text.Text.translatable("random-perks-bingo.message.game_started"),
                    false,
                )
            }
        }

        RandomPerksMod.logger.info(
            "[RandomPerks] game started → granted 1 free choice to {} player(s)",
            players.size,
        )
    }

    /**
     * 清空所有玩家的词条状态 (逐个执行 onRemove 清理效果)。
     * 对局结束时 / 关闭词条系统时调用。
     */
    fun clearAllPerkState() {
        val server = ServerHolder.server
        if (server != null) {
            for (state in PerkStateManager.all()) {
                val player = server.playerManager.getPlayer(state.uuid) ?: continue
                val ctx = PerkContext(player, state)
                for (perk in state.ownedPerks.toList()) {
                    runCatching { perk.onRemove(ctx) }
                        .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} onRemove failed", perk.id, it) }
                }
            }
        }
        PerkStateManager.resetAll()
        RandomPerksMod.logger.info("[RandomPerks] perk state cleared")
    }
}

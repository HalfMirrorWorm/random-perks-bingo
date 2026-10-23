package com.randomperks.lifecycle

import com.randomperks.RandomPerksMod
import com.randomperks.config.PerkConfig
import com.randomperks.game.PerkGrantService
import com.randomperks.game.DragonTamerService
import com.randomperks.game.JockeyService
import com.randomperks.game.LayFlatService
import com.randomperks.game.PerkSession
import com.randomperks.game.SwapService
import com.randomperks.game.TemperingAnvilService
import com.randomperks.game.VerminSwarmService
import com.randomperks.hook.RandomPerksHooks
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
        BingoEvents.GAME_ENDED.register {
            clearAllPerkState()
            PerkSession.reset()
        }
        BingoEvents.GAME_RESET.register { resetAll() }
        BingoEvents.CLOSE.register { resetAll() }
    }

    /** 清空词条状态 + 一切与对局绑定的持续效果 */
    private fun resetAll() {
        PerkStateManager.resetAll()
        VerminSwarmService.clear()
        LayFlatService.clear()
        JockeyService.clear()
        DragonTamerService.clear()
        SwapService.clear()
        TemperingAnvilService.clear()
        RandomPerksHooks.resetLineCounts()
    }

    private fun onGameStarted() {
        resetAll()

        // 开关每局生效: 开局时从配置快照, 局中修改不影响本局
        PerkSession.beginRound()
        if (!PerkSession.isEnabled()) {
            RandomPerksMod.logger.info("[RandomPerks] perk system is disabled for this round — no choices granted")
            return
        }

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

        // 清空后的能力状态同步给客户端
        players.forEach { PerkGrantService.syncAbilities(it) }

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
                // 先从列表移除再逐个 onRemove —— 与命令/偷窃路径的约定一致:
                // onRemove 里的装备操作会同步触发"装备变化→词条回调"分发, 若此时词条仍在
                // 列表中, "风之翼"等词条会把自己的回收操作当成"装备被换下"而立即补装
                val perks = state.ownedPerks.toList()
                state.ownedPerks.clear()
                for (perk in perks) {
                    runCatching { perk.onRemove(ctx) }
                        .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} onRemove failed", perk.id, it) }
                }
            }
        }
        resetAll()
        RandomPerksMod.logger.info("[RandomPerks] perk state cleared")

        // 清空后同步能力状态 (客户端关闭矿透等)
        server?.playerManager?.playerList?.forEach { PerkGrantService.syncAbilities(it) }
    }
}

package com.randomperks.lifecycle

import com.randomperks.RandomPerksMod
import com.randomperks.config.PerkConfig
import com.randomperks.game.PerkGrantService
import com.randomperks.game.PerkOrphanCleanup
import com.randomperks.game.DragonTamerService
import com.randomperks.game.LayFlatService
import com.randomperks.game.PigMorphService
import com.randomperks.game.PerkSession
import com.randomperks.game.ResonanceService
import com.randomperks.game.RewindService
import com.randomperks.game.SharedFortuneService
import com.randomperks.game.SoulOutService
import com.randomperks.game.SwapService
import com.randomperks.game.VerminSwarmService
import com.randomperks.hook.RandomPerksHooks
import com.randomperks.perk.PerkContext
import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import me.jfenn.bingo.api.BingoEvents
import java.util.UUID

/**
 * 对接 bingo 的游戏生命周期 (通过官方公开 API)。
 *
 * - 开局: 清空状态, 每人免费送 1 次选择机会 (词条系统关闭时跳过)
 * - 结束: **不清理**词条 —— 玩家可能选择"继续游戏", 保留当前词条/快照
 * - 重置 / 关闭 / 新开局: 清理词条效果与状态
 */
object BingoLifecycle {

    /** 当前这一局的 id; 同一个 id 再次进入 PLAYING 视为"继续游戏", 不重新开局 */
    private var currentGameId: UUID? = null

    fun register() {
        BingoEvents.GAME_STARTED.register { event ->
            if (currentGameId == event.id) {
                // "继续游戏": 词条、特质、待选机会和逆转快照全部保留,
                // 不重置、不重新发开局选择; 只把客户端状态重新同步一次。
                ServerHolder.server?.playerList?.players?.forEach {
                    PerkGrantService.syncAbilities(it)
                    val pending = PerkStateManager.get(it.uuid)?.totalPending ?: 0
                    if (pending > 0) PerkGrantService.notifyChoices(it)
                }
                RandomPerksMod.logger.info("[RandomPerks] game resumed → kept existing perks")
                return@register
            }

            currentGameId = event.id
            onGameStarted()
        }
        BingoEvents.GAME_ENDED.register { event ->
            // 结束不等于真正结束这一局: 玩家可以从结算界面选择"继续游戏"。
            // 这里不做任何清理, 真正的清理在 GAME_RESET / CLOSE / 下一局开局。
            RandomPerksMod.logger.info("[RandomPerks] game ended ({}); keeping perks for keep-playing", event.id)
        }
        BingoEvents.GAME_RESET.register {
            currentGameId = null
            PerkSession.reset()
            RewindService.stopGame()
            resetAll()
        }
        BingoEvents.CLOSE.register {
            currentGameId = null
            PerkSession.reset()
            RewindService.stopGame()
            resetAll()
        }
    }

    /** 清空词条状态 + 一切与对局绑定的持续效果 */
    private fun resetAll() {
        // 必须在清空 memory 之前: 让还处于"灵魂出窍"状态的玩家结束灵魂相机
        SoulOutService.clear()

        // GAME_ENDED 之外的生命周期入口 (GAME_RESET / CLOSE / 直接开下一局) 也必须
        // 走一遍 onRemove, 否则词条自己的清理逻辑 (物品、召唤物、天气、服务状态等) 会被跳过
        runOnRemoveForOnlinePlayers()

        PerkStateManager.resetAll()
        VerminSwarmService.clear()
        LayFlatService.clear()
        DragonTamerService.clear()
        SwapService.clear()
        ResonanceService.clear()
        SharedFortuneService.clear()
        PigMorphService.clear()
        RandomPerksHooks.resetLineCounts()
        // 状态已经清空: 在线玩家身上可能还留着上一局的属性 / 无限效果 (离线玩家则由加入时的
        // PerkOrphanCleanup.onJoin 兜底)
        ServerHolder.server?.playerList?.players?.forEach {
            PerkOrphanCleanup.clean(it)
            // 客户端侧的矿透等能力也立即关掉, 不等到下一局开局再同步
            PerkGrantService.syncAbilities(it)
        }
    }

    /** 对在线玩家逐个执行 onRemove, 但不清空状态本身 (由调用方决定何时清空) */
    private fun runOnRemoveForOnlinePlayers() {
        val server = ServerHolder.server ?: return
        for (state in PerkStateManager.all()) {
            val player = server.playerList.getPlayer(state.uuid) ?: continue
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

    private fun onGameStarted() {
        resetAll()

        // 开关每局生效: 开局时从配置快照, 局中修改不影响本局
        PerkSession.beginRound()
        if (!PerkSession.isEnabled()) {
            RandomPerksMod.logger.info("[RandomPerks] perk system is disabled for this round — no choices granted")
            RewindService.startGame()
            return
        }

        val server = ServerHolder.server ?: return
        val players = server.playerList.players
        if (players.isEmpty()) {
            RewindService.startGame()
            return
        }

        // 开局: 先给一次"特质选择"机会; 选完特质后再补发开局的普通词条机会
        val granted = PerkGrantService.grantStartingTrait(players)
        if (granted) {
            for (player in players) {
                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.translatable("random-perks-bingo.message.game_started"),
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

        // 开始为「逆转时间」记录本局快照
        RewindService.startGame()
    }

    /**
     * 立即清空所有玩家的词条 (逆转时间恢复前 / 手动重置时使用)。
     * 不会重置 [PerkSession] 或当前局 id —— 那两条属于真正的对局生命周期。
     */
    fun clearAllPerkState() {
        resetAll()
        RandomPerksMod.logger.info("[RandomPerks] perk state cleared")
    }
}

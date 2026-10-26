package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.lifecycle.BingoLifecycle
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkRegistry
import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import me.jfenn.bingo.api.BingoEvents
import me.jfenn.bingo.api.IBingoApi
import me.jfenn.bingo.common.card.objective.BingoObjectiveCapture
import me.jfenn.bingo.common.state.BingoState
import me.jfenn.bingo.common.state.GameState
import me.jfenn.bingo.common.team.BingoTeamKey
import me.jfenn.bingo.common.team.TeamCompletedCard
import me.jfenn.bingo.common.team.TeamScore
import me.jfenn.bingo.common.team.TeamWinner
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * 「逆转时间」的数据层: 每 30 秒记录一份**进度快照**, 需要时把本局进度回退。
 *
 * <p>性能设计: 快照里**只拷贝会变化的进度字段**, 不做 JSON 序列化、不反射整棵对象树,
 * 也不复制棋盘定义/选项/显示数据:
 * <ul>
 *   <li>全局: 游戏状态、计时相关的 4 个字段</li>
 *   <li>每支队伍: 两个分数、胜者、已完成卡片列表、当前卡 id</li>
 *   <li>每张卡: ticks / 下一张卡 id, 以及每个格子的 7 个进度集合</li>
 *   <li>每名玩家: 待选队列、已拥有/已使用词条、冷却、词条记忆</li>
 * </ul>
 * 这些值要么是不可变对象 (TeamScore / TeamCompletedCard / Instant / UUID) 直接共享引用,
 * 要么是小集合的浅拷贝, 所以一次快照是几百个微小对象, 30 秒一次的开销可以忽略。
 *
 * <p>回溯时把这些字段直接写回**活对象**, 棋盘更新由 bingo 自己的
 * `CardViewService.updateCard`(每 tick 比对缓存)自动推送给客户端。
 * 世界方块、玩家位置/背包不回溯。
 */
object RewindService {

    private const val LOOKBACK_MILLIS = 30L * 60L * 1000L
    private const val RETAIN_MILLIS = 31L * 60L * 1000L
    private const val SNAPSHOT_SLACK_MILLIS = 10_000L
    private const val SNAPSHOT_INTERVAL_TICKS = 20 * 30
    private const val COUNTDOWN_TICKS = 20 * 10
    private const val MAX_SNAPSHOTS = 80

    // ------------------------------------------------------------------
    // snapshot model (只存进度, 不存棋盘定义)
    // ------------------------------------------------------------------

    private class Snapshot(
        val time: Long,
        val progress: ProgressSnapshot,
        val perks: List<PerkSnapshot>,
    )

    private class ProgressSnapshot(
        val gameState: GameState,
        val endedAt: Instant?,
        val timeOffline: Duration,
        val timeAdjustment: Duration,
        val isForfeit: Boolean,
        val teams: Map<String, TeamSnapshot>,
        val cards: Map<UUID, CardSnapshot>,
    )

    private class TeamSnapshot(
        val cardId: UUID?,
        val currentScore: TeamScore,
        val persistentScore: TeamScore,
        val winner: TeamWinner?,
        val completedCards: List<TeamCompletedCard>,
    )

    private class CardSnapshot(
        val ticks: Int,
        val nextCardId: UUID?,
        val objectives: Map<String, ObjectiveSnapshot>,
    )

    private class ObjectiveSnapshot(
        val teamsSeen: Set<BingoTeamKey>,
        val teamsAchieved: Map<BingoTeamKey, Instant>,
        val teamsLost: Map<BingoTeamKey, Instant>,
        val teamsOnceAchieved: Set<BingoTeamKey>,
        val players: Map<UUID, BingoObjectiveCapture>,
        val playersHolding: Map<UUID, BingoObjectiveCapture>,
        val teamsProgress: Map<BingoTeamKey, Float>,
    )

    private class PerkSnapshot(
        val uuid: UUID,
        val pendingOffers: List<List<String>>,
        val owned: List<String>,
        val used: Set<String>,
        val cooldowns: Map<String, Long>,
        val memory: Map<String, String>,
    )

    private class PendingRewind(val uuid: UUID, val perkId: String, val executeAtTick: Int)

    private val snapshots = ArrayDeque<Snapshot>()
    private val pendingReapply = mutableSetOf<UUID>()

    @Volatile
    private var bingoState: BingoState? = null

    @Volatile
    private var capturing = false

    @Volatile
    private var restoring = false

    private var gameStartMillis = 0L
    private var lastSnapshotTick = 0
    private var pending: PendingRewind? = null

    val isCapturing: Boolean get() = capturing

    fun register() {
        BingoEvents.INIT.register { api -> captureBingoState(api) }

        ServerTickEvents.END_SERVER_TICK.register { server -> tick(server) }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val uuid = handler.player.uuid
            if (pendingReapply.remove(uuid)) {
                handler.player.server?.execute {
                    val state = PerkStateManager.get(uuid) ?: return@execute
                    applyPerks(handler.player, state)
                }
            }
        }
    }

    private fun captureBingoState(api: IBingoApi) {
        try {
            var type: Class<*>? = api.javaClass
            while (type != null && type != Any::class.java) {
                val field = type.declaredFields.firstOrNull { it.name == "state" }
                if (field != null) {
                    field.isAccessible = true
                    bingoState = field.get(api) as? BingoState
                    if (bingoState != null) {
                        RandomPerksMod.logger.info("[Rewind] captured the Bingo state")
                    }
                    return
                }
                type = type.superclass
            }
            RandomPerksMod.logger.warn("[Rewind] could not locate the Bingo state field")
        } catch (t: Throwable) {
            RandomPerksMod.logger.warn("[Rewind] failed to capture the Bingo state", t)
        }
    }

    // ------------------------------------------------------------------
    // capture lifecycle
    // ------------------------------------------------------------------

    fun startGame() {
        val server = ServerHolder.server ?: return
        clearCaptureData()
        pendingReapply.clear()
        pending = null
        restoring = false
        capturing = true
        gameStartMillis = System.currentTimeMillis()
        lastSnapshotTick = server.ticks
        takeSnapshot()
        RandomPerksMod.logger.info("[Rewind] started Bingo/perk snapshot capture")
    }

    fun stopGame() {
        capturing = false
        restoring = false
        pending = null
        pendingReapply.clear()
        clearCaptureData()
    }

    /** Called by the mythic perk as soon as it is acquired. */
    fun begin(player: ServerPlayerEntity, perkId: String) {
        val server = ServerHolder.server ?: return
        if (!capturing || restoring || bingoState == null) {
            player.sendMessage(Text.literal("【逆转时间】当前无法回溯时间。"), true)
            return
        }
        if (pending != null) {
            return
        }

        pending = PendingRewind(player.uuid, perkId, server.ticks + COUNTDOWN_TICKS)
        broadcast(server, "【逆转时间】10 秒后，本局进度与词条将回到 30 分钟前……")
    }

    private fun tick(server: MinecraftServer) {
        val current = pending
        if (current != null) {
            if (!capturing) {
                pending = null
            } else {
                val remaining = current.executeAtTick - server.ticks
                if (remaining <= 0) {
                    pending = null
                    execute(server, current)
                } else if (remaining % 20 == 0) {
                    broadcast(server, "【逆转时间】${remaining / 20} 秒…", overlay = true)
                }
            }
        }

        if (!capturing) {
            return
        }

        if (server.ticks - lastSnapshotTick >= SNAPSHOT_INTERVAL_TICKS) {
            lastSnapshotTick = server.ticks
            takeSnapshot()
        }
    }

    // ------------------------------------------------------------------
    // capture
    // ------------------------------------------------------------------

    private fun takeSnapshot() {
        val live = bingoState ?: return
        val now = System.currentTimeMillis()
        snapshots.addLast(Snapshot(now, captureProgress(live), capturePerks()))
        prune(now)
    }

    private fun captureProgress(live: BingoState) = ProgressSnapshot(
        gameState = live.state,
        endedAt = live.endedAt,
        timeOffline = live.timeOffline,
        timeAdjustment = live.timeAdjustment,
        isForfeit = live.isForfeit,
        teams = live.teams.values.associate { team ->
            team.id to TeamSnapshot(
                cardId = team.cardId,
                currentScore = team.currentScore,
                persistentScore = team.persistentScore,
                winner = team.winner,
                // TeamCompletedCard 是不可变 data class, 直接共享引用即可
                completedCards = team.completedCards.toList(),
            )
        },
        cards = live.cards.associate { card ->
            card.id to CardSnapshot(
                ticks = card.ticks,
                nextCardId = card.nextCardId,
                objectives = card.objectives.mapValues { (_, objective) ->
                    ObjectiveSnapshot(
                        teamsSeen = objective.teamsSeen.toSet(),
                        teamsAchieved = objective.teamsAchieved.toMap(),
                        teamsLost = objective.teamsLost.toMap(),
                        teamsOnceAchieved = objective.teamsOnceAchieved.toSet(),
                        players = objective.players.toMap(),
                        playersHolding = objective.playersHolding.toMap(),
                        teamsProgress = objective.teamsProgress.toMap(),
                    )
                },
            )
        },
    )

    private fun capturePerks(): List<PerkSnapshot> = PerkStateManager.all().map { state ->
        PerkSnapshot(
            uuid = state.uuid,
            pendingOffers = state.pendingOffers.map { it.toList() },
            owned = state.ownedPerks.map { it.id },
            used = state.usedPerks.toSet(),
            cooldowns = state.cooldowns.toMap(),
            memory = state.memory.toMap(),
        )
    }

    private fun prune(now: Long) {
        val cutoff = now - RETAIN_MILLIS
        while (snapshots.isNotEmpty() && snapshots.first().time < cutoff) {
            snapshots.removeFirst()
        }
        while (snapshots.size > MAX_SNAPSHOTS) {
            snapshots.removeFirst()
        }
    }

    private fun clearCaptureData() {
        snapshots.clear()
    }

    // ------------------------------------------------------------------
    // rewind
    // ------------------------------------------------------------------

    private fun execute(server: MinecraftServer, request: PendingRewind) {
        if (restoring) {
            return
        }
        restoring = true
        val target = maxOf(gameStartMillis, System.currentTimeMillis() - LOOKBACK_MILLIS)
        val minutes = ((System.currentTimeMillis() - target) / 60_000L).toInt().coerceAtLeast(0)

        val snap = pickSnapshot(target)
        if (snap == null) {
            broadcast(server, "【逆转时间】没有可用的历史记录。")
            restoring = false
            return
        }

        broadcast(server, "【逆转时间】时间开始倒流……")

        try {
            restoreProgress(snap, System.currentTimeMillis() - snap.time)
            restorePerks(server, snap, request.uuid, request.perkId)
            broadcast(server, "【逆转时间】本局进度与词条已回到 $minutes 分钟前。")
            RandomPerksMod.logger.info("[Rewind] rewound Bingo progress by {} minutes", minutes)
        } catch (t: Throwable) {
            RandomPerksMod.logger.error("[Rewind] rewind failed", t)
            broadcast(server, "【逆转时间】回溯过程中出现异常，已尽量恢复。")
        } finally {
            restoring = false
            capturing = false
            pending = null
            clearCaptureData()
        }
    }

    private fun pickSnapshot(target: Long): Snapshot? {
        val cutoff = target + SNAPSHOT_SLACK_MILLIS
        return snapshots.lastOrNull { it.time <= cutoff } ?: snapshots.firstOrNull()
    }

    private fun restoreProgress(snap: Snapshot, rewindMillis: Long) {
        val live = bingoState ?: return
        val progress = snap.progress

        live.state = progress.gameState
        live.endedAt = progress.endedAt
        live.timeOffline = progress.timeOffline
        // 计时 = (updatedAt - startedAt) + timeAdjustment - timeOffline
        // 把回溯掉的那段时间从 timeAdjustment 里减掉, 剩余时间才会跟着后退
        live.timeAdjustment = progress.timeAdjustment.minusMillis(rewindMillis.coerceAtLeast(0L))
        live.isForfeit = progress.isForfeit
        live.updatedAt = Instant.now()

        val liveCards = live.cards.associateBy { it.id }

        // 队伍: 恢复分数/胜者/已完成卡片; 队伍成员与地图缓存保持当前值
        for (team in live.teams.values) {
            val saved = progress.teams[team.id] ?: continue
            team.currentScore = saved.currentScore
            team.persistentScore = saved.persistentScore
            team.winner = saved.winner
            saved.cardId?.let { if (it in liveCards) team.cardId = it }

            team.completedCards.clear()
            team.completedCards.addAll(saved.completedCards)
        }

        // 卡片: 恢复重掷计数与每个格子的进度 (显示数据/图标保持当前值)
        for (card in live.cards) {
            val saved = progress.cards[card.id] ?: continue
            card.ticks = saved.ticks
            card.nextCardId = saved.nextCardId

            for ((objectiveId, objective) in card.objectives) {
                val objectiveSaved = saved.objectives[objectiveId] ?: continue
                objective.teamsSeen = objectiveSaved.teamsSeen.toMutableSet()
                objective.teamsAchieved = objectiveSaved.teamsAchieved.toMutableMap()
                objective.teamsLost = objectiveSaved.teamsLost.toMutableMap()
                objective.teamsOnceAchieved.clear()
                objective.teamsOnceAchieved.addAll(objectiveSaved.teamsOnceAchieved)
                objective.players = objectiveSaved.players.toMutableMap()
                objective.playersHolding = objectiveSaved.playersHolding.toMutableMap()
                objective.teamsProgress = objectiveSaved.teamsProgress.toMutableMap()
            }
        }
    }

    // ------------------------------------------------------------------
    // perk state
    // ------------------------------------------------------------------

    private fun restorePerks(
        server: MinecraftServer,
        snap: Snapshot,
        activatorUuid: UUID,
        perkId: String,
    ) {
        BingoLifecycle.clearAllPerkState()

        for (entry in snap.perks) {
            val state = PerkStateManager.getOrCreate(entry.uuid)
            state.pendingOffers.clear()
            state.ownedPerks.clear()
            state.usedPerks.clear()
            state.cooldowns.clear()
            state.memory.clear()

            state.pendingOffers.addAll(entry.pendingOffers.map { it.toList() })
            state.ownedPerks.addAll(entry.owned.mapNotNull { PerkRegistry.byId(it) })
            state.usedPerks.addAll(entry.used)
            state.cooldowns.putAll(entry.cooldowns)
            state.memory.putAll(entry.memory)
        }

        // The rewind perk itself is not undone: it stays owned and marked as used.
        val activatorState = PerkStateManager.getOrCreate(activatorUuid)
        val rewindPerk = PerkRegistry.byId(perkId)
        if (rewindPerk != null) {
            if (activatorState.ownedPerks.none { it.id == rewindPerk.id }) {
                activatorState.ownedPerks.add(rewindPerk)
            }
            activatorState.usedPerks.add(rewindPerk.id)
        }

        reapplyPerks(server)
    }

    private fun reapplyPerks(server: MinecraftServer) {
        pendingReapply.clear()
        for (state in PerkStateManager.all()) {
            val player = server.playerManager.getPlayer(state.uuid)
            if (player == null) {
                pendingReapply.add(state.uuid)
                continue
            }
            applyPerks(player, state)
        }
    }

    private fun applyPerks(player: ServerPlayerEntity, state: PlayerPerkState) {
        val context = PerkContext(player, state)
        for (perk in state.ownedPerks.toList()) {
            runCatching { perk.onRespawn(context) }
                .onFailure { RandomPerksMod.logger.warn("[Rewind] perk {} onRespawn failed", perk.id, it) }
        }
        for (perk in state.ownedPerks.toList()) {
            runCatching { perk.onPerkListChanged(context) }
                .onFailure { RandomPerksMod.logger.warn("[Rewind] perk {} list-change failed", perk.id, it) }
        }
        PerkGrantService.syncAbilities(player)
    }

    private fun broadcast(server: MinecraftServer, text: String, overlay: Boolean = false) {
        for (player in server.playerManager.playerList) {
            player.sendMessage(Text.literal(text), overlay)
        }
    }
}

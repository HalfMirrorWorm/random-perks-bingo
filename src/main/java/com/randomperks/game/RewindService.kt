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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.Leashable
import net.minecraft.entity.boss.WitherEntity
import net.minecraft.entity.boss.dragon.EnderDragonEntity
import net.minecraft.entity.mob.ElderGuardianEntity
import net.minecraft.entity.mob.WardenEntity
import net.minecraft.entity.passive.TameableEntity
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.passive.WanderingTraderEntity
import net.minecraft.inventory.Inventory
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.RegistryKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.storage.NbtReadView
import net.minecraft.storage.NbtWriteView
import net.minecraft.text.Text
import net.minecraft.util.ErrorReporter
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

/**
 * 「逆转时间」的数据层: **检查点 + 变更日志** 的伪回溯。
 *
 * <h2>记录</h2>
 * 开局记录一次检查点, 之后每进行 30 分钟再记录一次。检查点包含:
 * <ul>
 *   <li>Bingo 进度 (只拷贝会变的字段, 不序列化整份状态)</li>
 *   <li>每名在线玩家的完整 NBT (背包/生命/饥饿/效果/经验/位置)</li>
 *   <li>所有已加载容器方块实体的 NBT (箱子/桶/熔炉/潜影盒...)</li>
 *   <li>重要生物 (Boss、带名字、已驯服/拴绳、村民/流浪商人) 的 NBT</li>
 *   <li>所有玩家词条状态</li>
 * </ul>
 * 检查点之后, 用 mixin 记录每个坐标**第一次**被写入前的方块状态 (变更日志)。
 * 因此常态开销就是一次 HashMap 查找, 不需要周期性全量扫描。
 *
 * <h2>回溯</h2>
 * 回到最近一次检查点:
 * <ol>
 *   <li>按日志把方块写回去 (分帧, 每 tick 一批, 避免一次性卡死)</li>
 *   <li>恢复容器内容</li>
 *   <li>恢复玩家 NBT (含位置), 并同步给客户端</li>
 *   <li>恢复仍存活的重要生物</li>
 *   <li>恢复 Bingo 进度与词条, 重挂词条效果</li>
 * </ol>
 * 这是"伪回溯": 新生成的区块、记录上限之外的方块、离线玩家、已死亡的重要生物
 * 都不会被还原。
 */
object RewindService {

    /** 每局进行多久记录一次检查点 */
    private const val CHECKPOINT_INTERVAL_TICKS = 20 * 60 * 30

    /** 触发后到真正回溯的倒计时 */
    private const val COUNTDOWN_TICKS = 20 * 10

    /** 方块变更日志上限, 超过后停止记录并标记为不完整 */
    private const val MAX_BLOCK_EDITS = 250_000

    /** 每个 tick 还原多少方块 (控制单 tick 卡顿) */
    private const val BLOCKS_PER_TICK = 1_500

    /**
     * 还原方块: 通知客户端 + 跳过掉落/邻居更新/方块加入回调/方块实体替换回调,
     * 避免连锁反应, 也避免把"回溯后不该存在的容器"里的东西撒出来。
     */
    private const val RESTORE_FLAGS =
        Block.NOTIFY_LISTENERS or Block.FORCE_STATE or Block.SKIP_DROPS or
            Block.SKIP_BLOCK_ENTITY_REPLACED_CALLBACK or Block.SKIP_BLOCK_ADDED_CALLBACK

    // ------------------------------------------------------------------
    // checkpoint model
    // ------------------------------------------------------------------

    private class Checkpoint(
        val time: Long,
        val ticks: Int,
        val progress: ProgressSnapshot,
        val perks: List<PerkSnapshot>,
        val players: List<PlayerSnapshot>,
        val containers: List<ContainerSnapshot>,
        val entities: List<EntitySnapshot>,
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

    private class PlayerSnapshot(
        val uuid: UUID,
        val dimension: RegistryKey<World>,
        val nbt: NbtCompound,
    )

    private class ContainerSnapshot(
        val dimension: RegistryKey<World>,
        val pos: Long,
        val type: BlockEntityType<*>,
        val nbt: NbtCompound,
    )

    private class EntitySnapshot(
        val dimension: RegistryKey<World>,
        val uuid: UUID,
        val type: EntityType<*>,
        val nbt: NbtCompound,
    )

    private class BlockEdit(
        val dimension: RegistryKey<World>,
        val pos: Long,
        val previous: BlockState,
    )

    private class PendingRewind(val uuid: UUID, val perkId: String, val executeAtTick: Int)

    private class RestoreJob(
        val checkpoint: Checkpoint,
        val activatedBy: UUID,
        val perkId: String,
        val minutes: Int,
        /** 按游戏 tick 计算的回溯时长 (毫秒), 服务器暂停的时间不算 */
        val rewindMillis: Long,
        val edits: List<BlockEdit>,
        val truncated: Boolean,
    ) {
        var index = 0
    }

    // ------------------------------------------------------------------
    // state
    // ------------------------------------------------------------------

    private val loadedBlockEntities: MutableSet<BlockEntity> =
        Collections.newSetFromMap(IdentityHashMap())

    /** dimension -> (packed pos -> 该坐标在检查点时的方块状态) */
    private val blockEdits = HashMap<RegistryKey<World>, HashMap<Long, BlockState>>()
    private var blockEditCount = 0
    private var journalTruncated = false

    private var checkpoint: Checkpoint? = null
    private var restoreJob: RestoreJob? = null
    private val pendingReapply = mutableSetOf<UUID>()

    @Volatile
    private var bingoState: BingoState? = null

    @Volatile
    private var capturing = false

    @Volatile
    private var restoring = false

    private var nextCheckpointTick = 0
    private var pending: PendingRewind? = null

    val isCapturing: Boolean get() = capturing

    fun register() {
        BingoEvents.INIT.register { api -> captureBingoState(api) }

        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register { be, _ -> loadedBlockEntities.add(be) }
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register { be, _ -> loadedBlockEntities.remove(be) }

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

    /** 服务器关闭: 清掉跨对局的缓存 */
    fun shutdown() {
        stopGame()
        loadedBlockEntities.clear()
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
    // lifecycle
    // ------------------------------------------------------------------

    fun startGame() {
        val server = ServerHolder.server ?: return
        clearCaptureData()
        pendingReapply.clear()
        pending = null
        restoring = false
        restoreJob = null
        capturing = true
        nextCheckpointTick = server.ticks + CHECKPOINT_INTERVAL_TICKS
        takeCheckpoint(server)
        RandomPerksMod.logger.info("[Rewind] started checkpoint capture (interval {} min)", CHECKPOINT_INTERVAL_TICKS / 1200)
    }

    fun stopGame() {
        capturing = false
        restoring = false
        pending = null
        restoreJob = null
        pendingReapply.clear()
        clearCaptureData()
    }

    /** Called by the mythic perk as soon as it is acquired. */
    fun begin(player: ServerPlayerEntity, perkId: String) {
        val server = ServerHolder.server ?: return
        if (!capturing || restoring || checkpoint == null) {
            player.sendMessage(Text.literal("【逆转时间】当前无法回溯时间。"), true)
            return
        }
        if (pending != null) {
            return
        }

        pending = PendingRewind(player.uuid, perkId, server.ticks + COUNTDOWN_TICKS)
        broadcast(server, "【逆转时间】10 秒后，本局将回到最近一次记录点……")
    }

    private fun tick(server: MinecraftServer) {
        val job = restoreJob
        if (job != null) {
            stepRestore(server, job)
            return
        }

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

        // 倒计时期间不刷新检查点, 否则恰好跨过记录点时触发会几乎不退
        if (pending == null && server.ticks >= nextCheckpointTick) {
            while (server.ticks >= nextCheckpointTick) {
                nextCheckpointTick += CHECKPOINT_INTERVAL_TICKS
            }
            takeCheckpoint(server)
        }
    }

    // ------------------------------------------------------------------
    // block journal (called from the mixin)
    // ------------------------------------------------------------------

    fun shouldRecordBlockChange(): Boolean = capturing && !restoring && !journalTruncated

    fun recordBlockChange(world: ServerWorld, pos: BlockPos, previous: BlockState) {
        val edits = blockEdits.getOrPut(world.registryKey) { HashMap() }
        // 同一坐标只保留间隔内第一次变更前的状态
        if (edits.putIfAbsent(pos.asLong(), previous) == null) {
            blockEditCount++
            if (blockEditCount > MAX_BLOCK_EDITS) {
                journalTruncated = true
                RandomPerksMod.logger.warn(
                    "[Rewind] block journal reached the {} entry cap; later changes will not be recorded",
                    MAX_BLOCK_EDITS,
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // checkpoint capture
    // ------------------------------------------------------------------

    private fun takeCheckpoint(server: MinecraftServer) {
        val live = bingoState ?: return
        val now = System.currentTimeMillis()
        checkpoint = Checkpoint(
            time = now,
            ticks = server.ticks,
            progress = captureProgress(live),
            perks = capturePerks(),
            players = capturePlayers(server),
            containers = captureContainers(),
            entities = captureEntities(server),
        )
        // 检查点数据已经拿到, 从这里开始记录下一段间隔的方块变更
        blockEdits.clear()
        blockEditCount = 0
        journalTruncated = false
        RandomPerksMod.logger.info("[Rewind] checkpoint stored")
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

    private fun capturePlayers(server: MinecraftServer): List<PlayerSnapshot> =
        server.playerManager.playerList.mapNotNull { player ->
            try {
                val view = NbtWriteView.create(ErrorReporter.EMPTY, server.registryManager)
                player.writeData(view)
                PlayerSnapshot(player.uuid, player.world.registryKey, view.nbt)
            } catch (t: Throwable) {
                RandomPerksMod.logger.warn("[Rewind] failed to snapshot player {}", player.name.string, t)
                null
            }
        }

    private fun captureContainers(): List<ContainerSnapshot> {
        val result = ArrayList<ContainerSnapshot>()
        for (be in loadedBlockEntities.toList()) {
            if (be.isRemoved) continue
            if (be !is Inventory) continue
            val world = be.world as? ServerWorld ?: continue
            val nbt = try {
                be.createNbt(world.registryManager)
            } catch (t: Throwable) {
                RandomPerksMod.logger.warn("[Rewind] failed to snapshot container at {}", be.pos, t)
                continue
            }
            result.add(ContainerSnapshot(world.registryKey, be.pos.asLong(), be.type, nbt))
        }
        return result
    }

    private fun captureEntities(server: MinecraftServer): List<EntitySnapshot> {
        val result = ArrayList<EntitySnapshot>()
        for (world in server.worlds) {
            for (entity in world.iterateEntities()) {
                if (!isImportant(entity)) continue
                val nbt = try {
                    val view = NbtWriteView.create(ErrorReporter.EMPTY, server.registryManager)
                    entity.writeData(view)
                    view.nbt
                } catch (t: Throwable) {
                    RandomPerksMod.logger.warn("[Rewind] failed to snapshot entity {} at {}", entity.uuid, entity.pos, t)
                    continue
                }
                result.add(EntitySnapshot(world.registryKey, entity.uuid, entity.type, nbt))
            }
        }
        return result
    }

    private fun isImportant(entity: Entity): Boolean {
        if (entity is ServerPlayerEntity) return false
        if (entity is EnderDragonEntity || entity is WitherEntity ||
            entity is WardenEntity || entity is ElderGuardianEntity
        ) {
            return true
        }
        if (entity is VillagerEntity || entity is WanderingTraderEntity) return true
        if (entity.hasCustomName()) return true
        if (entity is TameableEntity && entity.isTamed) return true
        if (entity is Leashable && entity.isLeashed) return true
        return false
    }

    // ------------------------------------------------------------------
    // rewind
    // ------------------------------------------------------------------

    private fun execute(server: MinecraftServer, request: PendingRewind) {
        val snap = checkpoint
        if (snap == null) {
            broadcast(server, "【逆转时间】没有可用的记录点。")
            return
        }

        val edits = flattenBlockEdits()
        val truncated = journalTruncated
        blockEdits.clear()
        blockEditCount = 0

        capturing = false
        restoring = true

        // 显示用游戏 tick (暂停的墙钟时间不算 "几分钟前");
        // 但 bingo 的计时器是从墙钟推导的 (暂停时间记在 timeOffline), 所以时间补偿要按墙钟退。
        val elapsedTicks = (server.ticks - snap.ticks).coerceAtLeast(0)
        val minutes = elapsedTicks / 1200
        val rewindMillis = (System.currentTimeMillis() - snap.time).coerceAtLeast(0L)
        restoreJob = RestoreJob(snap, request.uuid, request.perkId, minutes, rewindMillis, edits, truncated)
        broadcast(server, "【逆转时间】时间开始倒流……")
        RandomPerksMod.logger.info(
            "[Rewind] rewinding to checkpoint {} min ago ({} block edits, truncated={})",
            minutes, edits.size, truncated,
        )
    }

    private fun flattenBlockEdits(): List<BlockEdit> {
        val edits = ArrayList<BlockEdit>(blockEditCount)
        for ((dimension, map) in blockEdits) {
            for ((pos, state) in map) {
                edits.add(BlockEdit(dimension, pos, state))
            }
        }
        return edits
    }

    private fun stepRestore(server: MinecraftServer, job: RestoreJob) {
        var processed = 0
        while (job.index < job.edits.size && processed < BLOCKS_PER_TICK) {
            val edit = job.edits[job.index++]
            processed++
            val world = server.getWorld(edit.dimension) ?: continue
            try {
                world.setBlockState(BlockPos.fromLong(edit.pos), edit.previous, RESTORE_FLAGS, 0)
            } catch (t: Throwable) {
                RandomPerksMod.logger.warn("[Rewind] failed to restore block at {}", BlockPos.fromLong(edit.pos), t)
            }
        }

        if (job.index >= job.edits.size) {
            restoreJob = null
            finishRestore(server, job)
        }
    }

    private fun finishRestore(server: MinecraftServer, job: RestoreJob) {
        try {
            restoreContainers(server, job.checkpoint)
            restorePlayers(server, job.checkpoint)
            restoreEntities(server, job.checkpoint)
            restoreProgress(job.checkpoint, job.rewindMillis)
            restorePerks(server, job.checkpoint, job.activatedBy, job.perkId)

            val suffix = if (job.truncated) "（方块变更记录达到上限，部分方块未能还原）" else ""
            broadcast(server, "【逆转时间】本局已回到 ${job.minutes} 分钟前的记录点。$suffix")
            RandomPerksMod.logger.info("[Rewind] rewind finished ({} min back)", job.minutes)
        } catch (t: Throwable) {
            RandomPerksMod.logger.error("[Rewind] rewind failed", t)
            broadcast(server, "【逆转时间】回溯过程中出现异常，已尽量恢复。")
        } finally {
            restoring = false
            pending = null
            clearCaptureData()
        }
    }

    private fun restoreProgress(snap: Checkpoint, rewindMillis: Long) {
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

    private fun restoreContainers(server: MinecraftServer, snap: Checkpoint) {
        var missing = 0
        for (container in snap.containers) {
            val world = server.getWorld(container.dimension) ?: continue
            val pos = BlockPos.fromLong(container.pos)
            val be = world.getBlockEntity(pos)
            if (be == null || be.type != container.type) {
                // 区块未加载 / 方块已被别的东西取代 —— 伪回溯允许跳过
                missing++
                continue
            }
            try {
                be.read(NbtReadView.create(ErrorReporter.EMPTY, server.registryManager, container.nbt))
                be.markDirty()
                world.updateListeners(pos, be.cachedState, be.cachedState, Block.NOTIFY_LISTENERS)
            } catch (t: Throwable) {
                RandomPerksMod.logger.warn("[Rewind] failed to restore container at {}", pos, t)
            }
        }
        if (missing > 0) {
            RandomPerksMod.logger.info("[Rewind] skipped {} container(s) that were not loaded", missing)
        }
    }

    private fun restorePlayers(server: MinecraftServer, snap: Checkpoint) {
        for (saved in snap.players) {
            val player = server.playerManager.getPlayer(saved.uuid) ?: continue
            try {
                player.closeHandledScreen()
                // 先走正常移除流程, 把客户端身上的旧效果清掉 (会发 RemoveEntityStatusEffect 包)
                for (effect in player.statusEffects.toList()) {
                    player.removeStatusEffect(effect.effectType)
                }
                player.readData(NbtReadView.create(ErrorReporter.EMPTY, server.registryManager, saved.nbt))

                val world = server.getWorld(saved.dimension) ?: player.world
                player.stopRiding()
                player.teleport(world, player.x, player.y, player.z, emptySet(), player.yaw, player.pitch, false)
                player.markHealthDirty()
                player.playerScreenHandler.syncState()
                if (player.currentScreenHandler !== player.playerScreenHandler) {
                    player.currentScreenHandler.syncState()
                }
                player.sendAbilitiesUpdate()
                // readData 直接改了 activeStatusEffects, 需要手动把恢复后的效果推给客户端
                server.playerManager.sendStatusEffects(player)
            } catch (t: Throwable) {
                RandomPerksMod.logger.warn("[Rewind] failed to restore player {}", player.name.string, t)
            }
        }
    }

    private fun restoreEntities(server: MinecraftServer, snap: Checkpoint) {
        var missing = 0
        for (saved in snap.entities) {
            val world = server.getWorld(saved.dimension) ?: continue
            val entity = world.getEntity(saved.uuid)
            if (entity == null || entity.type != saved.type) {
                // 已死亡/未加载/被替换: 按约定不复活
                missing++
                continue
            }
            try {
                entity.readData(NbtReadView.create(ErrorReporter.EMPTY, server.registryManager, saved.nbt))
            } catch (t: Throwable) {
                RandomPerksMod.logger.warn("[Rewind] failed to restore entity {}", saved.uuid, t)
            }
        }
        if (missing > 0) {
            RandomPerksMod.logger.info("[Rewind] skipped {} important entities that are gone", missing)
        }
    }

    // ------------------------------------------------------------------
    // perk state
    // ------------------------------------------------------------------

    private fun restorePerks(
        server: MinecraftServer,
        snap: Checkpoint,
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

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun clearCaptureData() {
        checkpoint = null
        blockEdits.clear()
        blockEditCount = 0
        journalTruncated = false
    }

    private fun broadcast(server: MinecraftServer, text: String, overlay: Boolean = false) {
        for (player in server.playerManager.playerList) {
            player.sendMessage(Text.literal(text), overlay)
        }
    }
}

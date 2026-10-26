package com.randomperks.game

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.randomperks.RandomPerksMod
import com.randomperks.lifecycle.BingoLifecycle
import com.randomperks.perk.PerkContext
import com.randomperks.perk.PerkRegistry
import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import me.jfenn.bingo.api.BingoEvents
import me.jfenn.bingo.api.IBingoApi
import kotlinx.serialization.json.Json
import me.jfenn.bingo.common.state.BingoState
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.lang.reflect.Modifier
import java.util.UUID

/**
 * "逆转时间" mythic perk (low-overhead, in-process version).
 *
 * <p>Every 30 seconds it snapshots two things:
 * <ul>
 *   <li>the serialized Yet Another Bingo game state (cards, objectives, team
 *       scores, timer, completed cards, ...), and</li>
 *   <li>the in-memory perk state of every player.</li>
 * </ul>
 *
 * <p>When triggered, the live Bingo state is merged back to the snapshot taken
 * 30 minutes ago (or the start of the game) and the perk state is restored.
 * YABingo's own tick controllers then re-send the changed card tiles and
 * scoreboard to clients. No world/block snapshots are taken, so the overhead is
 * just a small JSON encode every 30 seconds.
 *
 * <p>The world, block entities and player positions/inventories are NOT rewound.
 */
object RewindService {

    private const val LOOKBACK_MILLIS = 30L * 60L * 1000L
    private const val RETAIN_MILLIS = 31L * 60L * 1000L
    private const val SNAPSHOT_SLACK_MILLIS = 10_000L
    private const val SNAPSHOT_INTERVAL_TICKS = 20 * 30
    private const val COUNTDOWN_TICKS = 20 * 10
    private const val MAX_SNAPSHOTS = 80

    private val GSON = Gson()
    private val PERK_LIST_TYPE = object : TypeToken<List<PerkEntryDto>>() {}.type

    /** YABingo's configured Json instance (handles its custom serializers). */
    private val JSON: Json by lazy {
        try {
            Class.forName("me.jfenn.bingo.common.utils.JsonKt")
                .getMethod("getJson")
                .invoke(null) as Json
        } catch (t: Throwable) {
            RandomPerksMod.logger.warn("[Rewind] falling back to the default Json instance", t)
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        }
    }

    private data class BingoSave(val time: Long, val json: String)

    private data class PerkSnap(val time: Long, val json: String)

    private data class PendingRewind(val uuid: UUID, val perkId: String, val executeAtTick: Int)

    private class PerkEntryDto(
        var uuid: String = "",
        var pendingOffers: List<List<String>> = emptyList(),
        var owned: List<String> = emptyList(),
        var used: List<String> = emptyList(),
        var cooldowns: Map<String, Long> = emptyMap(),
        var memory: Map<String, String> = emptyMap(),
    )

    private val bingoSaves = mutableListOf<BingoSave>()
    private val perkSnaps = mutableListOf<PerkSnap>()
    private val pendingReapply = mutableSetOf<UUID>()

    @Volatile
    private var bingoState: BingoState? = null

    @Volatile
    private var capturing = false

    @Volatile
    private var restoring = false

    private var gameStartMillis = 0L
    private var lastSnapshotTick = 0
    private var lastPruneTick = 0
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
        lastPruneTick = server.ticks
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
        if (server.ticks - lastPruneTick >= 20 * 30) {
            lastPruneTick = server.ticks
            prune(System.currentTimeMillis())
        }
    }

    // ------------------------------------------------------------------
    // snapshots
    // ------------------------------------------------------------------

    private fun takeSnapshot() {
        val now = System.currentTimeMillis()
        captureBingo(now)
        capturePerks(now)
        trimSnapshots()
    }

    private fun captureBingo(now: Long) {
        val state = bingoState ?: return
        try {
            val text = JSON.encodeToString(BingoState.serializer(), state)
            bingoSaves.add(BingoSave(now, text))
        } catch (t: Throwable) {
            RandomPerksMod.logger.warn("[Rewind] failed to snapshot the Bingo state", t)
        }
    }

    private fun capturePerks(now: Long) {
        val entries = PerkStateManager.all().map { state ->
            PerkEntryDto(
                uuid = state.uuid.toString(),
                pendingOffers = state.pendingOffers.map { it.toList() },
                owned = state.ownedPerks.map { it.id },
                used = state.usedPerks.toList(),
                cooldowns = state.cooldowns.toMap(),
                memory = state.memory.toMap(),
            )
        }
        perkSnaps.add(PerkSnap(now, GSON.toJson(entries)))
    }

    private fun prune(now: Long) {
        val cutoff = now - RETAIN_MILLIS
        bingoSaves.removeAll { it.time < cutoff }
        perkSnaps.removeAll { it.time < cutoff }
        trimSnapshots()
    }

    private fun trimSnapshots() {
        trim(bingoSaves, MAX_SNAPSHOTS)
        trim(perkSnaps, MAX_SNAPSHOTS)
    }

    private fun <T> trim(list: MutableList<T>, max: Int) {
        if (list.size > max) {
            list.subList(0, list.size - max).clear()
        }
    }

    private fun clearCaptureData() {
        bingoSaves.clear()
        perkSnaps.clear()
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

        broadcast(server, "【逆转时间】时间开始倒流……")

        try {
            restoreBingo(target)
            restorePerks(server, target, request.uuid, request.perkId)
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

    private fun restoreBingo(target: Long) {
        val live = bingoState ?: return
        val cutoff = target + SNAPSHOT_SLACK_MILLIS
        val snap = bingoSaves.filter { it.time <= cutoff }.maxByOrNull { it.time }
            ?: bingoSaves.minByOrNull { it.time }
            ?: return

        val old = try {
            JSON.decodeFromString(BingoState.serializer(), snap.json)
        } catch (t: Throwable) {
            RandomPerksMod.logger.error("[Rewind] failed to decode the Bingo snapshot", t)
            return
        }

        val rewindMillis = (System.currentTimeMillis() - snap.time).coerceAtLeast(0L)
        mergeBingoState(live, old, rewindMillis)
    }

    private fun mergeBingoState(live: BingoState, old: BingoState, rewindMillis: Long) {
        // Keep the current game id / start time; only rewind the game progress.
        // The Bingo timer is derived from (updatedAt - startedAt) + timeAdjustment:
        // subtract the rewind amount so the displayed/remaining time also moves back.
        live.state = old.state
        if (live.startedAt == null) {
            live.startedAt = old.startedAt
        }
        live.updatedAt = java.time.Instant.now()
        live.endedAt = old.endedAt
        live.timeOffline = old.timeOffline
        live.timeAdjustment = old.timeAdjustment.minusMillis(rewindMillis)
        live.isForfeit = old.isForfeit

        // Teams: restore scores/cards/objective state; keep the current membership.
        for ((key, oldTeam) in old.teams) {
            val liveTeam = live.teams[key] ?: continue
            // Keep the live team's map/card-view cache: CardViewService uses it
            // to detect which tiles changed and to send the card update packets.
            copyMutableFields(oldTeam, liveTeam, skip = setOf("players", "map", "cardId"))
            if (live.cards.any { it.id == oldTeam.cardId }) {
                liveTeam.cardId = oldTeam.cardId
            }

            val liveCards = live.cards.associateBy { it.id }
            val mapped = liveTeam.completedCards.map { completed ->
                val liveCard = liveCards[completed.card.id]
                if (liveCard != null) completed.copy(card = liveCard) else completed
            }
            liveTeam.completedCards.clear()
            liveTeam.completedCards.addAll(mapped)
        }

        // Cards: copy each objective's state into the live objective object, so
        // transient fields like the resolved display/item icon stay intact.
        for (liveCard in live.cards) {
            val oldCard = old.cards.find { it.id == liveCard.id } ?: continue
            liveCard.nextCardId = oldCard.nextCardId
            liveCard.ticks = oldCard.ticks

            for ((objectiveId, liveObjective) in liveCard.objectives) {
                val oldObjective = oldCard.objectives[objectiveId] ?: continue
                copyMutableFields(oldObjective, liveObjective)
            }
        }
    }

    /**
     * Copies every non-static, non-transient field from [src] to [dst].
     * Final mutable collections are merged in place; other final fields are
     * left alone. This lets us restore Kotlin data-class state without touching
     * transient/resolved display data.
     */
    private fun copyMutableFields(src: Any, dst: Any, skip: Set<String> = emptySet()) {
        var type: Class<*>? = src.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || Modifier.isTransient(field.modifiers)) continue
                if (field.name in skip) continue
                // kotlinx @Transient 的字段不写进快照, 反序列化后是默认值 (如 objective.display
                // 会是 EMPTY)。若把它们覆盖回活对象, 棋盘格子的图标/装饰/点击行为会丢失。
                if (isRuntimeOnly(type, field.name)) continue

                try {
                    field.isAccessible = true
                    if (!Modifier.isFinal(field.modifiers)) {
                        field.set(dst, copyValue(field.get(src)))
                    } else {
                        val srcValue = field.get(src) ?: continue
                        val dstValue = field.get(dst) ?: continue
                        when {
                            srcValue is MutableMap<*, *> && dstValue is MutableMap<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                (dstValue as MutableMap<Any?, Any?>).let { target ->
                                    target.clear()
                                    target.putAll(srcValue as Map<Any?, Any?>)
                                }
                            }
                            srcValue is MutableCollection<*> && dstValue is MutableCollection<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                (dstValue as MutableCollection<Any?>).let { target ->
                                    target.clear()
                                    target.addAll(srcValue as Collection<Any?>)
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // ignore fields that cannot be copied
                }
            }
            type = type.superclass
        }
    }

    /**
     * 该字段是否被 kotlinx.serialization 标记为 `@Transient`(只存在于内存、不写进快照)。
     *
     * Kotlin 把**属性**上的注解放在编译器生成的合成方法上, 字段本身没有这个注解,
     * 所以不能直接用 `field.isAnnotationPresent`。属性 `display` → `getDisplay$annotations()`,
     * 属性 `isInitialized` → `isInitialized$annotations()`。
     */
    private fun isRuntimeOnly(owner: Class<*>, fieldName: String): Boolean {
        val capitalized = fieldName.replaceFirstChar { it.uppercaseChar() }
        val candidates = arrayOf("$fieldName\$annotations", "get$capitalized\$annotations")
        var type: Class<*>? = owner
        while (type != null && type != Any::class.java) {
            for (candidate in candidates) {
                val method = try {
                    type.getDeclaredMethod(candidate)
                } catch (_: NoSuchMethodException) {
                    null
                } ?: continue
                if (method.isAnnotationPresent(kotlinx.serialization.Transient::class.java)) return true
            }
            type = type.superclass
        }
        return false
    }

    private fun copyValue(value: Any?): Any? = when (value) {
        is MutableMap<*, *> -> value.toMutableMap()
        is MutableSet<*> -> value.toMutableSet()
        is MutableList<*> -> value.toMutableList()
        else -> value
    }

    // ------------------------------------------------------------------
    // perk state
    // ------------------------------------------------------------------

    private fun restorePerks(
        server: MinecraftServer,
        target: Long,
        activatorUuid: UUID,
        perkId: String,
    ) {
        val cutoff = target + SNAPSHOT_SLACK_MILLIS
        val snap = perkSnaps.filter { it.time <= cutoff }.maxByOrNull { it.time }
            ?: perkSnaps.minByOrNull { it.time }
            ?: return

        BingoLifecycle.clearAllPerkState()

        val entries: List<PerkEntryDto> = try {
            GSON.fromJson(snap.json, PERK_LIST_TYPE) ?: emptyList()
        } catch (t: Throwable) {
            RandomPerksMod.logger.error("[Rewind] failed to parse the perk snapshot", t)
            emptyList()
        }

        for (dto in entries) {
            val state = try {
                PerkStateManager.getOrCreate(UUID.fromString(dto.uuid))
            } catch (t: Throwable) {
                continue
            }
            state.pendingOffers.clear()
            state.ownedPerks.clear()
            state.usedPerks.clear()
            state.cooldowns.clear()
            state.memory.clear()

            state.pendingOffers.addAll(dto.pendingOffers.map { it.toList() })
            state.ownedPerks.addAll(dto.owned.mapNotNull { PerkRegistry.byId(it) })
            state.usedPerks.addAll(dto.used)
            state.cooldowns.putAll(dto.cooldowns)
            state.memory.putAll(dto.memory)
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


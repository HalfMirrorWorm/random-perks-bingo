package com.randomperks.recap

import com.randomperks.RandomPerksMod
import com.randomperks.game.RewindService
import com.randomperks.network.PerkNetworking
import kotlinx.serialization.json.Json
import me.jfenn.bingo.api.BingoEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.network.ServerPlayerEntity
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * 「上局回顾」的数据采集与分发。
 *
 * <h3>记录什么</h3>
 * <ul>
 *   <li>开局时的队伍快照 (名字 + 颜色);</li>
 *   <li>每次**拿下格子**: 时间、队伍、完成的玩家、格子名;</li>
 *   <li>每次**选择词条**: 时间、玩家、词条 id。</li>
 * </ul>
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>GAME_STARTED: 清空并开始记录;</li>
 *   <li>GAME_ENDED: 冻结成"上局", 写入 `config/random-perks-bingo-recap.json`;</li>
 *   <li>大厅菜单里的按钮 → 把"上局"整包发给客户端渲染。</li>
 * </ul>
 *
 * 对局进行中打开回顾看到的是**上一局**的数据 (还没有已结束的对局时显示空状态)。
 */
object RecapService {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val recapFile: Path
        get() = FabricLoader.getInstance().configDir.resolve("random-perks-bingo-recap.json")

    private var recording = false
    private var gameId: String = ""
    private var startedAt: Long = 0L

    private val teams = mutableListOf<RecapTeam>()
    private val picks = mutableListOf<RecapPick>()
    private val captures = mutableListOf<RecapCapture>()

    /** 上一局 (已结束) 的回顾 */
    @Volatile
    private var last: RecapData? = null

    fun register() {
        loadLast()

        BingoEvents.GAME_STARTED.register { event ->
            startRecording(event.id.toString())
        }
        BingoEvents.GAME_ENDED.register {
            finishRecording()
        }
    }

    // ------------------------------------------------------------------
    // recording
    // ------------------------------------------------------------------

    private fun startRecording(id: String) {
        recording = true
        gameId = id
        startedAt = System.currentTimeMillis()
        teams.clear()
        picks.clear()
        captures.clear()

        // 队伍快照: 开局时 bingo 已经把队伍建好了
        val state = RewindService.currentBingoState() ?: return
        for (team in state.teams.values) {
            val name = runCatching { team.name.value.string }.getOrDefault(team.id)
            val color = runCatching {
                val rgb = team.textColor.colorValue
                if (rgb == null) 0xFFFFFF else 0xFF000000.toInt() or rgb
            }.getOrDefault(0xFFFFFF)
            teams.add(RecapTeam(team.id, name, color))
        }
    }

    private fun finishRecording() {
        if (!recording) return
        recording = false

        val data = RecapData(
            gameId = gameId,
            startedAt = startedAt,
            endedAt = System.currentTimeMillis(),
            teams = teams.toList(),
            picks = picks.toList(),
            captures = captures.toList(),
        )
        last = data
        save(data)
        RandomPerksMod.logger.info(
            "[RandomPerks] recap stored for game {} ({} capture(s), {} pick(s))",
            gameId, data.captures.size, data.picks.size,
        )
    }

    private fun secondsSinceStart(): Int =
        ((System.currentTimeMillis() - startedAt) / 1000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /** 某队拿下一个格子 (由 BingoHook 调用) */
    fun recordCapture(teamId: String, playerUuid: UUID?, playerName: String?, objectiveName: String) {
        if (!recording) return
        captures.add(
            RecapCapture(
                seconds = secondsSinceStart(),
                teamId = teamId,
                playerUuid = playerUuid?.toString() ?: "",
                playerName = playerName ?: "",
                objective = objectiveName,
            )
        )
    }

    /** 玩家从三选一里选走了一个词条 */
    fun recordPerkPick(player: ServerPlayerEntity, perkId: String) {
        if (!recording) return
        picks.add(
            RecapPick(
                seconds = secondsSinceStart(),
                playerUuid = player.uuid.toString(),
                playerName = player.name.string,
                teamId = player.scoreboardTeam?.name ?: "",
                perkId = perkId,
            )
        )
    }

    // ------------------------------------------------------------------
    // distribution
    // ------------------------------------------------------------------

    /** 大厅菜单里的按钮被点击: 把上一局的回顾发给该玩家 */
    fun openFor(player: ServerPlayerEntity) {
        val data = last
        val payload = if (data == null) RecapData() else data
        PerkNetworking.sendRecap(player, json.encodeToString(RecapData.serializer(), payload))
    }

    fun lastRecap(): RecapData? = last

    // ------------------------------------------------------------------
    // persistence
    // ------------------------------------------------------------------

    private fun loadLast() {
        last = runCatching {
            if (Files.exists(recapFile)) {
                json.decodeFromString(RecapData.serializer(), Files.readString(recapFile))
            } else {
                null
            }
        }.getOrElse {
            RandomPerksMod.logger.warn("[RandomPerks] failed to read the recap file: {}", it.toString())
            null
        }
    }

    private fun save(data: RecapData) {
        runCatching {
            Files.createDirectories(recapFile.parent)
            Files.writeString(recapFile, json.encodeToString(RecapData.serializer(), data))
        }.onFailure {
            RandomPerksMod.logger.warn("[RandomPerks] failed to save the recap file: {}", it.toString())
        }
    }
}

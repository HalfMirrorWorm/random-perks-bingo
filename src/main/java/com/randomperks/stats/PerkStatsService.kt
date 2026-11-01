package com.randomperks.stats

import com.randomperks.RandomPerksMod
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * 词条**选取率**统计 —— 每个词条被抽到多少次、被选走多少次。
 *
 * <ul>
 *   <li>{@code offered}: 该词条出现在某位玩家的候选组里的次数 (每发放一次机会 +1);</li>
 *   <li>{@code picked}: 玩家真正选走它的次数;</li>
 *   <li>选取率 = picked / offered。</li>
 * </ul>
 *
 * 数据**跨对局累计**, 存在 `config/random-perks-bingo-stats.json`。
 * 用 `/randomperks stats [数量]` 查看, `/randomperks stats reset` 清空。
 *
 * <p>写入是延迟批量化的: 记录时只标脏, 每 30 秒 / 对局结束 / 服务器停止时才真正落盘,
 * 免得每发一次机会就写一次文件。
 */
object PerkStatsService {

    @Serializable
    private data class StatsData(
        val offered: MutableMap<String, Int> = mutableMapOf(),
        val picked: MutableMap<String, Int> = mutableMapOf(),
    )

    /** 报告用的一行 */
    class Entry(val id: String, val offered: Int, val picked: Int) {
        val rate: Float get() = if (offered <= 0) 0f else picked.toFloat() / offered
    }

    private const val FLUSH_INTERVAL_TICKS = 20 * 30

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private var data = StatsData()
    private var dirty = false
    private var nextFlushTick = 0

    private val statsFile: Path
        get() = FabricLoader.getInstance().configDir.resolve("random-perks-bingo-stats.json")

    fun register() {
        load()

        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (!dirty) return@register
            if (server.ticks < nextFlushTick) return@register
            nextFlushTick = server.ticks + FLUSH_INTERVAL_TICKS
            save()
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { save() }
    }

    fun load() {
        data = runCatching {
            if (Files.exists(statsFile)) {
                json.decodeFromString(StatsData.serializer(), Files.readString(statsFile))
            } else {
                StatsData()
            }
        }.getOrElse {
            RandomPerksMod.logger.warn("[RandomPerks] failed to read perk stats: {}", it.toString())
            StatsData()
        }
        RandomPerksMod.logger.info("[RandomPerks] perk stats loaded ({} perk(s) tracked)", data.offered.size)
    }

    private fun save() {
        if (!dirty) return
        dirty = false
        runCatching {
            Files.createDirectories(statsFile.parent)
            Files.writeString(statsFile, json.encodeToString(StatsData.serializer(), data))
        }.onFailure {
            RandomPerksMod.logger.warn("[RandomPerks] failed to save perk stats: {}", it.toString())
        }
    }

    // ------------------------------------------------------------------
    // recording
    // ------------------------------------------------------------------

    /** 一组候选被发放给某位玩家 */
    fun recordOffered(ids: Collection<String>) {
        if (ids.isEmpty()) return
        for (id in ids) {
            data.offered[id] = (data.offered[id] ?: 0) + 1
        }
        dirty = true
    }

    /** 玩家从候选里选走了这个词条 */
    fun recordPicked(id: String) {
        data.picked[id] = (data.picked[id] ?: 0) + 1
        dirty = true
    }

    // ------------------------------------------------------------------
    // reporting
    // ------------------------------------------------------------------

    /** 按选取率降序排列 (同率按被抽到次数降序, 再按 id) */
    fun entries(): List<Entry> =
        data.offered.entries
            .filter { it.value > 0 }
            .map { Entry(it.key, it.value, data.picked[it.key] ?: 0) }
            .sortedWith(
                compareByDescending<Entry> { it.rate }
                    .thenByDescending { it.offered }
                    .thenBy { it.id },
            )

    fun totalOffered(): Int = data.offered.values.sum()

    fun totalPicked(): Int = data.picked.values.sum()

    fun reset() {
        data = StatsData()
        dirty = true
        save()
        RandomPerksMod.logger.info("[RandomPerks] perk stats cleared")
    }
}

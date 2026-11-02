package com.randomperks.stats

import com.randomperks.RandomPerksMod
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * 词条**选取率**统计 —— 用来判断"玩家更想选什么", 给稀有度平衡提供依据。
 *
 * <h3>口径</h3>
 * <ul>
 *   <li>{@code offered}: 该词条出现在某位玩家的候选组里的次数 (每发放一次机会 +1);</li>
 *   <li>{@code picked}: 玩家真正选走它的次数;</li>
 *   <li>选取率 = picked / offered。</li>
 * </ul>
 * 三选一里平均期望是 33.3%, 但不同稀有度的词条出现在同一组里时, 这个基准会被拉高/拉低,
 * 所以报告里给的是**同稀有度基准** (该稀有度整体的 picked/offered) 和每个词条相对它的差值 ——
 * 差值明显为负 = 同档位里不受欢迎 (可考虑降档); 明显为正 = 该升档。
 *
 * <p>数据**跨对局累计**, 存在 `config/random-perks-bingo-stats.json`, 并记录统计起始时间;
 * 调完稀有度后用 `/randomperks stats reset` 开一段新统计, 免得新旧档位的数据混在一起。
 *
 * <p>写入是延迟批量化的: 记录时只标脏, 每 30 秒 / 服务器停止时才真正落盘。
 */
object PerkStatsService {

    /** 少于这个抽到次数就不参与"最不受欢迎"排序 (噪声太大), 列表里也会标记出来 */
    const val MIN_SAMPLES = 10

    @Serializable
    private data class StatsData(
        /** 统计起始时间 (ISO-8601); 首次记录或 reset 时写入 */
        var since: String? = null,
        val offered: MutableMap<String, Int> = mutableMapOf(),
        val picked: MutableMap<String, Int> = mutableMapOf(),
    )

    /** 报告用的一行 */
    class Entry(val id: String, val offered: Int, val picked: Int, val rarity: PerkRarity?) {
        val rate: Float get() = if (offered <= 0) 0f else picked.toFloat() / offered

        /** 样本是否足够 (不足时排序/对比只能当参考) */
        val sufficient: Boolean get() = offered >= MIN_SAMPLES
    }

    /** 按稀有度汇总: 该档整体被抽到/被选中的次数 */
    class RaritySummary(val rarity: PerkRarity, val perkCount: Int, val offered: Int, val picked: Int) {
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
            if (server.tickCount < nextFlushTick) return@register
            nextFlushTick = server.tickCount + FLUSH_INTERVAL_TICKS
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
        markSampled()
    }

    /** 玩家从候选里选走了这个词条 */
    fun recordPicked(id: String) {
        data.picked[id] = (data.picked[id] ?: 0) + 1
        markSampled()
    }

    private fun markSampled() {
        if (data.since == null) data.since = Instant.now().toString()
        dirty = true
    }

    // ------------------------------------------------------------------
    // reporting
    // ------------------------------------------------------------------

    /** 统计起始时间 (ISO-8601); 还没有数据时为 null */
    fun since(): String? = data.since

    /** 按选取率降序排列 (同率按被抽到次数降序, 再按 id) */
    fun entries(): List<Entry> =
        data.offered.entries
            .filter { it.value > 0 }
            .map { (id, offered) ->
                Entry(id, offered, data.picked[id] ?: 0, PerkRegistry.byId(id)?.rarity)
            }
            .sortedWith(
                compareByDescending<Entry> { it.rate }
                    .thenByDescending { it.offered }
                    .thenBy { it.id },
            )

    /** 各稀有度的整体表现 (只列有过投放的档位) */
    fun raritySummaries(): List<RaritySummary> {
        val perks = IntArray(PerkRarity.entries.size)
        val offered = IntArray(PerkRarity.entries.size)
        val picked = IntArray(PerkRarity.entries.size)

        for ((id, count) in data.offered) {
            val rarity = PerkRegistry.byId(id)?.rarity ?: continue
            val i = rarity.ordinal
            perks[i]++
            offered[i] += count
            picked[i] += data.picked[id] ?: 0
        }

        return PerkRarity.entries
            .map { RaritySummary(it, perks[it.ordinal], offered[it.ordinal], picked[it.ordinal]) }
            .filter { it.offered > 0 }
    }

    fun totalOffered(): Int = data.offered.values.sum()

    fun totalPicked(): Int = data.picked.values.sum()

    fun reset() {
        data = StatsData(since = Instant.now().toString())
        dirty = true
        save()
        RandomPerksMod.logger.info("[RandomPerks] perk stats cleared")
    }
}

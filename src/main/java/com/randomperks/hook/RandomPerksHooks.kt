package com.randomperks.hook

import com.randomperks.RandomPerksMod
import com.randomperks.game.PerkGrantService
import java.util.UUID

/**
 * Kotlin 侧的 hook 入口: 由 [BingoHook] (Java) 在 bingo 计分点调用。
 *
 * 这里只接收纯数据 (UUID / String), 不引用任何 bingo 内部类型。
 */
object RandomPerksHooks {

    /**
     * 某个队伍完成了一个 bingo 格子。
     *
     * @param teamId           队伍 id (如 bingo_red)
     * @param memberUuids      完成瞬间该队伍的成员
     * @param scoredPlayerUuid 捕获该格子的玩家 (可能为 null, 如 opponent 类目标)
     * @param objectiveId      完成的格子/目标 id
     */
    fun onTeamScored(
        teamId: String,
        memberUuids: List<UUID>,
        scoredPlayerUuid: UUID?,
        objectiveId: String,
    ) {
        RandomPerksMod.logger.info(
            "[RandomPerks] team '{}' scored '{}' (by {}) → {} member(s)",
            teamId, objectiveId, scoredPlayerUuid?.toString() ?: "unknown", memberUuids.size,
        )

        // 给每位在线队员发放一次选择机会 (+ 触发词条联动)
        PerkGrantService.onTeamScored(memberUuids, objectiveId)
    }

    /** 每个队伍上一次已知的连线数 (用于按差值补发, 支持一次捕获完成多条连线) */
    private val lastLineCounts = mutableMapOf<String, Int>()

    /**
     * 每次格子计分后调用, 按连线数差值发放选择机会。
     *
     * 不依赖 bingo 的 sendLineCaptured —— 那个只在游戏目标为 Lines 时调用。
     */
    fun onTeamLineCount(teamId: String, memberUuids: List<UUID>, currentLines: Int) {
        val previous = lastLineCounts[teamId] ?: 0
        lastLineCounts[teamId] = currentLines
        val newLines = currentLines - previous
        if (newLines <= 0) return

        RandomPerksMod.logger.info(
            "[RandomPerks] team '{}' completed {} new line(s) ({} → {}) → {} member(s)",
            teamId, newLines, previous, currentLines, memberUuids.size,
        )
        PerkGrantService.onTeamLines(memberUuids, newLines)
    }

    /** 格子丢失后同步基准 (不发放, 只防止重新完成同一条线时算错) */
    fun updateLineBaseline(teamId: String, currentLines: Int) {
        lastLineCounts[teamId] = currentLines
    }

    /** 对局开始/重置时清空连线计数 */
    fun resetLineCounts() = lastLineCounts.clear()
}

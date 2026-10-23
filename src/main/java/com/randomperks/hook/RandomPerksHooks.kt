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

    /**
     * 某个队伍完成了一条连线 —— 只额外发放一次选择机会, 不重复触发格子计分回调。
     */
    fun onTeamLine(teamId: String, memberUuids: List<UUID>) {
        RandomPerksMod.logger.info(
            "[RandomPerks] team '{}' completed a line → {} member(s)",
            teamId, memberUuids.size,
        )
        PerkGrantService.onTeamLine(memberUuids)
    }
}

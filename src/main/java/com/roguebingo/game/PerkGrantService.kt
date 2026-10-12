package com.roguebingo.game

import com.roguebingo.RogueBingoMod
import com.roguebingo.network.PerkNetworking
import com.roguebingo.perk.PerkContext
import com.roguebingo.server.ServerHolder
import com.roguebingo.state.PerkStateManager
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import java.util.UUID

/**
 * 服务端: 向玩家发放选择机会 / 广播"完成格子"事件到词条回调。
 */
object PerkGrantService {

    /**
     * 团队完成了一个 bingo 格子: 给每位队员发放一次选择机会, 并触发词条的联动回调。
     */
    fun onTeamScored(memberUuids: List<UUID>, objectiveId: String) {
        val server = ServerHolder.server ?: return

        for (uuid in memberUuids) {
            val player = server.playerManager.getPlayer(uuid) ?: continue
            val state = PerkStateManager.getOrCreate(uuid)

            // 已获得词条的联动回调 (如"每次完成格子触发一次效果")
            if (state.ownedPerks.isNotEmpty()) {
                val ctx = PerkContext(player, state)
                for (perk in state.ownedPerks.toList()) {
                    runCatching { perk.onTeamScored(ctx, objectiveId) }
                        .onFailure { RogueBingoMod.logger.error("[RogueBingo] perk {} onTeamScored failed", perk.id, it) }
                }
            }

            grantChoice(player, 1)
        }

        RogueBingoMod.logger.info(
            "[RogueBingo] objective '{}' scored by team with {} member(s) → +1 choice each",
            objectiveId, memberUuids.size,
        )
    }

    /** 给单个玩家发放 [count] 次选择机会并提示 */
    fun grantChoice(player: ServerPlayerEntity, count: Int) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        state.pendingChoices += count

        PerkNetworking.sendNotify(player, state.pendingChoices)

        player.sendMessage(
            Text.translatable("rogue-bingo.message.gained_choice", state.pendingChoices),
            true, // actionbar
        )
        player.playSoundToPlayer(
            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
            SoundCategory.PLAYERS,
            0.8f,
            1.2f,
        )
    }
}

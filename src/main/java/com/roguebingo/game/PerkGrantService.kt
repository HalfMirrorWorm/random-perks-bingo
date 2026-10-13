package com.roguebingo.game

import com.roguebingo.RogueBingoMod
import com.roguebingo.network.PerkNetworking
import com.roguebingo.perk.PerkContext
import com.roguebingo.roll.PerkRollService
import com.roguebingo.server.ServerHolder
import com.roguebingo.state.PerkStateManager
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import java.util.UUID

/**
 * 服务端: 向玩家发放选择机会 / 广播"完成格子"事件到词条回调。
 *
 * 选项在**发放时**为每位玩家独立生成并固定 —— 同一次机会反复打开界面看到的是同一组,
 * 但每位玩家的选项互不相同 (各自按自己的词条池抽取)。
 */
object PerkGrantService {

    /**
     * 团队完成了一个 bingo 格子: 给每位队员发放一次选择机会 (各自独立随机), 并触发词条联动回调。
     */
    fun onTeamScored(memberUuids: List<UUID>, objectiveId: String) {
        val server = ServerHolder.server ?: return
        val players = memberUuids.mapNotNull { server.playerManager.getPlayer(it) }
        if (players.isEmpty()) return

        // 已获得词条的联动回调 (如"每次完成格子触发一次效果")
        for (player in players) {
            val state = PerkStateManager.getOrCreate(player.uuid)
            if (state.ownedPerks.isEmpty()) continue
            val ctx = PerkContext(player, state)
            for (perk in state.ownedPerks.toList()) {
                runCatching { perk.onTeamScored(ctx, objectiveId) }
                    .onFailure { RogueBingoMod.logger.error("[RogueBingo] perk {} onTeamScored failed", perk.id, it) }
            }
        }

        grantOffer(players)

        RogueBingoMod.logger.info(
            "[RogueBingo] objective '{}' scored by team with {} member(s) → +1 choice each",
            objectiveId, players.size,
        )
    }

    /**
     * 给每位玩家发放一次选择机会 (每人独立生成一组固定候选)。
     *
     * @return 是否至少发放成功一次 (词条池耗尽时为 false)
     */
    fun grantOffer(players: List<ServerPlayerEntity>): Boolean {
        var anyGranted = false

        for (player in players) {
            val state = PerkStateManager.getOrCreate(player.uuid)
            val offer = PerkRollService.rollOptions(state).map { it.id }
            if (offer.isEmpty()) continue

            state.pendingOffers.add(offer)
            notifyChoicesAvailable(player, state.pendingChoices)
            anyGranted = true
        }

        if (!anyGranted && players.isNotEmpty()) {
            RogueBingoMod.logger.warn("[RogueBingo] perk pool exhausted — no offer granted")
        }
        return anyGranted
    }

    /** 单人发放 (调试命令用) */
    fun grantChoice(player: ServerPlayerEntity, count: Int) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        repeat(count) {
            val offer = PerkRollService.rollOptions(state).map { it.id }
            if (offer.isNotEmpty()) state.pendingOffers.add(offer)
        }
        notifyChoicesAvailable(player, state.pendingChoices)
    }

    private fun notifyChoicesAvailable(player: ServerPlayerEntity, pending: Int) {
        PerkNetworking.sendNotify(player, pending)
        player.sendMessage(
            Text.translatable("rogue-bingo.message.gained_choice", pending),
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

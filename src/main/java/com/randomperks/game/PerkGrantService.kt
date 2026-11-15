package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.network.PerkNetworking
import com.randomperks.perk.PerkContext
import com.randomperks.perk.content.TraitPerks
import com.randomperks.roll.PerkRollService
import com.randomperks.server.ServerHolder
import com.randomperks.state.ChoiceKind
import com.randomperks.state.PerkStateManager
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvents
import net.minecraft.network.chat.Component
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
        if (!PerkSession.isEnabled()) return
        val server = ServerHolder.server ?: return
        val players = memberUuids.mapNotNull { server.playerList.getPlayer(it) }
        if (players.isEmpty()) return

        // 已获得词条的联动回调 (如"每次完成格子触发一次效果")
        for (player in players) {
            val state = PerkStateManager.getOrCreate(player.uuid)
            if (state.ownedPerks.isEmpty()) continue
            val ctx = PerkContext(player, state)
            for (perk in state.ownedPerks.toList()) {
                runCatching { perk.onTeamScored(ctx, objectiveId) }
                    .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} onTeamScored failed", perk.id, it) }
            }
        }

        grantOffer(players)

        RandomPerksMod.logger.info(
            "[RandomPerks] objective '{}' scored by team with {} member(s) → +1 choice each",
            objectiveId, players.size,
        )
    }

    /**
     * 团队完成了 [lineCount] 条新连线: 每位在线队员获得对应次数的**特质选择**机会。
     * 连线不再发普通词条机会; 同一格的普通词条机会仍由 [onTeamScored] 发放,
     * 两类机会按获得顺序进入同一条队列。
     */
    fun onTeamLines(memberUuids: List<UUID>, lineCount: Int) {
        if (lineCount <= 0) return
        if (!PerkSession.isEnabled()) return
        val server = ServerHolder.server ?: return
        val players = memberUuids.mapNotNull { server.playerList.getPlayer(it) }
        if (players.isEmpty()) return

        grantTraitChoices(players, lineCount)
        RandomPerksMod.logger.info(
            "[RandomPerks] bingo {} line(s) completed by team with {} member(s) → +{} trait choice each",
            lineCount, players.size, lineCount,
        )
    }

    /**
     * 给每位玩家发放一次选择机会 (每人独立生成一组固定候选)。
     *
     * @return 是否至少发放成功一次 (词条池耗尽时为 false)
     */
    fun grantOffer(players: List<ServerPlayer>, count: Int = 1): Boolean {
        if (count <= 0) return false
        var anyGranted = false

        for (player in players) {
            val state = PerkStateManager.getOrCreate(player.uuid)
            var added = 0

            for (i in 0 until count) {
                // 排除已被任何人持有的神话词条 (全局唯一)
                val offer = PerkRollService.rollOptions(
                    state,
                    extraExcluded = PerkStateManager.globallyOwnedMythicIds(),
                ).map { it.id }
                if (offer.isEmpty()) break

                state.pendingOffers.add(offer)
                state.pendingOrder.add(ChoiceKind.PERK)
                com.randomperks.stats.PerkStatsService.recordOffered(offer)
                added++
            }

            if (added > 0) {
                notifyChoices(player)
                anyGranted = true
            }
        }

        if (!anyGranted && players.isNotEmpty()) {
            RandomPerksMod.logger.warn("[RandomPerks] perk pool exhausted — no offer granted")
        }
        return anyGranted
    }

    /**
     * 发放特质选择机会 (每点一次从剩余的 13 条特质里选一条)。
     * 特质池已经抽完时不再增加机会。
     */
    fun grantTraitChoices(players: List<ServerPlayer>, count: Int = 1): Boolean {
        if (count <= 0) return false
        var anyGranted = false

        for (player in players) {
            val state = PerkStateManager.getOrCreate(player.uuid)
            val remaining = TraitPerks.available(state).size
            val grant = minOf(count, remaining)
            if (grant <= 0) continue

            repeat(grant) { state.pendingOrder.add(ChoiceKind.TRAIT) }
            notifyChoices(player)
            anyGranted = true
        }

        return anyGranted
    }

    /**
     * 开局: 按获得顺序先放一次特质选择, 紧接着放开局的普通词条机会。
     * 队列是 [TRAIT, PERK], 所以玩家先选特质, 再选开局词条。
     * 特质池没有剩余时只放普通机会。
     */
    fun grantStartingTrait(players: List<ServerPlayer>): Boolean {
        var anyGranted = false
        for (player in players) {
            val state = PerkStateManager.getOrCreate(player.uuid)
            if (TraitPerks.available(state).isNotEmpty()) {
                state.pendingOrder.add(ChoiceKind.TRAIT)
            }
            // 普通开局机会紧跟在开局特质之后 (按获得顺序)
            if (grantOffer(listOf(player), 1)) anyGranted = true
            else if (state.nextChoiceKind() == ChoiceKind.TRAIT) {
                notifyChoices(player)
                anyGranted = true
            }
        }
        return anyGranted
    }

    /** 单人发放 (调试命令用) */
    fun grantChoice(player: ServerPlayer, count: Int) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        repeat(count) {
            val offer = PerkRollService.rollOptions(
                state,
                extraExcluded = PerkStateManager.globallyOwnedMythicIds(),
            ).map { it.id }
            if (offer.isNotEmpty()) {
                state.pendingOffers.add(offer)
                state.pendingOrder.add(ChoiceKind.PERK)
                com.randomperks.stats.PerkStatsService.recordOffered(offer)
            }
        }
        notifyChoices(player)
    }

    /** 把普通词条 + 特质的总待选数同步给客户端 */
    fun notifyChoices(player: ServerPlayer) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        notifyChoicesAvailable(player, state.totalPending)
    }

    /** 把玩家的词条能力列表同步给其客户端 (矿透渲染/偷窃按键等客户端能力依赖它) */
    fun syncAbilities(player: ServerPlayer) {
        val state = PerkStateManager.getOrCreate(player.uuid)
        PerkNetworking.sendAbilitySync(player, state.ownedPerks.map { it.id })
    }

    /** 词条列表变化后调用: 让已拥有的词条即时重算联动效果 (替代定时轮询) */
    fun notifyPerkListChanged(player: ServerPlayer) {
        val state = PerkStateManager.get(player.uuid) ?: return
        if (state.ownedPerks.isEmpty()) return

        val ctx = PerkContext(player, state)
        for (perk in state.ownedPerks.toList()) {
            runCatching { perk.onPerkListChanged(ctx) }
                .onFailure { RandomPerksMod.logger.error("[RandomPerks] perk {} onPerkListChanged failed", perk.id, it) }
        }
    }

    private fun notifyChoicesAvailable(player: ServerPlayer, pending: Int) {
        PerkNetworking.sendNotify(player, pending)
        if (pending <= 0) return

        val state = PerkStateManager.get(player.uuid)
        val key = if (state != null && state.nextChoiceKind() == ChoiceKind.TRAIT) {
            "random-perks-bingo.message.gained_trait_choice"
        } else {
            "random-perks-bingo.message.gained_choice"
        }
        player.sendSystemMessage(
            Component.translatable(key, pending),
            true, // actionbar
        )
        player.level().playSound(
            null, player.x, player.y, player.z,
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f,
        )
    }
}

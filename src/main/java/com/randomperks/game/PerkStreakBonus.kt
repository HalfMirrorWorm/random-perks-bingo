package com.randomperks.game

import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkDefinition
import com.randomperks.perk.PerkRarity
import com.randomperks.perk.PerkRegistry
import com.randomperks.roll.PerkRollBias
import com.randomperks.state.PlayerPerkState
import com.randomperks.stats.PerkStatsService
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * "专研"特质: 选定一个流派后, 每选择 3 个该流派词条,
 * 下一次普通三选一保底出现一个该流派的紫色或金色词条。
 *
 * <p>进度/待触发/当前机会三个标记都存在 [PlayerPerkState.memory], 随存档和逆转时间快照持久化。
 * 触发时优先修改**下一次打开的普通机会** (包括已经生成、尚未打开的候选), 而不是只影响之后新发的机会。
 */
object PerkStreakBonus {

    const val PERK_ID = "trait_12"

    private const val ARCHETYPE_KEY = "streak_archetype"
    private const val PROGRESS_KEY = "streak_progress"
    private const val NEXT_KEY = "streak_pending"
    private const val CURRENT_KEY = "streak_current"
    private const val REQUIRED_PICKS = 3

    fun archetype(state: PlayerPerkState): PerkArchetype? {
        val name = state.memory[ARCHETYPE_KEY] ?: return null
        val archetype = runCatching { PerkArchetype.valueOf(name) }.getOrNull() ?: return null
        return archetype.takeIf { it != PerkArchetype.TRAIT }
    }

    fun setArchetype(state: PlayerPerkState, archetype: PerkArchetype) {
        state.memory[ARCHETYPE_KEY] = archetype.name
        state.memory[PROGRESS_KEY] = "0"
        state.memory.remove(NEXT_KEY)
        state.memory.remove(CURRENT_KEY)
    }

    fun clearAll(state: PlayerPerkState) {
        state.memory.remove(ARCHETYPE_KEY)
        state.memory.remove(PROGRESS_KEY)
        state.memory.remove(NEXT_KEY)
        state.memory.remove(CURRENT_KEY)
    }

    fun isPending(state: PlayerPerkState): Boolean = state.memory[NEXT_KEY] == "1"

    fun isCurrent(state: PlayerPerkState): Boolean = state.memory[CURRENT_KEY] == "1"

    fun clearCurrent(state: PlayerPerkState) {
        state.memory.remove(CURRENT_KEY)
    }

    /** 当前机会被转移/丢弃时, 把保底重新挂到下一次普通机会上 */
    fun requeueCurrent(state: PlayerPerkState) {
        if (!isCurrent(state)) return
        state.memory.remove(CURRENT_KEY)
        state.memory[NEXT_KEY] = "1"
    }

    /** 玩家从普通三选一里成功选中 [perk] 后调用 */
    fun recordPick(player: ServerPlayer, state: PlayerPerkState, perk: PerkDefinition) {
        if (!state.hasPerk(PERK_ID)) return
        val archetype = archetype(state) ?: return
        if (perk.archetype != archetype) return

        val progress = (state.memory[PROGRESS_KEY]?.toIntOrNull() ?: 0) + 1
        if (progress >= REQUIRED_PICKS) {
            state.memory[PROGRESS_KEY] = "0"
            state.memory[NEXT_KEY] = "1"
            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.streak_ready"),
                true,
            )
        } else {
            state.memory[PROGRESS_KEY] = progress.toString()
            player.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.streak_progress", progress, REQUIRED_PICKS),
                true,
            )
        }
    }

    /**
     * 确保 [options] 里有一个符合的紫色/金色流派词条。
     *
     * <p>会读写 memory 标记, 但**不更新** pendingOffers —— 调用方负责把修改后的列表写回。
     * 当前机会是"精选"时只允许塞金色, 避免破坏金/红三选一的承诺。
     *
     * @return true 表示候选已满足保底 (或本次成功注入)
     */
    fun applyToOffer(state: PlayerPerkState, options: MutableList<PerkDefinition>): Boolean {
        if (options.isEmpty()) return false
        if (!isPending(state) && !isCurrent(state)) return false
        if (!state.hasPerk(PERK_ID)) {
            clearAll(state)
            return false
        }
        val archetype = archetype(state) ?: run {
            clearAll(state)
            return false
        }

        val highOnly = PerkHighRarityOffer.isCurrentHigh(state)
        fun qualifies(perk: PerkDefinition): Boolean =
            perk.archetype == archetype &&
                (perk.rarity == PerkRarity.PURPLE || perk.rarity == PerkRarity.GOLD) &&
                (!highOnly || perk.rarity == PerkRarity.GOLD)

        if (options.any { qualifies(it) }) {
            if (isPending(state)) {
                state.memory.remove(NEXT_KEY)
                state.memory[CURRENT_KEY] = "1"
            }
            return true
        }

        val excluded = state.ownedPerks.mapTo(mutableSetOf()) { it.id }
        state.pendingOffers.forEach { excluded.addAll(it) }
        options.forEach { excluded.add(it.id) }
        val pool = PerkRegistry.all.filter { qualifies(it) && it.id !in excluded }
        if (pool.isEmpty()) return false

        val replacement = pool.random()
        var replaceIndex = options.lastIndex

        // 不要挤掉"专精"保底的唯一一个该流派词条
        val bias = PerkRollBias.bias(state)
        if (bias != null && replacement.archetype != bias) {
            val biasCount = options.count { it.archetype == bias }
            if (biasCount <= 1 && options[replaceIndex].archetype == bias) {
                replaceIndex = options.indices.lastOrNull { options[it].archetype != bias } ?: replaceIndex
            }
        }

        options[replaceIndex] = replacement
        PerkStatsService.recordOffered(listOf(replacement.id))
        state.memory.remove(NEXT_KEY)
        state.memory[CURRENT_KEY] = "1"
        return true
    }
}

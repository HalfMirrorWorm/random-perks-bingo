package com.randomperks.game

import com.randomperks.RandomPerksMod
import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * "反制"特质: 消耗一次充能, 无效化其他玩家下一次对你施加的负面词条效果。
 *
 * <p>充能存在 [PlayerPerkState.memory], 随存档持久化, 也会被"逆转时间"快照回退。
 * 只负责判定与通知; 真正的中止由各负面词条在效果生效前调用 [tryCounter] 完成。
 */
object PerkCounterService {

    const val PERK_ID = "trait_09"

    private const val ARMED_KEY = "counter_negative_perk"

    fun arm(state: PlayerPerkState) {
        state.memory[ARMED_KEY] = "1"
    }

    fun disarm(state: PlayerPerkState) {
        state.memory.remove(ARMED_KEY)
    }

    private fun isArmed(state: PlayerPerkState): Boolean = state.memory[ARMED_KEY] == "1"

    /**
     * 检查 [target] 是否反制了 [source] 的这次负面效果。
     *
     * @return true 表示已触发反制 (充能已消耗、双方已收到提示), 调用方必须立刻中止效果
     */
    fun tryCounter(target: ServerPlayer, source: ServerPlayer?, perkId: String): Boolean {
        if (source == null || source.uuid == target.uuid) return false

        val state = PerkStateManager.get(target.uuid) ?: return false
        if (!state.hasPerk(PERK_ID) || !isArmed(state)) return false

        disarm(state)

        val perkName = Component.translatable("random-perks-bingo.perk.$perkId.name")
        val targetMessage = Component.translatable(
            "random-perks-bingo.message.counter_triggered_target",
            source.name,
            perkName,
        )
        target.sendSystemMessage(targetMessage, false)
        target.sendSystemMessage(targetMessage, true)
        source.sendSystemMessage(
            Component.translatable(
                "random-perks-bingo.message.counter_triggered_source",
                perkName,
                target.name,
            ),
            false,
        )

        RandomPerksMod.logger.info(
            "[RandomPerks] {} countered {} from {}",
            target.name.string, perkId, source.name.string,
        )
        return true
    }
}

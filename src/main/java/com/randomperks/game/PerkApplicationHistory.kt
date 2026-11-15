package com.randomperks.game

import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import net.minecraft.server.level.ServerPlayer

/**
 * "回放"特质用的历史记录: 本局中**其他玩家**对某名玩家施加过的词条 id。
 *
 * <p>按施加顺序记录、去重; 数据存在 [PlayerPerkState.memory] 里,
 * 随现有词条状态存档持久化, 也会被"逆转时间"的快照一起回退。
 */
object PerkApplicationHistory {

    private const val KEY = "applied_perks"
    private const val SEPARATOR = ","

    /** 记录一次"他人对我施加词条"; 自己对自己不记 */
    fun record(target: ServerPlayer, perkId: String, source: ServerPlayer?) {
        if (source == null || source.uuid == target.uuid) return
        val state = PerkStateManager.get(target.uuid) ?: return
        val current = appliedPerks(state)
        if (perkId in current) return
        state.memory[KEY] = (current + perkId).joinToString(SEPARATOR)
    }

    fun appliedPerks(state: PlayerPerkState): List<String> =
        state.memory[KEY]?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
}

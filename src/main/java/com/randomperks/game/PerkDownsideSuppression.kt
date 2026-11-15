package com.randomperks.game

import com.randomperks.state.PerkStateManager
import com.randomperks.state.PlayerPerkState
import net.minecraft.server.level.ServerPlayer

/**
 * "净化"特质: 记录被玩家选中的**一个**词条, 让它自身的负面效果失效。
 *
 * <p>抑制 id 存在 [PlayerPerkState.memory] 里, 随现有状态持久化;
 * 具体哪些负面效果可被屏蔽见 [ELIGIBLE]。
 */
object PerkDownsideSuppression {

    const val KEY = "suppressed_downside_perk"

    /** 有明确负面效果、可被"净化"的词条 */
    val ELIGIBLE = setOf(
        "artisan_nya_axe",          // 永久虚弱 I
        "seeker_lay_flat",          // 失明 + 缓慢 IV
        "delver_collector",         // -50% 挖掘速度
        "delver_gluttonous_snake",  // -20% 基础挖掘速度
        "hunter_pearl_gauntlet",    // 剑不能横扫
        "hunter_veteran",           // 剑不能横扫
        "swift_gura_shark",         // 陆地不能疾跑
    )

    fun suppressedFor(state: PlayerPerkState): String? =
        state.memory[KEY]?.takeIf { it in ELIGIBLE }

    fun suppressedFor(player: ServerPlayer): String? =
        PerkStateManager.get(player.uuid)?.let { suppressedFor(it) }

    fun isSuppressed(state: PlayerPerkState, perkId: String): Boolean =
        perkId in ELIGIBLE && state.memory[KEY] == perkId

    fun isSuppressed(player: ServerPlayer, perkId: String): Boolean =
        PerkStateManager.get(player.uuid)?.let { isSuppressed(it, perkId) } == true

    fun set(state: PlayerPerkState, perkId: String) {
        if (perkId in ELIGIBLE) state.memory[KEY] = perkId
    }

    fun clear(state: PlayerPerkState) {
        state.memory.remove(KEY)
    }

    /** 当前已拥有、且带有可净化负面效果的词条 */
    fun eligibleOwned(state: PlayerPerkState) =
        state.ownedPerks.filter { it.id in ELIGIBLE }
}

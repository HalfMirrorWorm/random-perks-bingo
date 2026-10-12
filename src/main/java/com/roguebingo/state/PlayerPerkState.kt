package com.roguebingo.state

import com.roguebingo.perk.PerkDefinition
import java.util.UUID

/**
 * 单个玩家在"本局"中的词条状态。
 */
class PlayerPerkState(val uuid: UUID) {
    /** 当前累积的待选机会次数 */
    var pendingChoices: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
        }

    /** 已获得的词条 (本局内不可重复) */
    val ownedPerks: MutableList<PerkDefinition> = mutableListOf()

    /** 主动技能/触发效果的冷却到期时间 (游戏 tick 时间, 毫秒) */
    val cooldowns: MutableMap<String, Long> = mutableMapOf()

    /** 局内最近一次生成但尚未被选择的三选一 (offerId -> 词条 id 列表) */
    var pendingOffer: List<String>? = null

    fun hasPerk(id: String): Boolean = ownedPerks.any { it.id == id }

    fun reset() {
        pendingChoices = 0
        ownedPerks.clear()
        cooldowns.clear()
        pendingOffer = null
    }
}

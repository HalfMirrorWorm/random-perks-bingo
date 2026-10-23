package com.randomperks.state

import com.randomperks.perk.PerkDefinition
import java.util.UUID

/**
 * 单个玩家在"本局"中的词条状态。
 */
class PlayerPerkState(val uuid: UUID) {

    /**
     * 待选机会队列。
     *
     * 每个元素是一组固定的候选词条 id (通常 3 个)。选项在**发放机会时**生成:
     * - 同一次机会(同一个格子)发给全队的是同一组选项
     * - 未做出选择前, 反复打开界面看到的始终是同一组 (队列第一个)
     */
    val pendingOffers: MutableList<List<String>> = mutableListOf()

    /** 当前累积的待选机会次数 = 队列长度 */
    val pendingChoices: Int get() = pendingOffers.size

    /** 已获得的词条 (本局内不可重复; 局内**不会**被移出) */
    val ownedPerks: MutableList<PerkDefinition> = mutableListOf()

    /**
     * 已经使用过的一次性词条 id。
     *
     * 一次性词条获得时不立即生效, 由玩家在 V 界面点击触发; 触发成功后记在这里,
     * 词条卡面随之停止闪烁。局间随 [reset] 清空。
     */
    val usedPerks: MutableSet<String> = mutableSetOf()

    fun isUsed(perkId: String): Boolean = perkId in usedPerks

    fun markUsed(perkId: String) {
        usedPerks.add(perkId)
    }

    /** 主动技能/触发效果的冷却到期时间 (游戏 tick 时间) */
    val cooldowns: MutableMap<String, Long> = mutableMapOf()

    /** 词条自定义数据 (如"猎杀印记"记录的目标 UUID、偷窃目标等) */
    val memory: MutableMap<String, String> = mutableMapOf()

    fun hasPerk(id: String): Boolean = ownedPerks.any { it.id == id }

    /** 当前应展示的候选 (队列第一个, 未选择前不变) */
    fun currentOffer(): List<String>? = pendingOffers.firstOrNull()

    /** 消费最前面的机会 */
    fun consumeOffer() {
        if (pendingOffers.isNotEmpty()) pendingOffers.removeAt(0)
    }

    fun reset() {
        pendingOffers.clear()
        ownedPerks.clear()
        usedPerks.clear()
        cooldowns.clear()
        memory.clear()
    }
}

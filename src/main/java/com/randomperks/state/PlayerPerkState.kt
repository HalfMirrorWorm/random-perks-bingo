package com.randomperks.state

import com.randomperks.perk.PerkDefinition
import java.util.UUID

/**
 * 一个待选机会的类型。
 *
 * 普通词条机会与特质机会放进同一条**按获得顺序**排列的队列:
 * 先获得的先选, 不再按"普通优先/特质优先"分类。
 */
enum class ChoiceKind { PERK, TRAIT }

/**
 * 普通词条机会的候选规则。
 *
 * - [NORMAL]: 常规三选一, 按品质权重抽取
 * - [BLUE]: "蔚蓝"特质发放的额外机会, 候选全部为蓝色品质
 */
enum class OfferKind { NORMAL, BLUE }

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

    /**
     * 与 [pendingOffers] 一一对应的候选规则 (长度保持一致)。
     * 旧存档没有该字段时按 [OfferKind.NORMAL] 处理。
     */
    val pendingOfferKinds: MutableList<OfferKind> = mutableListOf()

    /**
     * 待选机会的**获得顺序**: PERK 对应 [pendingOffers] 的队首, TRAIT 对应一次特质选择。
     * 普通机会和特质机会混排, 严格按获得先后处理。
     */
    val pendingOrder: MutableList<ChoiceKind> = mutableListOf()

    /** 当前累积的待选机会次数 (普通 + 特质) */
    val pendingChoices: Int get() = pendingOrder.size

    /** 队首的待选类型 (没有待选时为 null) */
    fun nextChoiceKind(): ChoiceKind? = pendingOrder.firstOrNull()

    /** 已获得的词条 (本局内不可重复; 局内**不会**被移出) */
    val ownedPerks: MutableList<PerkDefinition> = mutableListOf()

    /**
     * 已经使用过的一次性词条 id。
     *
     * 一次性词条获得时不立即生效, 由玩家在 V 界面点击触发; 触发成功后记在这里,
     * 词条卡面随之停止闪烁。局间随 [reset] 清空。
     */
    val usedPerks: MutableSet<String> = mutableSetOf()

    /**
     * 多次使用的贮藏词条已使用次数 (仅 maxUses > 1 的词条)。
     * 单次词条仍然只使用 [usedPerks], 旧存档语义保持不变。
     */
    val useCounts: MutableMap<String, Int> = mutableMapOf()

    fun isUsed(perkId: String): Boolean = perkId in usedPerks

    fun markUsed(perkId: String) {
        usedPerks.add(perkId)
    }

    /** 该贮藏词条还剩几次可用 */
    fun usesRemaining(perk: PerkDefinition): Int {
        if (!perk.isOneTime) return 0
        val used = useCounts[perk.id] ?: if (perk.id in usedPerks) perk.maxUses else 0
        return (perk.maxUses - used).coerceAtLeast(0)
    }

    /** 消费一次使用次数; 次数用完后自动记入 [usedPerks] */
    fun consumeUse(perk: PerkDefinition) {
        if (perk.maxUses <= 1) {
            markUsed(perk.id)
            return
        }
        val used = (useCounts[perk.id] ?: 0) + 1
        useCounts[perk.id] = used
        if (used >= perk.maxUses) markUsed(perk.id)
    }

    /** 主动技能/触发效果的冷却到期时间 (游戏 tick 时间) */
    val cooldowns: MutableMap<String, Long> = mutableMapOf()

    /** 词条自定义数据 (如"猎杀印记"记录的目标 UUID、偷窃目标等) */
    val memory: MutableMap<String, String> = mutableMapOf()

    fun hasPerk(id: String): Boolean = ownedPerks.any { it.id == id }

    /** 当前应展示的普通候选 (只有队首是 PERK 时才有) */
    fun currentOffer(): List<String>? =
        if (pendingOrder.firstOrNull() == ChoiceKind.PERK) pendingOffers.firstOrNull() else null

    /** 队首普通候选的规则 (没有记录时按普通处理) */
    fun currentOfferKind(): OfferKind =
        if (pendingOrder.firstOrNull() == ChoiceKind.PERK) {
            pendingOfferKinds.firstOrNull() ?: OfferKind.NORMAL
        } else {
            OfferKind.NORMAL
        }

    /** 普通词条 + 特质的总待选数 (给通知/列表显示用) */
    val totalPending: Int get() = pendingOrder.size

    /** 消费队首的普通机会 */
    fun consumeOffer() {
        if (pendingOrder.firstOrNull() != ChoiceKind.PERK) return
        pendingOrder.removeAt(0)
        if (pendingOffers.isNotEmpty()) pendingOffers.removeAt(0)
        if (pendingOfferKinds.isNotEmpty()) pendingOfferKinds.removeAt(0)
    }

    /** 消费队首的特质机会 */
    fun consumeTraitChoice() {
        if (pendingOrder.firstOrNull() == ChoiceKind.TRAIT) pendingOrder.removeAt(0)
    }

    /**
     * 移除最后 [count] 个普通词条机会, 并同步移除对应的候选和候选规则。
     * 特质机会与其余普通机会的先后顺序保持不变。
     *
     * @return 普通机会不足 [count] 时返回 false, 且不做任何修改
     */
    fun removeLastPerkChoices(count: Int): Boolean {
        if (count <= 0) return false
        val perkPositions = pendingOrder.indices.filter { pendingOrder[it] == ChoiceKind.PERK }
        if (perkPositions.size < count) return false

        for (orderIndex in perkPositions.takeLast(count).sortedDescending()) {
            val offerIndex = pendingOrder.subList(0, orderIndex).count { it == ChoiceKind.PERK }
            pendingOrder.removeAt(orderIndex)
            if (offerIndex in pendingOffers.indices) pendingOffers.removeAt(offerIndex)
            if (offerIndex in pendingOfferKinds.indices) pendingOfferKinds.removeAt(offerIndex)
        }
        return true
    }

    fun reset() {
        pendingOffers.clear()
        pendingOfferKinds.clear()
        pendingOrder.clear()
        ownedPerks.clear()
        usedPerks.clear()
        useCounts.clear()
        cooldowns.clear()
        memory.clear()
    }
}

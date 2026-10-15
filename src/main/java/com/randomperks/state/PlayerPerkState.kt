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

    /** 已获得的词条 (本局内不可重复) */
    val ownedPerks: MutableList<PerkDefinition> = mutableListOf()

    /** 主动技能/触发效果的冷却到期时间 (游戏 tick 时间) */
    val cooldowns: MutableMap<String, Long> = mutableMapOf()

    /** 词条自定义数据 (如"猎杀印记"记录的目标 UUID、偷窃目标等) */
    val memory: MutableMap<String, String> = mutableMapOf()

    /**
     * "风之翼"绑定鞘翅的死亡暂存: 死亡时从胸甲槽取下保留 (不掉落), 重生时原样装回。
     * 局间随状态清空。
     */
    var savedChestItem: net.minecraft.item.ItemStack? = null

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
        cooldowns.clear()
        memory.clear()
        savedChestItem = null
    }
}

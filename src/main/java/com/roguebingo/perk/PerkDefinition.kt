package com.roguebingo.perk

import com.roguebingo.state.PlayerPerkState
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity

/**
 * 词条上下文: 传给词条的生命周期回调, 提供玩家与状态访问。
 */
class PerkContext(
    val player: ServerPlayerEntity,
    val state: PlayerPerkState,
) {
    /** 该玩家当前拥有的、指定流派的词条数量 (用于流派联动) */
    fun countArchetype(archetype: PerkArchetype): Int =
        state.ownedPerks.count { it.archetype == archetype }
}

/**
 * 一个词条的定义。
 *
 * 效果通过生命周期回调实现 —— 四类效果 (属性强化 / 资源获取 / 信息辅助 / 特殊能力)
 * 都可以用这组回调表达。
 */
interface PerkDefinition {
    /** 唯一 id, 如 "rogue_bingo:swift_speed" */
    val id: String

    val rarity: PerkRarity

    val archetype: PerkArchetype

    /** 界面图标 */
    fun icon(): ItemStack

    /** 玩家获得该词条时 (立即生效的效果在这里应用) */
    fun onAcquire(ctx: PerkContext) {}

    /** 周期性驱动 (每 20 tick 一次) —— 持续/触发类效果 */
    fun onTick(ctx: PerkContext) {}

    /** 本队完成一个 bingo 格子时 */
    fun onTeamScored(ctx: PerkContext, objectiveId: String) {}

    /** 词条被移除 / 对局结束时清理 */
    fun onRemove(ctx: PerkContext) {}

    /** 玩家重生后重新应用 (属性类效果需要) */
    fun onRespawn(ctx: PerkContext) {}
}

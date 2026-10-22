package com.randomperks.perk

import com.randomperks.state.PlayerPerkState
import net.minecraft.block.BlockState
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos

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

    /**
     * 一次性词条: 获得后**不会**立即触发, 而是留在词条列表里等玩家主动使用。
     * 这类词条在 V 界面里未使用时闪烁, 点击后触发 [onActivate]。
     *
     * ★ 神话品质的词条**不允许**变成可贮藏的, 也不允许变成可点击使用的 ——
     * 它们必须获得瞬间强制生效 (在设计上神话词条是"拿到就改变游戏规则"的东西)。
     */
    val isOneTime: Boolean get() = false

    /** 一次性词条的触发入口 (由 V 界面点击调用; 普通词条不会走到这里) */
    fun onActivate(ctx: PerkContext) {}

    /**
     * 该词条当前是否可用。
     *
     * 依赖外部 mod 的词条 (例如「开摆」需要 Nature's Compass / Explorer's Compass)
     * 在依赖缺失时返回 false —— 它会被排除在抽取池与百科之外, 不会出现在任何候选里。
     */
    fun isAvailable(): Boolean = true

    /** 玩家获得该词条时 (立即生效的效果在这里应用) */
    fun onAcquire(ctx: PerkContext) {}

    /** 周期性驱动 (每 20 tick 一次) —— 持续/触发类效果 */
    fun onTick(ctx: PerkContext) {}

    /** 本队完成一个 bingo 格子时 */
    fun onTeamScored(ctx: PerkContext, objectiveId: String) {}

    /** 该玩家击杀了一个生物 */
    fun onKill(ctx: PerkContext, victim: LivingEntity) {}

    /** 该玩家破坏了一个方块 */
    fun onBlockBreak(ctx: PerkContext, pos: BlockPos, blockState: BlockState) {}

    /** 装备槽变化时 (穿/脱护甲等) —— 用于即时重算装备相关效果 */
    fun onEquipmentChange(ctx: PerkContext) {}

    /** **吃完一份食物**时 (由 ConsumableBoostMixin 注入原版 ConsumableComponent.finishConsumption 触发;
     *  此时饥饿值已按食物结算完毕) */
    fun onFoodEaten(ctx: PerkContext) {}

    /** 该玩家的词条列表发生变化后 (获得/移除任一词条) —— 供"同流派联动"类词条即时重算 */
    fun onPerkListChanged(ctx: PerkContext) {}

    /** 词条被移除 / 对局结束时清理 */
    fun onRemove(ctx: PerkContext) {}

    /** 玩家重生后重新应用 (属性类效果需要) */
    fun onRespawn(ctx: PerkContext) {}
}

package com.randomperks.game

import java.util.UUID

/**
 * "变群友" 的状态表: 处于变猪状态的**玩家 UUID** 集合。
 *
 * 放在 main 源集里让两端共用同一份实现:
 *  - 服务端按"玩家是否持有词条"维护 ([PigMorphService]);
 *  - 客户端收到 `MorphS2C` 时同步同一份集合。
 *
 * 为什么两端都要记: 碰撞箱 (`getBaseDimensions`) 是**服务端权威 + 客户端预测**的东西,
 * 只改一端会让客户端预测的走位被服务端拉回来 (橡皮筋)。眼睛高度则是纯客户端表现。
 *
 * ★ 为什么用 UUID 而不是实体 id:
 *   服务端重生会创建**新的 ServerPlayerEntity**(新实体 id); 客户端重生时却会复用同一个
 *   ClientPlayerEntity 并把 id 设回旧值。用实体 id 记录时, 服务端广播的新 id 对不上
 *   客户端本地玩家的旧 id, 重生后视角/模型就会"重置"。玩家 UUID 则跨重生保持不变。
 *
 * 注意: 变形的是玩家**自己** (词条效果), 所以这里只会出现玩家 UUID ——
 * 渲染侧因此可以放心地把它当作玩家处理。
 */
object MorphState {

    /**
     * 变猪期间的碰撞箱宽度 —— 沿用玩家自己的 0.6。
     *
     * 不换成原版猪的 0.9: 横向变宽只会更容易卡在柱子/门框之间, 而这个词条的
     * 卖点是"钻 1 格高的缝", 与宽度无关。
     */
    const val PIG_WIDTH = 0.6f

    /** 碰撞箱高度 = 原版猪的 0.9 格 → 可以站进 1 格高的空间 */
    const val PIG_HEIGHT = 0.9f

    /** 眼睛高度 —— 和原版 EntityDimensions.fixed 的取法一致 (高度的 85%), 0.765 格 */
    const val PIG_EYE_HEIGHT = 0.765f

    private val morphed = mutableSetOf<UUID>()

    /** 该玩家此刻是否处于变猪状态 */
    @JvmStatic
    fun isMorphed(playerId: UUID): Boolean = playerId in morphed

    @JvmStatic
    fun setMorphed(playerId: UUID, value: Boolean) {
        if (value) morphed.add(playerId) else morphed.remove(playerId)
    }

    /** 当前所有变猪玩家的 UUID 快照 (服务端清理已下线玩家用) */
    @JvmStatic
    fun players(): Set<UUID> = morphed.toSet()

    fun clear() {
        morphed.clear()
    }
}

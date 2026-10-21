package com.randomperks.game

/**
 * "变群友" 的状态表: 处于变猪状态的**玩家实体 id** 集合。
 *
 * 放在 main 源集里让两端共用同一份实现:
 *  - 服务端按"玩家是否持有词条"维护 ([PigMorphService]);
 *  - 客户端收到 `MorphS2C` 时同步同一份集合。
 *
 * 为什么两端都要记: 碰撞箱 (`getBaseDimensions`) 是**服务端权威 + 客户端预测**的东西,
 * 只改一端会让客户端预测的走位被服务端拉回来 (橡皮筋)。眼睛高度则是纯客户端表现。
 *
 * 注意: 变形的是玩家**自己** (词条效果), 所以这里只会出现玩家实体 id ——
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

    private val morphed = mutableSetOf<Int>()

    /** 该玩家此刻是否处于变猪状态 */
    @JvmStatic
    fun isMorphed(entityId: Int): Boolean = entityId in morphed

    @JvmStatic
    fun setMorphed(entityId: Int, value: Boolean) {
        if (value) morphed.add(entityId) else morphed.remove(entityId)
    }

    /** 当前所有变猪实体的 id 快照 (服务端清理失效 id 用) */
    @JvmStatic
    fun ids(): Set<Int> = morphed.toSet()

    fun clear() {
        morphed.clear()
    }
}

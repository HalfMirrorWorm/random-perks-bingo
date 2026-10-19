package com.randomperks.game

/**
 * "变群友" 的状态表: 实体 id → 结束时刻 (毫秒)。
 *
 * 放在 main 源集里让**两端共用同一份实现**:
 *  - 服务端在 [MorphService] 触发变形时写入 (它自己就是发起方);
 *  - 客户端在收到 `MorphS2C` 时写入。
 *
 * 为什么两端都要记: 碰撞箱 (`getBaseDimensions`) 是**服务端权威 + 客户端预测**的东西,
 * 只改一端会让客户端预测的走位被服务端拉回来 (橡皮筋)。两端各自计时, 结果一致。
 *
 * 到点自动失效, 不需要"结束包"; [consumeExpired] 供碰撞箱刷新使用。
 */
object MorphState {

    /**
     * 变猪期间的碰撞箱 —— 沿用 sparktraits 的取值。
     *
     * 宽度特意保持玩家自己的 0.6 不变: 换成原版猪的宽度, 玩家可能在变回来的那一瞬间
     * 正好卡在"猪能站、玩家站不下"的位置。高度压到 1.4, 于是变猪期间能钻进
     * 玩家过不去的 1.5 格高的缝 —— 这是这个效果唯一的实际收益。
     */
    const val PIG_WIDTH = 0.6f
    const val PIG_HEIGHT = 1.4f

    /** 眼睛高度 —— 和原版 EntityDimensions.fixed 的取法一致 (高度的 85%) */
    const val PIG_EYE_HEIGHT = PIG_HEIGHT * 0.85f

    private val active = mutableMapOf<Int, Long>()

    /** 刚过期、但还没刷新过碰撞箱的实体 */
    private val expired = mutableSetOf<Int>()

    /** 记录 / 续期一次"变猪" */
    @JvmStatic
    fun mark(entityId: Int, durationMillis: Long) {
        expired.remove(entityId)
        active[entityId] = System.currentTimeMillis() + durationMillis
    }

    /** 该实体此刻是否处于变猪状态 (顺手清掉过期项) */
    @JvmStatic
    fun isMorphed(entityId: Int): Boolean {
        val end = active[entityId] ?: return false
        if (System.currentTimeMillis() >= end) {
            active.remove(entityId)
            expired.add(entityId)
            return false
        }
        return true
    }

    /**
     * 变形是否**刚刚结束** (取走即清)。
     *
     * 拿到 true 的调用方应当 `calculateDimensions()` 把 boundingBox 恢复成原样 ——
     * `getBaseDimensions` 的返回值变了并不会自动更新已经缓存的碰撞箱。
     */
    @JvmStatic
    fun consumeExpired(entityId: Int): Boolean = expired.remove(entityId)

    fun clear() {
        active.clear()
        expired.clear()
    }
}

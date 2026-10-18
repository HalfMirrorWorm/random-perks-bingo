package com.randomperks.client

/**
 * 客户端侧的"大雪球"集合: 实体 id → 过期时刻 (毫秒)。
 *
 * 服务端的 commandTag 不同步给客户端, 所以"哪些雪球是变大的"只能靠
 * [com.randomperks.network.BigSnowballS2C] 单独通知。雪球寿命只有几秒,
 * 用一个宽裕的过期时间兜底即可, 不需要在实体卸载时精确回收。
 */
object BigSnowballState {

    /** 过期时间 (毫秒) —— 比雪球最长飞行时间宽裕得多 */
    private const val LIFETIME_MILLIS = 30_000L

    private val active = mutableMapOf<Int, Long>()

    fun accept(entityId: Int) {
        active[entityId] = System.currentTimeMillis() + LIFETIME_MILLIS
    }

    /** 该投掷物此刻是否是"大雪球" (顺手清掉过期项) */
    @JvmStatic
    fun isBig(entityId: Int): Boolean {
        val end = active[entityId] ?: return false
        if (System.currentTimeMillis() >= end) {
            active.remove(entityId)
            return false
        }
        return true
    }

    fun clear() = active.clear()
}

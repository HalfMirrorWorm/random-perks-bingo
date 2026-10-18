package com.randomperks.client

/**
 * 客户端侧的"变猪"状态: 实体 id → 结束时刻 (毫秒)。
 *
 * 由 [com.randomperks.network.MorphS2C] 写入; 渲染器 (PigMorphRenderer) 与尺寸伪装
 * (MorphSizeMixin) 都从这里查询。服务端只负责"通知", 到点自动失效, 不需要结束包。
 */
object MorphClientState {

    private val active = mutableMapOf<Int, Long>()

    /** 收到一次"变猪"通知 */
    fun accept(entityId: Int, durationMillis: Long) {
        active[entityId] = System.currentTimeMillis() + durationMillis
    }

    /** 该实体此刻是否处于变猪状态 (顺手清掉过期项) */
    @JvmStatic
    fun isMorphed(entityId: Int): Boolean {
        val end = active[entityId] ?: return false
        if (System.currentTimeMillis() >= end) {
            active.remove(entityId)
            return false
        }
        return true
    }

    fun clear() = active.clear()
}

package com.randomperks.client

import java.util.concurrent.ConcurrentHashMap

/**
 * 客户端的"发光标记"状态 (由 GlowMarkS2C 驱动)。
 *
 * 服务端只把标记推送给同队玩家, 因此发光轮廓是**团队可见**而非全局可见。
 * 本类放在主源集 (无客户端依赖), 供 EntityGlowMixin (client 段) 查询。
 */
object GlowMarkState {

    /** 实体 id -> 标记过期时间 (epoch millis) */
    private val expiries = ConcurrentHashMap<Int, Long>()

    fun mark(entityIds: Collection<Int>, durationMillis: Long) {
        val until = System.currentTimeMillis() + durationMillis
        entityIds.forEach { expiries[it] = until }
    }

    /** 该实体当前是否应显示为发光 (过期自动清理) */
    fun isMarked(entityId: Int): Boolean {
        val until = expiries[entityId] ?: return false
        if (System.currentTimeMillis() < until) return true
        expiries.remove(entityId, until)
        return false
    }

    /** 断开连接/换世界时清理 */
    fun clear() {
        expiries.clear()
    }
}

package com.randomperks.game

import com.randomperks.network.MorphS2C
import com.randomperks.network.PerkNetworking
import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/**
 * "变群友" (金 · 猎杀) 的服务端逻辑。
 *
 * 攻击命中 → 给目标打一个"变猪"标记并广播给所有客户端。渲染与尺寸伪装**全部在客户端完成**
 * (服务端不改实体的任何状态), 所以被变的玩家/生物行为照常, 只是"看起来"是猪 ——
 * 这样既不会丢实体数据, 也不会因为替换实体而破坏对局状态。
 */
object MorphService {

    /** 变形持续 2 秒 */
    const val DURATION_MILLIS = 2000L

    /** 冷却 8 秒 */
    private const val COOLDOWN_TICKS = 8 * 20

    /** 攻击者 uuid → 冷却结束 tick (玩家数量有限, 不清理) */
    private val cooldowns = mutableMapOf<UUID, Long>()

    /** 攻击命中时尝试把目标变成猪; 返回是否真的触发了 */
    fun tryApply(attacker: ServerPlayerEntity, target: LivingEntity): Boolean {
        if (!PerkSession.isEnabled()) return false
        if (!PerkBoostQuery.hasPigMorph(attacker)) return false
        if (target === attacker) return false
        if (!target.isAlive) return false

        val server = attacker.server ?: return false
        val now = server.ticks.toLong()
        if (now < (cooldowns[attacker.uuid] ?: 0L)) return false
        cooldowns[attacker.uuid] = now + COOLDOWN_TICKS

        // 广播给所有客户端 —— 谁看得见这只猪由各自的实体追踪决定, 这里不做筛选
        val payload = MorphS2C(target.id, DURATION_MILLIS)
        for (player in server.playerManager.playerList) {
            PerkNetworking.sendMorph(player, payload)
        }
        return true
    }
}

package com.randomperks.game

import com.randomperks.network.MorphS2C
import com.randomperks.network.PerkNetworking
import net.minecraft.entity.LivingEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import java.util.UUID

/**
 * "变群友" (金 · 猎杀) 的服务端逻辑。
 *
 * 攻击命中 → 给目标打一个"变猪"标记并广播给所有客户端。**服务端不改实体的任何持久状态**
 * (不换 EntityType、不存组件), 被变的玩家/生物行为照常, 只是"看起来"是猪 ——
 * 这样既不会丢实体数据, 也不会因为替换实体而破坏对局状态。
 *
 * 只有两样东西两端都要改, 所以 [MorphState] 两端各记一份:
 *  - 碰撞箱 (客户端预测 / 服务端权威必须一致, 否则走位会被拉回来);
 *  - 音效 (受伤死亡的声音是各自本地播的)。
 * 渲染则纯属客户端。
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

        // 服务端自己先记一份状态 —— 碰撞箱要靠它, 不能只指望客户端收包
        MorphState.mark(target.id, DURATION_MILLIS)
        // getBaseDimensions 的返回值变了并不会自动更新已缓存的 boundingBox, 得主动重算
        target.calculateDimensions()

        // 变形的那一瞬间"哼"一声 (参考 sparktraits: 用猪的环境音)
        target.world.playSound(
            null,
            target.x, target.y, target.z,
            SoundEvents.ENTITY_PIG_AMBIENT,
            SoundCategory.NEUTRAL,
            1.0f,
            0.8f + target.random.nextFloat() * 0.4f,
        )

        // 广播给所有客户端 —— 谁看得见这只猪由各自的实体追踪决定, 这里不做筛选
        val payload = MorphS2C(target.id, DURATION_MILLIS)
        for (player in server.playerManager.playerList) {
            PerkNetworking.sendMorph(player, payload)
        }
        return true
    }
}

package com.randomperks.game

import com.randomperks.effect.PermanentEffects
import com.randomperks.state.PerkStateManager
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * 清理"跨局残留"的属性修饰符与无限药水效果。
 *
 * <p>对局结束时 [com.randomperks.lifecycle.BingoLifecycle.clearAllPerkState] 只会对**在线**玩家
 * 执行 `onRemove`；如果某人当时离线，`PerkEffects.setAttribute` 写入玩家存档的永久修饰符
 * （以及无限时长的词条效果）会留到下一局。这里在玩家加入、开局/重置时按"当前没有任何词条"
 * 的前提兜底清除。
 */
object PerkOrphanCleanup {

    private const val NAMESPACE = "random-perks-bingo"

    /** mod 会写进玩家属性的全部属性 (见 PerkEffects.setAttribute 的调用点) */
    private val ATTRIBUTES = listOf(
        Attributes.MAX_HEALTH,
        Attributes.MOVEMENT_SPEED,
        Attributes.BLOCK_BREAK_SPEED,
        Attributes.ATTACK_DAMAGE,
        Attributes.ARMOR,
        Attributes.KNOCKBACK_RESISTANCE,
        Attributes.JUMP_STRENGTH,
        Attributes.FALL_DAMAGE_MULTIPLIER,
        Attributes.BLOCK_INTERACTION_RANGE,
        Attributes.ENTITY_INTERACTION_RANGE,
    )

    /** 玩家加入: 身上还有词条时不动，避免误删正在进行中的效果 */
    fun onJoin(player: ServerPlayer) {
        val state = PerkStateManager.get(player.uuid)
        if (state != null && state.ownedPerks.isNotEmpty()) return
        clean(player)
    }

    /** 强制清理 (开局/重置时所有玩家都已经没有词条) */
    fun clean(player: ServerPlayer) {
        for (attribute in ATTRIBUTES) {
            val instance = player.getAttribute(attribute) ?: continue
            // getModifiers() 同时包含 permanent (存档) 与 transient (不存档)，
            // 疾跑类词条的临时修饰符在重置后也可能残留，一并摘掉
            for (modifier in instance.modifiers.toList()) {
                if (modifier.id.namespace == NAMESPACE) {
                    instance.removeModifier(modifier.id)
                }
            }
        }
        PermanentEffects.removeAllOrphans(player)
        // 大背包的 >64 堆叠同理: 离线跨局时 onRemove 没跑到, 加入时拆回合法数量
        BigBackpackService.normalize(player)
    }
}

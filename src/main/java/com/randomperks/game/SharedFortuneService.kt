package com.randomperks.game

import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.Holder
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import java.util.UUID

/**
 * 「有福同享」的范围光环。
 *
 * <ul>
 *   <li>把施法者身上的**所有**药水效果（含永久、含负面）复制给 256 格内同队、同维度的队友；</li>
 *   <li>每 tick 检查队伍 / 维度 / 距离 / 存活，失效时立刻移除**本光环施加的**效果；</li>
 *   <li>不会覆盖队友身上更强（等级更高）或更长（剩余时间更久）的同类效果；</li>
 *   <li>共享效果用短时长不断刷新，光环断开后最多下一 tick 就被移除，不会永久留在队友身上。</li>
 * </ul>
 */
object SharedFortuneService {

    const val PERK_ID = "artisan_shared_fortune"

    private const val RANGE = 256.0

    /** 共享效果的刷新时长: 3 秒；每 tick 检查，剩余不足 1 秒时续上 */
    private const val AURA_TICKS = 60
    private const val REFRESH_THRESHOLD = AURA_TICKS - 20

    private data class Applied(
        val amplifier: Int,
        val duration: Int,
    )

    /** source UUID → (target UUID → (effect → 我们最后施加的实例)) */
    private val tracked = mutableMapOf<UUID, MutableMap<UUID, MutableMap<Holder<MobEffect>, Applied>>>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server -> tick(server) }
    }

    /** 玩家失去词条 / 被移除时调用 */
    fun removeSource(sourceId: UUID) {
        val targets = tracked.remove(sourceId) ?: return
        val server = ServerHolder.server ?: return
        for ((targetId, effects) in targets) {
            val target = server.playerList.getPlayer(targetId) ?: continue
            for ((effect, applied) in effects) {
                removeIfOurs(target, effect, applied)
            }
        }
    }

    /** 对局重置时调用 */
    fun clear() {
        for (sourceId in tracked.keys.toList()) {
            removeSource(sourceId)
        }
    }

    private fun tick(server: MinecraftServer) {
        val sources = server.playerList.players.filter { player ->
            player.isAlive &&
                !player.isSpectator &&
                PerkStateManager.get(player.uuid)?.hasPerk(PERK_ID) == true
        }
        val sourceIds = sources.mapTo(mutableSetOf()) { it.uuid }

        // 施法者下线 / 死亡 / 失去词条 → 立刻移除他共享出去的效果
        for (sourceId in tracked.keys.toList()) {
            if (sourceId !in sourceIds) removeSource(sourceId)
        }
        if (sources.isEmpty()) return

        for (source in sources) {
            val sourceTrack = tracked.getOrPut(source.uuid) { mutableMapOf() }
            val sourceEffects = source.activeEffects.associateBy { it.effect }
            val validTargets = mutableSetOf<UUID>()

            for (target in server.playerList.players) {
                if (!isValidTarget(source, target)) continue
                validTargets += target.uuid
                val targetTrack = sourceTrack.getOrPut(target.uuid) { mutableMapOf() }

                // 施法者已经没有这个效果了 → 移除我们之前共享的
                for (effect in targetTrack.keys.toList()) {
                    if (effect in sourceEffects) continue
                    val applied = targetTrack.remove(effect) ?: continue
                    removeIfOurs(target, effect, applied)
                }

                for ((effect, instance) in sourceEffects) {
                    val duration = if (instance.isInfiniteDuration()) {
                        AURA_TICKS
                    } else {
                        minOf(instance.duration, AURA_TICKS)
                    }
                    if (duration <= 0) {
                        val applied = targetTrack.remove(effect)
                        if (applied != null) removeIfOurs(target, effect, applied)
                        continue
                    }

                    val current = target.getEffect(effect)
                    val applied = targetTrack[effect]
                    val ours = current != null && applied != null &&
                        current.amplifier == applied.amplifier &&
                        !current.isInfiniteDuration() &&
                        current.duration <= applied.duration + 1

                    if (current != null && !ours &&
                        isStrongerOrLonger(current, instance.amplifier, duration)
                    ) {
                        // 队友已经有更强/更长的同类效果: 不覆盖，也不再声称这个效果是我们的
                        targetTrack.remove(effect)
                        continue
                    }

                    if (ours) {
                        // 有限时长的效果在复制时已经用了源效果的剩余时长，跟着一起到期即可；
                        // 只有源效果是永久时才需要不断续期。
                        if (!instance.isInfiniteDuration()) continue
                        if (current.duration > REFRESH_THRESHOLD) continue
                    }

                    // 如果当前是我们自己刷的、但时长/等级需要变化，先摘掉再重新施加，
                    // 否则原版 addEffect 不会把效果缩短
                    if (ours) {
                        target.removeEffect(effect)
                    }

                    val aura = MobEffectInstance(
                        effect,
                        duration,
                        instance.amplifier,
                        instance.isAmbient,
                        instance.isVisible,
                        instance.showIcon(),
                    )
                    if (target.addEffect(aura)) {
                        targetTrack[effect] = Applied(instance.amplifier, duration)
                    } else {
                        targetTrack.remove(effect)
                    }
                }
            }

            // 队友离开范围 / 换队 / 换维度 / 死亡 → 立刻移除我们共享给他的效果
            for (targetId in sourceTrack.keys.toList()) {
                if (targetId in validTargets) continue
                val effects = sourceTrack.remove(targetId) ?: continue
                val target = server.playerList.getPlayer(targetId) ?: continue
                for ((effect, applied) in effects) {
                    removeIfOurs(target, effect, applied)
                }
            }

            if (sourceTrack.isEmpty()) tracked.remove(source.uuid)
        }
    }

    private fun isValidTarget(source: ServerPlayer, target: ServerPlayer): Boolean {
        if (target === source) return false
        if (!target.isAlive || target.isSpectator) return false
        if (target.level() !== source.level()) return false

        val team = source.team?.name ?: return false
        if (target.team?.name != team) return false

        return target.distanceToSqr(source) <= RANGE * RANGE
    }

    /** 队友身上的效果是否比我们将要施加的更强或更长 */
    private fun isStrongerOrLonger(current: MobEffectInstance, amplifier: Int, duration: Int): Boolean {
        if (current.amplifier > amplifier) return true
        if (current.isInfiniteDuration()) return true
        if (current.duration > duration) return true
        return false
    }

    /** 只移除确实由我们施加的那个实例，避免误删队友自己/其他来源的更强更长效果 */
    private fun removeIfOurs(target: ServerPlayer, effect: Holder<MobEffect>, applied: Applied) {
        val current = target.getEffect(effect) ?: return
        if (current.amplifier != applied.amplifier) return
        if (current.isInfiniteDuration()) return
        if (current.duration > applied.duration) return
        target.removeEffect(effect)
    }
}

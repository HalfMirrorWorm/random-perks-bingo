package com.randomperks.game

import com.randomperks.effect.PerkEffects
import com.randomperks.server.ServerHolder
import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.Attributes
import java.util.UUID

/**
 * "共鸣" (金 · 唤法) 的共享血池。
 *
 * 两名玩家的最大生命相加作为共享上限, 当前生命始终取同一个值:
 *  - 任意一方受伤/回血, 另一边同步变化;
 *  - 一方死亡 → 共鸣断裂, 另一方也死亡 (同生共死);
 *  - 解除后各自恢复原本的生命上限, 当前生命夹回自己的上限。
 *
 * 用**永久**属性修饰符 (随玩家 NBT 保存) 抬高双方上限; 服务器重启导致链接丢失时,
 * 玩家加入时由 [cleanupOrphan] 摘掉残留修饰符, 不会留下超大的生命上限。
 */
object ResonanceService {

    const val PERK_ID = "summon_resonance"

    private val HEALTH_MOD = Identifier.fromNamespaceAndPath("random-perks-bingo", "resonance_health")

    private class Link(
        val initiator: UUID,
        val partner: UUID,
        var sharedHealth: Double,
        var sharedMax: Double,
    )

    private val links = mutableListOf<Link>()

    fun isLinked(uuid: UUID): Boolean = links.any { it.initiator == uuid || it.partner == uuid }

    private fun linkOf(uuid: UUID): Link? =
        links.firstOrNull { it.initiator == uuid || it.partner == uuid }

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server -> tick(server) }

        // 同生共死: 一方死亡, 另一方跟着死亡
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (entity is ServerPlayer) onDeath(entity)
        }

        // 离线就解除 (避免在线一方一直挂着被抬高的生命上限)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            unlinkPlayer(handler.player.uuid, true)
        }

        // 服务器崩溃/重启后链接已丢失, 摘掉玩家 NBT 里残留的修饰符
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            cleanupOrphan(handler.player)
        }
    }

    /** 建立共鸣; 任何条件不满足返回 false */
    fun link(initiator: ServerPlayer, target: ServerPlayer): Boolean {
        if (initiator === target) return false
        if (isLinked(initiator.uuid) || isLinked(target.uuid)) return false
        if (!initiator.isAlive || !target.isAlive) return false
        if (initiator.isSpectator || target.isSpectator) return false

        val team = initiator.team?.name ?: return false
        if (target.team?.name != team) return false

        // 正常不会残留, 保险起见先清一次
        PerkEffects.removeAttribute(initiator, Attributes.MAX_HEALTH, HEALTH_MOD)
        PerkEffects.removeAttribute(target, Attributes.MAX_HEALTH, HEALTH_MOD)

        val baseA = initiator.maxHealth.toDouble()
        val baseB = target.maxHealth.toDouble()
        val sharedMax = baseA + baseB

        // 各自 + 对方的基础生命 → 两人的上限都变成 baseA + baseB
        PerkEffects.setAttribute(initiator, Attributes.MAX_HEALTH, HEALTH_MOD, baseB)
        PerkEffects.setAttribute(target, Attributes.MAX_HEALTH, HEALTH_MOD, baseA)

        val sharedHealth = (initiator.health.toDouble() + target.health.toDouble()).coerceIn(0.0, sharedMax)
        val link = Link(initiator.uuid, target.uuid, sharedHealth, sharedMax)
        links.add(link)

        initiator.setHealth(sharedHealth.toFloat())
        target.setHealth(sharedHealth.toFloat())

        initiator.sendSystemMessage(
            Component.translatable("random-perks-bingo.message.resonance_linked", target.name),
            false,
        )
        target.sendSystemMessage(
            Component.translatable("random-perks-bingo.message.resonance_linked", initiator.name),
            false,
        )
        return true
    }

    fun unlinkPlayer(uuid: UUID, notify: Boolean) {
        val link = linkOf(uuid) ?: return
        unlink(link, notify)
    }

    fun clear() {
        for (link in links.toList()) {
            unlink(link, false)
        }
    }

    private fun tick(server: MinecraftServer) {
        if (links.isEmpty()) return

        val refreshMax = server.tickCount % 20 == 0
        for (link in links.toList()) {
            val a = server.playerList.getPlayer(link.initiator) ?: continue
            val b = server.playerList.getPlayer(link.partner) ?: continue

            // 发起者必须仍持有词条; 队伍变化或有人旁观/死亡则解除
            if (PerkStateManager.get(a.uuid)?.hasPerk(PERK_ID) != true ||
                a.isSpectator || b.isSpectator ||
                a.team?.name == null || a.team?.name != b.team?.name
            ) {
                unlink(link, true)
                continue
            }

            if (a.isDeadOrDying || b.isDeadOrDying || a.health <= 0f || b.health <= 0f) {
                unlink(link, false)
                kill(a)
                kill(b)
                continue
            }

            // 每秒重算一次共享上限 (纹饰护体 / SMP玩家 等会改变基础生命)
            // 不 remove 修饰符再读 maxHealth —— 那会瞬时压低上限、把当前生命夹掉,
            // 下面的同步公式会把这当成伤害导致误死。这里直接减去我们自己那份修饰符。
            if (refreshMax) {
                val modA = a.getAttribute(Attributes.MAX_HEALTH)?.getModifier(HEALTH_MOD)?.amount ?: 0.0
                val modB = b.getAttribute(Attributes.MAX_HEALTH)?.getModifier(HEALTH_MOD)?.amount ?: 0.0
                val baseA = a.maxHealth.toDouble() - modA
                val baseB = b.maxHealth.toDouble() - modB
                val sharedMax = baseA + baseB

                if (modA != baseB || modB != baseA) {
                    PerkEffects.setAttribute(a, Attributes.MAX_HEALTH, HEALTH_MOD, baseB)
                    PerkEffects.setAttribute(b, Attributes.MAX_HEALTH, HEALTH_MOD, baseA)
                }
                link.sharedMax = sharedMax
                link.sharedHealth = link.sharedHealth.coerceIn(0.0, sharedMax)
            }

            // 双方都从 sharedHealth 出发: 两边变化量相加就是共享血池的新值
            val aHealth = a.health.toDouble()
            val bHealth = b.health.toDouble()
            val newHealth = (aHealth + bHealth - link.sharedHealth).coerceIn(0.0, link.sharedMax)

            if (newHealth <= 0.0) {
                unlink(link, false)
                kill(a)
                kill(b)
                continue
            }

            link.sharedHealth = newHealth
            if (aHealth != newHealth) a.setHealth(newHealth.toFloat())
            if (bHealth != newHealth) b.setHealth(newHealth.toFloat())
        }
    }

    /** 死亡事件: 先断开链接, 再让另一方一起死 (避免递归) */
    private fun onDeath(player: ServerPlayer) {
        val link = linkOf(player.uuid) ?: return
        val partnerId = if (link.initiator == player.uuid) link.partner else link.initiator
        unlink(link, false)

        val partner = ServerHolder.server?.playerList?.getPlayer(partnerId) ?: return
        kill(partner)
    }

    private fun kill(player: ServerPlayer) {
        if (!player.isAlive) return
        val level = player.level() as? ServerLevel ?: return
        player.kill(level)
    }

    private fun unlink(link: Link, notify: Boolean) {
        if (!links.remove(link)) return

        val server = ServerHolder.server
        val a: ServerPlayer? = server?.playerList?.getPlayer(link.initiator)
        val b: ServerPlayer? = server?.playerList?.getPlayer(link.partner)

        if (a != null) {
            PerkEffects.removeAttribute(a, Attributes.MAX_HEALTH, HEALTH_MOD)
            clampHealth(a)
        }
        if (b != null) {
            PerkEffects.removeAttribute(b, Attributes.MAX_HEALTH, HEALTH_MOD)
            clampHealth(b)
        }

        if (notify) {
            a?.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.resonance_unlinked", b?.name ?: Component.empty()),
                false,
            )
            b?.sendSystemMessage(
                Component.translatable("random-perks-bingo.message.resonance_unlinked", a?.name ?: Component.empty()),
                false,
            )
        }
    }

    /** 服务器重启后链接已不存在, 但永久修饰符还在玩家身上 —— 加入时摘掉 */
    private fun cleanupOrphan(player: ServerPlayer) {
        if (isLinked(player.uuid)) return
        val attribute = player.getAttribute(Attributes.MAX_HEALTH) ?: return
        if (attribute.getModifier(HEALTH_MOD) == null) return

        PerkEffects.removeAttribute(player, Attributes.MAX_HEALTH, HEALTH_MOD)
        clampHealth(player)
    }

    private fun clampHealth(player: ServerPlayer) {
        if (player.health > player.maxHealth) {
            player.setHealth(player.maxHealth)
        }
    }
}

package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.UUID

/**
 * "何易位" 的延迟结算: 选定两名玩家后先倒计时 10 秒, 再交换位置。
 *
 * 倒计时期间每秒给施放者和两名目标发 actionbar 提示 (含施放者名字);
 * 结算时重新校验在线/存活/同维度/天启日免疫。
 */
object SwapService {

    const val PERK_ID = "summon_swap"

    private const val DELAY_TICKS = 10 * 20L
    private const val NOTIFY_INTERVAL_TICKS = 20L

    private data class PendingSwap(
        val caster: UUID,
        val casterName: String,
        val first: UUID,
        val firstName: String,
        val second: UUID,
        val secondName: String,
        val endTick: Long,
        var nextNotifyTick: Long,
    )

    /** 施放者 uuid → 进行中的交换 */
    private val pending = mutableMapOf<UUID, PendingSwap>()

    fun register() {
        ServerTickEvents.START_SERVER_TICK.register { server ->
            if (pending.isEmpty()) return@register
            val now = server.ticks.toLong()

            for (swap in pending.values.toList()) {
                if (now >= swap.endTick) {
                    pending.remove(swap.caster)
                    execute(server, swap)
                } else if (now >= swap.nextNotifyTick) {
                    swap.nextNotifyTick = now + NOTIFY_INTERVAL_TICKS
                    val seconds = ((swap.endTick - now + 19) / 20).toInt()
                    notifyCountdown(server, swap, seconds)
                }
            }
        }
    }

    /** 是否有进行中的何易位 (防止重复施放) */
    fun hasPending(caster: UUID): Boolean = pending.containsKey(caster)

    /** 开始 10 秒倒计时; 已在倒计时中返回 false */
    fun start(caster: ServerPlayerEntity, first: ServerPlayerEntity, second: ServerPlayerEntity): Boolean {
        if (pending.containsKey(caster.uuid)) return false
        val now = caster.server?.ticks?.toLong() ?: return false

        val swap = PendingSwap(
            caster = caster.uuid,
            casterName = caster.name.string,
            first = first.uuid,
            firstName = first.name.string,
            second = second.uuid,
            secondName = second.name.string,
            endTick = now + DELAY_TICKS,
            nextNotifyTick = now + NOTIFY_INTERVAL_TICKS,
        )
        pending[caster.uuid] = swap

        caster.sendMessage(
            Text.translatable(
                "random-perks-bingo.message.swap_started",
                swap.firstName,
                swap.secondName,
            ),
            false,
        )
        if (first.uuid != caster.uuid) {
            first.sendMessage(
                Text.translatable(
                    "random-perks-bingo.message.swap_countdown_start",
                    swap.casterName,
                    swap.secondName,
                ),
                false,
            )
        }
        if (second.uuid != caster.uuid) {
            second.sendMessage(
                Text.translatable(
                    "random-perks-bingo.message.swap_countdown_start",
                    swap.casterName,
                    swap.firstName,
                ),
                false,
            )
        }

        notifyCountdown(caster.server ?: return true, swap, 10)
        return true
    }

    fun clear() = pending.clear()

    private fun notifyCountdown(server: MinecraftServer, swap: PendingSwap, seconds: Int) {
        val message = Text.translatable(
            "random-perks-bingo.message.swap_countdown",
            seconds,
            swap.firstName,
            swap.secondName,
            swap.casterName,
        )
        recipients(server, swap).forEach { it.sendMessage(message, true) }
    }

    private fun execute(server: MinecraftServer, swap: PendingSwap) {
        val a = server.playerManager.getPlayer(swap.first)
        val b = server.playerManager.getPlayer(swap.second)

        if (a == null || b == null || a === b ||
            !a.isAlive || !b.isAlive || a.isSpectator || b.isSpectator ||
            PerkBoostQuery.hasDoomsday(a) || PerkBoostQuery.hasDoomsday(b)
        ) {
            notifyCancelled(server, swap)
            return
        }

        // 骑着东西的话先下来, 否则载具留在原地、人却飞走了
        a.stopRiding()
        b.stopRiding()

        val worldA = a.world
        val worldB = b.world
        val posA = a.pos
        val yawA = a.yaw
        val pitchA = a.pitch
        val posB = b.pos
        val yawB = b.yaw
        val pitchB = b.pitch

        // 允许跨维度: 各自传送到对方所在维度里的坐标
        a.teleport(worldB, posB.x, posB.y, posB.z, emptySet(), yawA, pitchA, true)
        b.teleport(worldA, posA.x, posA.y, posA.z, emptySet(), yawB, pitchB, true)

        // 真正生效后才记为已用; 倒计时被取消则词条仍可重新使用
        PerkStateManager.get(swap.caster)?.markUsed(PERK_ID)
        server.playerManager.getPlayer(swap.caster)?.let {
            PerkGrantService.notifyPerkListChanged(it)
            PerkGrantService.syncAbilities(it)
        }

        val message = Text.translatable(
            "random-perks-bingo.message.swap_applied",
            swap.firstName,
            swap.secondName,
        )
        recipients(server, swap).forEach { it.sendMessage(message, false) }
    }

    private fun notifyCancelled(server: MinecraftServer, swap: PendingSwap) {
        val message = Text.translatable("random-perks-bingo.message.swap_cancelled")
        recipients(server, swap).forEach { it.sendMessage(message, false) }
    }

    /** 施放者 + 两名目标 (去重, 只含在线玩家) */
    private fun recipients(server: MinecraftServer, swap: PendingSwap): List<ServerPlayerEntity> =
        listOf(swap.caster, swap.first, swap.second)
            .distinct()
            .mapNotNull { server.playerManager.getPlayer(it) }
}

package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.UUID

/**
 * "何易位" (紫 · 唤法): 选择一名玩家, **5 秒倒计时后**传送到他的位置 (可跨维度)。
 *
 * <p>倒计时期间施放者与被选中的玩家都会在动作栏看到剩余秒数 —— 被选中的人有 5 秒反应时间。
 * 倒计时结束时若任意一方离线/死亡则取消 (词条**不消耗**); 只有传送成功才标记为已使用。
 * 词条本身是一次性的 (由 [com.randomperks.perk.PerkDefinition.isOneTime] 控制)。
 */
object SwapService {

    const val PERK_ID = "summon_swap"

    /** 倒计时时长 (tick): 5 秒 */
    private const val COUNTDOWN_TICKS = 100

    private class Pending(val targetUuid: UUID, var ticksLeft: Int)

    /** 施放者 uuid → 倒计时状态 */
    private val pending = mutableMapOf<UUID, Pending>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server -> tick(server) }
    }

    /**
     * 开始 5 秒倒计时并向双方发送提示。
     *
     * @return false 表示该施放者已经在倒计时中
     */
    fun startCountdown(caster: ServerPlayerEntity, target: ServerPlayerEntity): Boolean {
        if (pending.containsKey(caster.uuid)) return false

        pending[caster.uuid] = Pending(target.uuid, COUNTDOWN_TICKS)
        val seconds = COUNTDOWN_TICKS / 20
        caster.sendMessage(
            Text.translatable("random-perks-bingo.message.swap_started", seconds, target.name),
            false,
        )
        target.sendMessage(
            Text.translatable("random-perks-bingo.message.swap_countdown_start", caster.name, seconds),
            false,
        )
        return true
    }

    /** 该施放者是否正在倒计时中 */
    fun isCountingDown(caster: ServerPlayerEntity): Boolean = pending.containsKey(caster.uuid)

    private fun tick(server: MinecraftServer) {
        if (pending.isEmpty()) return

        for ((casterUuid, state) in pending.toList()) {
            val caster = server.playerManager.getPlayer(casterUuid)
            val target = server.playerManager.getPlayer(state.targetUuid)

            // 任意一方离线 / 死亡 → 取消 (不消耗词条)
            if (caster == null || target == null || !caster.isAlive || !target.isAlive || target.isSpectator) {
                caster?.sendMessage(Text.translatable("random-perks-bingo.message.swap_cancelled"), false)
                pending.remove(casterUuid)
                continue
            }

            state.ticksLeft--
            if (state.ticksLeft <= 0) {
                pending.remove(casterUuid)
                if (teleportTo(caster, target)) {
                    markUsed(caster)
                } else {
                    caster.sendMessage(Text.translatable("random-perks-bingo.message.swap_cancelled"), false)
                }
                continue
            }

            // 每秒刷新一次动作栏倒计时
            if (state.ticksLeft % 20 == 0) {
                val seconds = state.ticksLeft / 20
                caster.sendMessage(
                    Text.translatable("random-perks-bingo.message.swap_countdown_caster", seconds, target.name),
                    true,
                )
                target.sendMessage(
                    Text.translatable("random-perks-bingo.message.swap_countdown_target", seconds, caster.name),
                    true,
                )
            }
        }
    }

    /** 传送成功后才消耗一次性词条 */
    private fun markUsed(caster: ServerPlayerEntity) {
        val state = PerkStateManager.get(caster.uuid) ?: return
        state.markUsed(PERK_ID)
        PerkGrantService.notifyPerkListChanged(caster)
        PerkGrantService.syncAbilities(caster)
    }

    /**
     * 把施放者传送到目标当前所在的位置。
     *
     * @return true 表示传送成功
     */
    fun teleportTo(caster: ServerPlayerEntity, target: ServerPlayerEntity): Boolean {
        if (caster === target) return false
        if (!target.isAlive || target.isRemoved || target.isSpectator) return false

        // 骑着东西的话先下来, 否则载具会留在原地
        caster.stopRiding()

        caster.teleport(
            target.world,
            target.x, target.y, target.z,
            emptySet(),
            caster.yaw,
            caster.pitch,
            true,
        )

        caster.sendMessage(
            Text.translatable("random-perks-bingo.message.swap_teleported", target.name),
            false,
        )
        target.sendMessage(
            Text.translatable("random-perks-bingo.message.swap_arrived", caster.name),
            false,
        )
        return true
    }

    /** 对局重置: 清掉所有未结算的倒计时 */
    fun clear() {
        pending.clear()
    }
}

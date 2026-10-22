package com.randomperks.game

import com.randomperks.network.JockeyControlS2C
import com.randomperks.network.PerkNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.PlayerInput
import java.util.UUID

/**
 * "Jockey" (金 · 猎杀) 的服务端逻辑。
 *
 * 原版**不允许**把玩家当载具: `RideCommand` 见到玩家载具直接拒绝, 而
 * [net.minecraft.entity.Entity.startRiding] 里还有一道 `entity.type.isSaveable()` 检查
 * (`EntityType.PLAYER` 是 disableSaving 注册的, saveable = false) —— 且这道检查排在 force
 * 判定之前, 传 force = true 也绕不过去。放行由 {@link com.randomperks.mixin.JockeyRideMixin}
 * 负责, 这里只管调用。
 *
 * 之后骑乘者每 tick 上报自己的输入, 服务端原样转给被骑者。
 * 真正执行移动的是**被骑者的客户端**: 玩家位置由客户端权威计算, 服务端硬推位置会被
 * 对方下一 tick 的移动包顶掉, 所以只能让对面自己走。
 */
object JockeyService {

    /** 被骑者挣脱后, 骑乘者的冷却: 3 分钟 */
    private const val BREAK_FREE_COOLDOWN_TICKS = 3 * 60 * 20

    /** 骑乘者 uuid → 冷却结束 tick */
    private val cooldowns = mutableMapOf<UUID, Long>()

    /** 右键玩家 → 骑上去 (失败返回 false, 交还原版交互) */
    fun mount(rider: ServerPlayerEntity, target: ServerPlayerEntity): Boolean {
        if (rider.uuid == target.uuid) return false
        if (rider.hasVehicle()) return false
        if (target.isSpectator || !target.isAlive) return false

        // 被骑者挣脱后 3 分钟内不能再骑任何人
        val now = rider.server?.ticks?.toLong() ?: return false
        val until = cooldowns[rider.uuid] ?: 0L
        if (now < until) {
            rider.sendMessage(
                Text.translatable("random-perks-bingo.message.jockey_cooldown", (until - now + 19) / 20),
                true,
            )
            return false
        }

        // force = true: 玩家默认不作为载具, 常规检查会拒绝
        if (!rider.startRiding(target, true)) return false

        rider.sendMessage(
            Text.translatable("random-perks-bingo.message.jockey_mounted", target.name),
            false,
        )
        // 被骑者提示: 聊天栏 + actionbar 各发一次, 避免 actionbar 被其他消息顶掉后
        // 对方完全不知道可以连按 WASD 挣脱
        val victimMessage = Text.translatable("random-perks-bingo.message.jockey_mounted_on", rider.name)
        target.sendMessage(victimMessage, false)
        target.sendMessage(victimMessage, true)
        return true
    }

    /** 骑乘者的输入 → 被骑的玩家 (潜行 = 下马信号, 不参与控制) */
    fun handleControl(rider: ServerPlayerEntity, input: PlayerInput, yaw: Float, pitch: Float) {
        if (!PerkBoostQuery.hasJockey(rider)) return

        val carrier = rider.vehicle as? ServerPlayerEntity ?: return

        if (input.sneak()) {
            rider.stopRiding()
            return
        }

        // sneak 一律置 false —— 它是下马键, 不该让被骑者跟着潜行
        val forwarded = PlayerInput(
            input.forward(), input.backward(), input.left(), input.right(),
            input.jump(), false, input.sprint(),
        )
        PerkNetworking.sendJockeyControl(carrier, JockeyControlS2C(forwarded, yaw, pitch))
    }

    /** 被骑者按 WASD 任意键累计达到次数 → 挣脱, 骑乘者进入 3 分钟冷却 */
    fun handleBreakFree(carrier: ServerPlayerEntity) {
        val rider = carrier.passengerList
            .filterIsInstance<ServerPlayerEntity>()
            .firstOrNull { PerkBoostQuery.hasJockey(it) } ?: return

        rider.stopRiding()

        val now = carrier.server?.ticks?.toLong() ?: return
        cooldowns[rider.uuid] = now + BREAK_FREE_COOLDOWN_TICKS

        carrier.sendMessage(
            Text.translatable("random-perks-bingo.message.jockey_broke_free", rider.name),
            true,
        )
        rider.sendMessage(
            Text.translatable("random-perks-bingo.message.jockey_thrown_off", carrier.name),
            false,
        )
    }

    /** 对局重置时清掉冷却 */
    fun clear() = cooldowns.clear()
}

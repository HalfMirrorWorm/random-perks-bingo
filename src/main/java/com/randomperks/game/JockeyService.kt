package com.randomperks.game

import com.randomperks.network.JockeyControlS2C
import com.randomperks.network.PerkNetworking
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.PlayerInput

/**
 * "Jockey" (金 · 猎杀) 的服务端逻辑。
 *
 * 原版玩家不能当载具 (没有交互入口), 但 [net.minecraft.entity.Entity.startRiding] 本身是允许的 ——
 * 1.20.2 加入的 `/ride` 命令走的就是这条路。这里用 force = true 直接骑上去。
 *
 * 之后骑乘者每 tick 上报自己的输入, 服务端原样转给被骑者。
 * 真正执行移动的是**被骑者的客户端**: 玩家位置由客户端权威计算, 服务端硬推位置会被
 * 对方下一 tick 的移动包顶掉, 所以只能让对面自己走。
 */
object JockeyService {

    /** 右键玩家 → 骑上去 (失败返回 false, 交还原版交互) */
    fun mount(rider: ServerPlayerEntity, target: ServerPlayerEntity): Boolean {
        if (rider.uuid == target.uuid) return false
        if (rider.hasVehicle()) return false
        if (target.isSpectator || !target.isAlive) return false
        // force = true: 玩家默认不作为载具, 常规检查会拒绝
        if (!rider.startRiding(target, true)) return false

        rider.sendMessage(
            Text.translatable("random-perks-bingo.message.jockey_mounted", target.name),
            false,
        )
        target.sendMessage(
            Text.translatable("random-perks-bingo.message.jockey_mounted_on", rider.name),
            true,
        )
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
}

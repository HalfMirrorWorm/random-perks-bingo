package com.randomperks.client

import com.randomperks.client.net.ClientPerkNetworking
import com.randomperks.mixin.LivingEntityJumpVelocityInvoker
import com.randomperks.network.DoubleJumpC2S
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.util.math.Vec3d

/**
 * 客户端主动能力检测 (按键 → C2S 包)。
 *
 * 二段跳: 在空中按下跳跃键时发送一次请求 (每次腾空只发一次; 服务端负责验证词条与冷却)。
 */
object AbilityHandler {

    private const val DOUBLE_JUMP_PERK = "swift_double_jump"

    private var jumpHeldLastTick = false
    private var doubleJumpUsedThisAirTime = false

    /** 已连续离地的 tick 数 (用于排除"起跳那一下"同 tick 就触发二段跳) */
    private var airTicks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player
            if (player == null || client.currentScreen != null) {
                jumpHeldLastTick = false
                return@register
            }

            // 落地 / 进入鞘翅飞行时重置; 否则累计离地时长
            if (player.isOnGround || player.isGliding) {
                doubleJumpUsedThisAirTime = false
                airTicks = 0
            } else {
                airTicks++
            }

            val jumpPressed = client.options.jumpKey.isPressed
            val justPressed = jumpPressed && !jumpHeldLastTick
            jumpHeldLastTick = jumpPressed

            // 仅持有词条时发送 (未持有者的按键不再产生无谓的网络包);
            // airTicks >= 2 排除"起跳那一下"本身 —— 那个 tick 的按跳是地面跳, 不是二段跳
            if (justPressed && !player.isOnGround && !player.isGliding &&
                player.getVehicle() == null && !doubleJumpUsedThisAirTime &&
                airTicks >= 2 && ClientPerkNetworking.hasPerk(DOUBLE_JUMP_PERK)
            ) {
                doubleJumpUsedThisAirTime = true

                // 本地预测: 立即把垂直速度**置为**一次标准跳跃冲量 (移动由客户端结算, 不等服务端回包)。
                // 置位而非叠加 —— 参考 Origins 等主流二段跳做法: 任何时机按下都得到一致的标准跳跃高度
                val impulse = (player as LivingEntityJumpVelocityInvoker).invokeGetJumpVelocity()
                player.velocity = Vec3d(player.velocity.x, impulse.toDouble(), player.velocity.z)

                if (ClientPlayNetworking.canSend(DoubleJumpC2S.ID)) {
                    ClientPlayNetworking.send(DoubleJumpC2S())
                }
            }
        }
    }
}

package com.randomperks.client

import com.randomperks.network.DoubleJumpC2S
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * 客户端主动能力检测 (按键 → C2S 包)。
 *
 * 二段跳: 在空中按下跳跃键时发送一次请求 (每次腾空只发一次; 服务端负责验证词条与冷却)。
 */
object AbilityHandler {

    private var jumpHeldLastTick = false
    private var doubleJumpUsedThisAirTime = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player
            if (player == null || client.currentScreen != null) {
                jumpHeldLastTick = false
                return@register
            }

            // 落地 / 进入鞘翅飞行时重置
            if (player.isOnGround || player.isGliding) {
                doubleJumpUsedThisAirTime = false
            }

            val jumpPressed = client.options.jumpKey.isPressed
            val justPressed = jumpPressed && !jumpHeldLastTick
            jumpHeldLastTick = jumpPressed

            if (justPressed && !player.isOnGround && !player.isGliding &&
                player.getVehicle() == null && !doubleJumpUsedThisAirTime
            ) {
                doubleJumpUsedThisAirTime = true
                if (ClientPlayNetworking.canSend(DoubleJumpC2S.ID)) {
                    ClientPlayNetworking.send(DoubleJumpC2S())
                }
            }
        }
    }
}

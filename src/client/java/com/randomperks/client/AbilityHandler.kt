package com.randomperks.client

import com.randomperks.client.net.ClientPerkNetworking
import com.randomperks.game.DoubleJumpGlideGate
import com.randomperks.mixin.LivingEntityJumpVelocityInvoker
import com.randomperks.network.DashC2S
import com.randomperks.network.DoubleJumpC2S
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.util.math.Vec3d

/**
 * 客户端主动能力检测 (按键 → C2S 包)。
 *
 * 二段跳: 在空中按下跳跃键时发送一次请求 (每次腾空只发一次; 服务端负责验证词条与冷却)。
 *
 * 与鞘翅的协作: 装备鞘翅时,**第一次**空中按跳优先二段跳
 * (通过 [DoubleJumpGlideGate] 让原版 checkGliding 返回 false, 不启动滑翔);
 * 二段跳用掉、且松开过跳跃键之后,**下一次**按跳才触发鞘翅滑翔
 * (必要时主动补发 START_FALL_FLYING, 上升阶段同样可用)。
 */
object AbilityHandler {

    private const val DOUBLE_JUMP_PERK = "swift_double_jump"
    private const val DASH_PERK = "swift_dash"

    private var jumpHeldLastTick = false
    private var doubleJumpUsedThisAirTime = false

    private var sneakHeldLastTick = false
    private var dashUsedThisAirTime = false

    /** 二段跳后仍按住跳跃键: 继续让出滑翔, 直到松手 (否则"按住不放"会立刻转成滑翔) */
    private var glideSuppressedUntilRelease = false

    /** 已连续离地的 tick 数 (用于排除"起跳那一下"同 tick 就触发二段跳) */
    private var airTicks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player
            if (player == null || client.currentScreen != null) {
                jumpHeldLastTick = false
                sneakHeldLastTick = false
                DoubleJumpGlideGate.suppress = false
                return@register
            }

            // 落地重置; 滑翔**不**重置 —— 与服务端"每段离地只允许一次"的 memory 标记保持一致
            if (player.isOnGround) {
                doubleJumpUsedThisAirTime = false
                dashUsedThisAirTime = false
                airTicks = 0
            } else {
                airTicks++
            }

            val jumpPressed = client.options.jumpKey.isPressed
            val justPressed = jumpPressed && !jumpHeldLastTick
            jumpHeldLastTick = jumpPressed
            if (!jumpPressed) glideSuppressedUntilRelease = false

            val hasPerk = ClientPerkNetworking.hasPerk(DOUBLE_JUMP_PERK)
            val airborne = !player.isOnGround && !player.isGliding && player.getVehicle() == null

            if (justPressed && airborne && !doubleJumpUsedThisAirTime && airTicks >= 2 && hasPerk) {
                // 二段跳 (装备鞘翅时同样优先于滑翔)
                doubleJumpUsedThisAirTime = true
                glideSuppressedUntilRelease = true

                // 本地预测: 立即把垂直速度**置为**一次标准跳跃冲量 (移动由客户端结算, 不等服务端回包)。
                // 置位而非叠加 —— 参考 Origins 等主流二段跳做法: 任何时机按下都得到一致的标准跳跃高度
                val impulse = (player as LivingEntityJumpVelocityInvoker).invokeGetJumpVelocity()
                player.velocity = Vec3d(player.velocity.x, impulse.toDouble(), player.velocity.z)

                if (ClientPlayNetworking.canSend(DoubleJumpC2S.ID)) {
                    ClientPlayNetworking.send(DoubleJumpC2S())
                }
            } else if (justPressed && airborne && doubleJumpUsedThisAirTime && hasPerk &&
                player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
            ) {
                // 二段跳已用 → 这一次按跳让给鞘翅。
                // 原版 tickMovement 也会自行判断, 但这里主动补发一次: 上升阶段同样能开滑翔
                // (canGlide 只要求"不在落地/骑乘/漂浮 + 有鞘翅", 服务端也接受), 重复包无副作用。
                player.networkHandler.sendPacket(
                    ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING),
                )
            }

            // 冲刺: 空中按潜行键 → 朝视线水平方向冲一次 (每次腾空限一次)
            val sneakPressed = client.options.sneakKey.isPressed
            val sneakJustPressed = sneakPressed && !sneakHeldLastTick
            sneakHeldLastTick = sneakPressed

            if (sneakJustPressed && airborne && !dashUsedThisAirTime && ClientPerkNetworking.hasPerk(DASH_PERK)) {
                dashUsedThisAirTime = true

                // 本地预测: 只改水平分量, 垂直速度保持不变
                val look = player.rotationVector
                val horizontal = Vec3d(look.x, 0.0, look.z)
                val length = horizontal.length()
                val dir = if (length < 1.0E-4) Vec3d.fromPolar(0.0f, player.yaw) else horizontal.multiply(1.0 / length)
                player.velocity = Vec3d(dir.x * 0.9, player.velocity.y, dir.z * 0.9)

                if (ClientPlayNetworking.canSend(DashC2S.ID)) {
                    ClientPlayNetworking.send(DashC2S())
                }
            }

            // 给原版 checkGliding 的让位开关 (下一 tick 生效):
            // 二段跳还没用 → 优先二段跳; 二段跳后仍按住 → 继续压住滑翔, 直到松手后的下一次按下
            DoubleJumpGlideGate.suppress = hasPerk && airborne &&
                (!doubleJumpUsedThisAirTime || glideSuppressedUntilRelease)
        }
    }
}

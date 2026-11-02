package com.randomperks.game

import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.MobEntity
import net.minecraft.util.PlayerInput
import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 驯龙高手的驾驶速度。客户端按这个速度自己移动, 再把结果发给服务端。
 *
 * 飞行: 前进/后退沿准星 (抬头爬升、低头俯冲), 左右平移, 跳跃额外上升, 松手悬停。
 * 不看低头就下落, 免得瞄准放技能时坐骑自己往下掉。
 * 地面: WASD 平移, 着地时跳跃起跳, 空中才吃重力。松手立刻停, 方便对准目标。
 */
object DragonTamerMotion {

    /** 飞行巡航, 格/tick。大约 9 格/秒 */
    private const val FLY_SPEED = 0.46

    /** 按住疾跑时的飞行速度 */
    private const val FLY_SPRINT = 0.64

    /** 跳跃在飞行速度之外额外叠的上升速度 */
    private const val FLY_LIFT = 0.28

    /** 后退沿准星反向, 比前进慢, 方便边瞄准边拉开距离 */
    private const val REVERSE = 0.55

    private const val GROUND_JUMP = 0.42

    /**
     * @param airborneVertical 地面坐骑留在空中时, 上一拍算出来的竖直速度。飞行坐骑不用。
     */
    fun velocity(mob: MobEntity, yaw: Float, pitch: Float, input: PlayerInput, airborneVertical: Double): Vec3d {
        val forward = (if (input.forward()) 1.0 else 0.0) - (if (input.backward()) 1.0 else 0.0)
        val strafe = (if (input.left()) 1.0 else 0.0) - (if (input.right()) 1.0 else 0.0)
        return if (DragonTamerService.isFlyingMount(mob)) {
            fly(mob, yaw, pitch, forward, strafe, input.jump(), input.sprint())
        } else {
            ground(mob, yaw, forward, strafe, input.jump(), input.sprint(), airborneVertical)
        }
    }

    private fun fly(
        mob: MobEntity,
        yaw: Float,
        pitch: Float,
        forward: Double,
        strafe: Double,
        jump: Boolean,
        sprint: Boolean,
    ): Vec3d {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val cosPitch = cos(pitchRad)
        val sinYaw = sin(yawRad)
        val cosYaw = cos(yawRad)
        val lookX = -sinYaw * cosPitch
        val lookY = -sin(pitchRad)
        val lookZ = cosYaw * cosPitch

        var x = 0.0
        var y = 0.0
        var z = 0.0
        if (forward > 0.0) {
            x += lookX
            y += lookY
            z += lookZ
        } else if (forward < 0.0) {
            x -= lookX * REVERSE
            y -= lookY * REVERSE
            z -= lookZ * REVERSE
        }
        if (strafe != 0.0) {
            x += cosYaw * strafe
            z += sinYaw * strafe
        }

        val length = sqrt(x * x + y * y + z * z)
        if (length > 1.0) {
            x /= length
            y /= length
            z /= length
        }

        // 体型越大飞得越快, 恶魂不会跟蝙蝠一个速度。0.6 格宽是玩家量级, 到 1.55 封顶。
        val bulk = (1.0 + (mob.width.toDouble() - 0.6).coerceAtLeast(0.0) * 0.18).coerceAtMost(1.55)
        val speed = (if (sprint) FLY_SPRINT else FLY_SPEED) * bulk
        x *= speed
        y *= speed
        z *= speed
        if (jump) y += FLY_LIFT
        return Vec3d(x, y, z)
    }

    private fun ground(
        mob: MobEntity,
        yaw: Float,
        forward: Double,
        strafe: Double,
        jump: Boolean,
        sprint: Boolean,
        airborneVertical: Double,
    ): Vec3d {
        val yawRad = Math.toRadians(yaw.toDouble())
        var mx = -sin(yawRad) * forward + cos(yawRad) * strafe
        var mz = cos(yawRad) * forward + sin(yawRad) * strafe
        val length = sqrt(mx * mx + mz * mz)
        if (length > 1.0E-4) {
            mx /= length
            mz /= length
        }

        var speed = (mob.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) * 1.8).coerceIn(0.18, 0.42)
        if (sprint) speed = (speed * 1.35).coerceAtMost(0.55)
        val moving = length > 1.0E-4

        val vy = when {
            jump && mob.isOnGround -> GROUND_JUMP
            mob.isOnGround -> 0.0
            else -> (airborneVertical - mob.finalGravity) * 0.98
        }
        return Vec3d(if (moving) mx * speed else 0.0, vy, if (moving) mz * speed else 0.0)
    }
}

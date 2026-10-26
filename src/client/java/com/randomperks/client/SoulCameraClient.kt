package com.randomperks.client

import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Vec3d

/**
 * "灵魂出窍" 的客户端相机 (参考 FreeCam / 灵魂出窍类 mod)。
 *
 * 灵魂状态下 **身体留在原地**, 相机脱离身体自由飞行并忽略方块碰撞;
 * 鼠标视角与 WASD/空格/潜行移动都由这里接管, 原版玩家输入会被冻结。
 */
object SoulCameraClient {

    /** 常速 / 加速 (格每秒) */
    private const val SPEED_NORMAL = 10.0
    private const val SPEED_FAST = 22.0

    @JvmField
    var active: Boolean = false

    @JvmField
    var pos: Vec3d = Vec3d.ZERO

    @JvmField
    var yaw: Float = 0.0f

    @JvmField
    var pitch: Float = 0.0f

    private var lastFrameNanos: Long = 0L

    fun start() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        pos = player.eyePos
        yaw = player.yaw
        pitch = player.pitch
        lastFrameNanos = System.nanoTime()
        active = true
        // 灵魂状态不能交互; 如果正在挖掘, 先停掉
        client.interactionManager?.cancelBlockBreaking()
    }

    fun stop() {
        active = false
    }

    /** 鼠标视角 (拦截原版 changeLookDirection 后调用) */
    fun rotate(deltaX: Double, deltaY: Double) {
        yaw += deltaX.toFloat()
        pitch = (pitch + deltaY.toFloat()).coerceIn(-90.0f, 90.0f)
    }

    /** 每帧由相机 mixin 调用: 处理移动键 */
    fun updatePerFrame() {
        val client = MinecraftClient.getInstance()
        val now = System.nanoTime()

        // 打开界面时不动 (避免在聊天栏里打字时灵魂乱飞)
        if (client.currentScreen != null) {
            lastFrameNanos = now
            return
        }

        val dt = ((now - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.1)
        lastFrameNanos = now

        val options = client.options
        val forward = options.forwardKey.isPressed
        val backward = options.backKey.isPressed
        val left = options.leftKey.isPressed
        val right = options.rightKey.isPressed
        val up = options.jumpKey.isPressed
        val down = options.sneakKey.isPressed
        val fast = options.sprintKey.isPressed

        var move = Vec3d.ZERO
        val look = lookVector()
        if (forward) move = move.add(look)
        if (backward) move = move.subtract(look)
        if (left) move = move.subtract(rightVector())
        if (right) move = move.add(rightVector())
        if (up) move = move.add(0.0, 1.0, 0.0)
        if (down) move = move.add(0.0, -1.0, 0.0)

        if (move.lengthSquared() < 1.0E-6) return
        val speed = if (fast) SPEED_FAST else SPEED_NORMAL
        pos = pos.add(move.normalize().multiply(speed * dt))
    }

    private fun lookVector(): Vec3d = Vec3d.fromPolar(pitch, yaw)

    private fun rightVector(): Vec3d = Vec3d.fromPolar(0.0f, yaw + 90.0f)
}

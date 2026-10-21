package com.randomperks.client

import net.minecraft.util.PlayerInput

/**
 * 客户端侧的"被 Jockey 骑乘"状态。
 *
 * 由 [com.randomperks.network.JockeyControlS2C] 写入, 由 JockeyInputMixin 在 KeyboardInput.tick
 * 的末尾读取并覆盖本地输入 —— 于是被骑者的客户端会照着骑乘者的按键走, 而且走的是**原版完整的
 * 移动/碰撞/位置上报流程** (服务端硬推玩家位置会被对方下一 tick 的移动包顶掉, 所以必须这么做)。
 *
 * 用超时兜底: 骑乘者掉线、下马或停止上报时自动解除控制。
 */
object JockeyClientState {

    /** 输入超时 (毫秒) —— 超过这段时间没收到新输入就恢复自主控制 */
    private const val TIMEOUT_MILLIS = 250L

    private var input: PlayerInput? = null
    private var lastUpdateMillis: Long = 0L

    /** 骑乘者的视角 (被骑者的视角会被同步过去) */
    @JvmStatic
    var forcedYaw: Float = 0f
        private set

    @JvmStatic
    var forcedPitch: Float = 0f
        private set

    /** 收到一次骑乘者输入 */
    fun accept(input: PlayerInput, yaw: Float, pitch: Float) {
        this.input = input
        this.forcedYaw = yaw
        this.forcedPitch = pitch
        this.lastUpdateMillis = System.currentTimeMillis()
    }

    /** 当前生效的强制输入 (没有收到过 / 已超时 → null) */
    @JvmStatic
    fun forcedInput(): PlayerInput? {
        val current = input ?: return null
        if (System.currentTimeMillis() - lastUpdateMillis > TIMEOUT_MILLIS) {
            input = null
            return null
        }
        return current
    }

    /** 是否正在被骑乘者操控 (供视角同步判断) */
    @JvmStatic
    fun isControlled(): Boolean = forcedInput() != null

    /** 被骑者按 WASD 任意键累计多少次可以挣脱 */
    private const val BREAK_FREE_PRESSES = 30

    private var lastForward = false
    private var lastBack = false
    private var lastLeft = false
    private var lastRight = false
    private var keyPresses = 0

    /**
     * 记录一次"被骑者按 WASD"的按键边缘 —— 只在被操控期间计数。
     *
     * 被骑者的键盘输入在客户端就被 [com.randomperks.mixin.JockeyInputMixin] 覆盖成了骑乘者的,
     * 所以服务端根本看不到他自己的按键, 只能在这里读**原始按键绑定**自己数。
     * 四个键各自检测"按下"边缘, 累计满 [BREAK_FREE_PRESSES] 次即可挣脱。
     *
     * @return true 表示凑满次数, 可以请求脱离 (计数随即清零)
     */
    @JvmStatic
    fun noteMovementKeys(forward: Boolean, back: Boolean, left: Boolean, right: Boolean): Boolean {
        if (forcedInput() == null) {
            lastForward = false
            lastBack = false
            lastLeft = false
            lastRight = false
            keyPresses = 0
            return false
        }

        var pressed = 0
        if (forward && !lastForward) pressed++
        if (back && !lastBack) pressed++
        if (left && !lastLeft) pressed++
        if (right && !lastRight) pressed++
        lastForward = forward
        lastBack = back
        lastLeft = left
        lastRight = right

        if (pressed == 0) return false
        keyPresses += pressed
        if (keyPresses < BREAK_FREE_PRESSES) return false
        keyPresses = 0
        return true
    }

    fun clear() {
        input = null
        lastForward = false
        lastBack = false
        lastLeft = false
        lastRight = false
        keyPresses = 0
    }
}

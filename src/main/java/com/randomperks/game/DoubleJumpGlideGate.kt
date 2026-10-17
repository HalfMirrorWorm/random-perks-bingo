package com.randomperks.game

/**
 * "二段跳 + 鞘翅"的滑翔让位开关 (只由客户端维护; 服务端永远读到 false, 不产生任何影响)。
 *
 * 原版在 `ClientPlayerEntity.tickMovement` 里调用 `PlayerEntity.checkGliding()` 来决定是否
 * 启动滑翔(并发送 START_FALL_FLYING)。客户端 `AbilityHandler` 每 tick 维护本开关:
 * 为 true 时 `DoubleJumpGlideMixin` 让 `checkGliding()` 直接返回 false —— 这一次按跳的滑翔被让出,
 * 交给二段跳; 二段跳用掉并且松开过跳跃键后开关归位, 下一次按跳就正常触发鞘翅。
 */
object DoubleJumpGlideGate {

    /** true = 当前把滑翔机会让给二段跳 (下一 tick 的 checkGliding 生效) */
    @JvmStatic
    var suppress: Boolean = false
}

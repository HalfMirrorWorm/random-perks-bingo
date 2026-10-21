package com.randomperks.game

/**
 * "无忧的安眠" 的上下文标记。
 *
 * 床爆炸的判定发生在 {@code BedBlock.onUse} 里, 而爆炸参数的替换在
 * {@code World.createExplosion} 的 redirect 里 —— 两处之间没法直接传参,
 * 于是在 onUse 的 HEAD 记下"这次是否由持词条玩家触发", RETURN 时清掉。
 * 服务端单线程, 不需要额外同步。
 */
object BedBombContext {

    /** 本次 onUse 是否要按"无忧的安眠"放大爆炸 */
    @JvmStatic
    var active: Boolean = false
}

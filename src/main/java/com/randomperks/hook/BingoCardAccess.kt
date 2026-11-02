package com.randomperks.hook

import me.jfenn.bingo.common.options.BingoCardOptions
import me.jfenn.bingo.common.state.BingoState

/**
 * 给 Java mixin 用的薄封装。活动卡牌的取法在 bingo 里是 internal,
 * Java 看不到那个方法, Kotlin 这边开了 friend path 可以。
 */
object BingoCardAccess {
    fun activeCardOptions(state: BingoState): BingoCardOptions = state.getActiveCard().options
}

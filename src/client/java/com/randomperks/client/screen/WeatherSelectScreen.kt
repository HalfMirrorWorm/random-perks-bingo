package com.randomperks.client.screen

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * 天气选择器 (词条「世界一直下雨」): 三个按钮 —— 晴朗 / 下雨 / 雷雨。
 *
 * 由服务端在玩家点击词条后下发 [com.randomperks.network.WeatherSelectS2C] 弹出;
 * 选中后回调 onPick(0/1/2), 由调用方回传 [com.randomperks.network.WeatherPickC2S]。
 */
class WeatherSelectScreen(
    private val onPick: (Int) -> Unit,
) : Screen(Component.translatable("random-perks-bingo.screen.weather")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val BUTTON_WIDTH = 160
        const val BUTTON_HEIGHT = 20
        const val BUTTON_GAP = 6
    }

    private val labels = listOf(
        "random-perks-bingo.screen.weather.clear",
        "random-perks-bingo.screen.weather.rain",
        "random-perks-bingo.screen.weather.thunder",
    )

    private var firstTop = 0

    override fun init() {
        val totalHeight = labels.size * BUTTON_HEIGHT + (labels.size - 1) * BUTTON_GAP
        firstTop = (height - totalHeight) / 2 + 8

        for ((index, key) in labels.withIndex()) {
            addRenderableWidget(
                Button.builder(Component.translatable(key)) {
                    onPick(index)
                    onClose()
                }
                    .bounds(
                        (width - BUTTON_WIDTH) / 2,
                        firstTop + index * (BUTTON_HEIGHT + BUTTON_GAP),
                        BUTTON_WIDTH,
                        BUTTON_HEIGHT,
                    )
                    .build(),
            )
        }
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, DIM_BACKGROUND)
        context.centeredText(
            font, title, width / 2, firstTop - 32, ScreenStyle.TEXT_NAME,
        )
        context.centeredText(
            font,
            Component.translatable("random-perks-bingo.screen.weather.hint"),
            width / 2, firstTop - 18, ScreenStyle.TEXT_DIM,
        )
        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    override fun isPauseScreen(): Boolean = false
}

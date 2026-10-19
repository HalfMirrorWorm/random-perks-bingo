package com.randomperks.client.screen

import com.randomperks.network.PlayerTargetEntry
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import java.util.UUID

/**
 * 通用的**玩家目标选择器**: 列出一批玩家, 点一名就回调 onPick。
 *
 * 目前用于「顺手牵羊」(选偷谁) 与「引雷术」(选劈谁)。全程无需按键 ——
 * 由服务端在获得词条时主动下发候选列表并弹出。
 */
class TargetPickScreen(
    titleKey: String,
    private val hintKey: String,
    private val targets: List<PlayerTargetEntry>,
    /**
     * 选中后是否自动关闭。
     *
     * "易位"要连选两次, 第二次弹窗必须由调用方在 onPick 里直接 setScreen 换过去 ——
     * 所以那边设成 false, 否则这里的 close() 会把刚换上去的屏幕一起清掉。
     */
    private val closeOnPick: Boolean = true,
    private val onPick: (UUID) -> Unit,
) : Screen(Text.translatable(titleKey)) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val ROW_H = 18
        const val ROW_BG = 0xC0202020.toInt()
        const val ROW_BG_HOVER = 0xE0454545.toInt()
        const val ROW_WIDTH = 180
        const val MAX_ROWS = 10
    }

    private var listLeft = 0
    private var listTop = 0
    private var hovered = -1
    private var scroll = 0

    /** 一次可见的行数 (候选比屏幕还多时可以滚轮翻) */
    private val visibleRows: Int get() = minOf(MAX_ROWS, targets.size)

    override fun init() {
        listLeft = (width - ROW_WIDTH) / 2
        listTop = (height - visibleRows * ROW_H) / 2 + 6
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, listTop - 30, ScreenStyle.TEXT_NAME)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable(hintKey),
            width / 2, listTop - 17, ScreenStyle.TEXT_DIM,
        )

        hovered = -1
        for (row in 0 until visibleRows) {
            val index = scroll + row
            if (index >= targets.size) break
            val y = listTop + row * ROW_H

            val isHovered = mouseX in listLeft..(listLeft + ROW_WIDTH) && mouseY in y..(y + ROW_H - 2)
            if (isHovered) hovered = index

            context.fill(
                listLeft, y, listLeft + ROW_WIDTH, y + ROW_H - 2,
                if (isHovered) ROW_BG_HOVER else ROW_BG,
            )
            context.drawTextWithShadow(
                textRenderer,
                targets[index].name,
                listLeft + 6, y + 4,
                if (isHovered) ScreenStyle.TEXT_HIGHLIGHT else ScreenStyle.TEXT_NAME,
            )
        }

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && hovered >= 0) {
            onPick(targets[hovered].uuid)
            if (closeOnPick) close()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        val maxScroll = (targets.size - visibleRows).coerceAtLeast(0)
        if (maxScroll == 0) return false
        scroll = (scroll - verticalAmount.toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun shouldPause(): Boolean = false
}

package com.randomperks.client.screen

import com.randomperks.client.net.ClientPerkNetworking
import com.randomperks.network.StealTargetEntry
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * "顺手牵羊"的**目标选择器**: 列出全部可选玩家, 点一名打开他的背包。
 */
class StealTargetScreen(
    private val targets: List<StealTargetEntry>,
) : Screen(Text.translatable("random-perks-bingo.screen.steal_target")) {

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
            Text.translatable("random-perks-bingo.steal_target.hint"),
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
            ClientPerkNetworking.pickStealTarget(targets[hovered].uuid)
            close()
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

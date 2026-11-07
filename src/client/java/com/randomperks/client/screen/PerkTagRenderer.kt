package com.randomperks.client.screen

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * 词条卡片上的小标签 (如"贮藏"): 绿色方框 + 绿色文字。
 *
 * <p>用于在类型名称右侧标注词条属性。未获得的词条传 `dimmed = true`, 整体压暗。
 */
object PerkTagRenderer {

    private const val PAD_X = 2
    private const val PAD_Y = 1

    /** 标签总宽度 (含左右内边距) */
    fun width(font: Font, label: Component): Int =
        font.width(label) + PAD_X * 2

    /**
     * 在 (x, y) 处画标签; [y] 与 `context.text(...)` 的文字 y 对齐。
     *
     * @return 标签总宽度
     */
    fun draw(
        context: GuiGraphicsExtractor,
        font: Font,
        label: Component,
        x: Int,
        y: Int,
        dimmed: Boolean = false,
    ): Int {
        val w = width(font, label)
        val h = font.lineHeight + PAD_Y * 2
        val top = y - PAD_Y

        val border = if (dimmed) ScreenStyle.TAG_STORABLE_BORDER_DIM else ScreenStyle.TAG_STORABLE_BORDER
        val fill = if (dimmed) ScreenStyle.TAG_STORABLE_BG_DIM else ScreenStyle.TAG_STORABLE_BG
        val text = if (dimmed) ScreenStyle.TAG_STORABLE_TEXT_DIM else ScreenStyle.TAG_STORABLE_TEXT

        context.fill(x, top, x + w, top + h, fill)
        // 1px 方框
        context.fill(x, top, x + w, top + 1, border)
        context.fill(x, top + h - 1, x + w, top + h, border)
        context.fill(x, top, x + 1, top + h, border)
        context.fill(x + w - 1, top, x + w, top + h, border)
        context.text(font, label, x + PAD_X, y, text)
        return w
    }
}

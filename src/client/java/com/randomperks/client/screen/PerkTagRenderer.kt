package com.randomperks.client.screen

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.ceil

/**
 * 词条卡片上的小标签 (如"贮藏"): 绿色圆角方框 + 绿色文字。
 *
 * <p>用于在类型名称右侧标注词条属性。整体按 [SCALE] 缩小, 并做 2-3px 圆角;
 * 未获得的词条传 `dimmed = true`, 整体压暗。
 */
object PerkTagRenderer {

    private const val PAD_X = 2
    private const val PAD_Y = 1

    /** 标签整体缩放 (只缩小显示, 不改变文字内容) */
    private const val SCALE = 0.8f

    private const val OUTER_RADIUS = 3
    private const val INNER_RADIUS = 2

    /** 标签总宽度 (含左右内边距, 已按 [SCALE] 缩放) */
    fun width(font: Font, label: Component): Int =
        ceil((font.width(label) + PAD_X * 2) * SCALE).toInt()

    /**
     * 在 (x, [y]) 处画标签; [y] 是文字行顶部 (与 `context.text(...)` 的 y 对齐)。
     *
     * @return 标签总宽度 (已缩放)
     */
    fun draw(
        context: GuiGraphicsExtractor,
        font: Font,
        label: Component,
        x: Int,
        y: Int,
        dimmed: Boolean = false,
    ): Int {
        val w = font.width(label) + PAD_X * 2
        val h = font.lineHeight + PAD_Y * 2
        val scaledH = h * SCALE
        val top = y + (font.lineHeight - scaledH) / 2f

        val border = if (dimmed) ScreenStyle.TAG_STORABLE_BORDER_DIM else ScreenStyle.TAG_STORABLE_BORDER
        val fill = if (dimmed) ScreenStyle.TAG_STORABLE_BG_DIM else ScreenStyle.TAG_STORABLE_BG
        val text = if (dimmed) ScreenStyle.TAG_STORABLE_TEXT_DIM else ScreenStyle.TAG_STORABLE_TEXT

        context.pose().pushMatrix()
        context.pose().translate(x.toFloat(), top)
        context.pose().scale(SCALE, SCALE)

        // 先画外框色, 再画内层底色, 自然形成 1px 圆角边框
        fillRoundedRect(context, 0, 0, w, h, OUTER_RADIUS, border)
        fillRoundedRect(context, 1, 1, w - 2, h - 2, INNER_RADIUS, fill)
        context.text(font, label, PAD_X, PAD_Y, text)

        context.pose().popMatrix()
        return ceil(w * SCALE).toInt()
    }

    /** 逐行画圆角矩形; 用切角近似圆角, 小尺寸下足够平滑 */
    private fun fillRoundedRect(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        radius: Int,
        color: Int,
    ) {
        for (dy in 0 until h) {
            val inset = when {
                dy < radius -> radius - dy
                dy >= h - radius -> dy - (h - radius) + 1
                else -> 0
            }.coerceIn(0, w / 2)
            context.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color)
        }
    }
}

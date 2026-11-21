package com.randomperks.client.screen

import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * 贮藏词条的"电池"图标: 外壳 + 右侧电极, **每格代表一次使用机会**。
 *
 * 亮起的格子数 = 剩余可用次数; 暗格 = 已经用掉/尚未拥有的格数。
 * 候选与百科里未拥有的词条画满格 (只表示它总共有几次机会),
 * V 界面已获得的列表画真实剩余格数。
 */
object PerkBatteryRenderer {

    private const val CELL_W = 3
    private const val CELL_H = 6
    private const val CELL_GAP = 1

    /** 外壳内边距 */
    private const val PAD = 1

    /** 右侧电极 */
    private const val NUB_W = 2
    private const val NUB_H = 3

    private const val HEIGHT = CELL_H + 2

    /** 电池总宽度 (外壳 + 电极), 供调用方做排版/碰撞判断 */
    fun width(maxUses: Int): Int = bodyWidth(maxUses) + NUB_W

    /**
     * 在 (x, [y]) 画电池; [y] 是电池外壳顶部。
     *
     * @return 电池总宽度
     */
    fun draw(
        context: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        maxUses: Int,
        remainingUses: Int,
        dimmed: Boolean = false,
    ): Int {
        val cells = maxUses.coerceAtLeast(1)
        val remaining = remainingUses.coerceIn(0, cells)

        val border = if (dimmed) ScreenStyle.TAG_STORABLE_BORDER_DIM else ScreenStyle.TAG_STORABLE_BORDER
        val background = if (dimmed) ScreenStyle.TAG_STORABLE_BG_DIM else ScreenStyle.TAG_STORABLE_BG
        val filled = if (dimmed) ScreenStyle.TAG_STORABLE_TEXT_DIM else ScreenStyle.TAG_STORABLE_TEXT
        val empty = if (dimmed) ScreenStyle.BATTERY_EMPTY_DIM else ScreenStyle.BATTERY_EMPTY

        val bodyWidth = bodyWidth(cells)

        // 外壳: 1px 边框 + 半透明内底
        context.fill(x, y, x + bodyWidth, y + HEIGHT, border)
        context.fill(x + 1, y + 1, x + bodyWidth - 1, y + HEIGHT - 1, background)

        // 电池格: 从左往右, 亮格在前 (剩余次数)
        var cellX = x + 1 + PAD
        for (index in 0 until cells) {
            val color = if (index < remaining) filled else empty
            context.fill(cellX, y + 1, cellX + CELL_W, y + 1 + CELL_H, color)
            cellX += CELL_W + CELL_GAP
        }

        // 右侧正极
        val nubTop = y + (HEIGHT - NUB_H) / 2
        context.fill(x + bodyWidth, nubTop, x + bodyWidth + NUB_W, nubTop + NUB_H, border)

        return bodyWidth + NUB_W
    }

    /** 外壳宽度: 左右边框 + 内边距 + 格子与间隙 */
    private fun bodyWidth(maxUses: Int): Int {
        val cells = maxUses.coerceAtLeast(1)
        return 2 + PAD * 2 + cells * CELL_W + (cells - 1) * CELL_GAP
    }
}

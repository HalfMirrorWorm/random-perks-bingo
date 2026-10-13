package com.roguebingo.client.screen

import com.roguebingo.network.PerkOptionData
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * 已获得词条列表 (按键 C 打开)。
 *
 * 布局: 两列紧凑卡片网格 + 滚轮滚动, 支持任意数量的词条。
 * 卡片: 左侧品质色条 + 图标 + 名称(品质色) + 流派。
 */
class PerkListScreen(
    private val pendingCount: Int,
    private val perks: List<PerkOptionData>,
) : Screen(Text.translatable("rogue-bingo.screen.my_perks")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val COLUMNS = 2
        const val COL_GAP = 8
        const val CARD_HEIGHT = 26
        const val ROW_GAP = 4
        const val LIST_TOP = 48
        const val LIST_BOTTOM_MARGIN = 26
        const val MAX_WIDTH = 460
    }

    private var scrollOffset = 0
    private var maxScroll = 0
    private var hovered = -1

    // 布局缓存 (init/resize 时计算)
    private var listLeft = 0
    private var listWidth = 0
    private var cardWidth = 0

    override fun init() {
        listWidth = (width - 60).coerceAtMost(MAX_WIDTH)
        listLeft = (width - listWidth) / 2
        cardWidth = (listWidth - COL_GAP) / COLUMNS
        scrollOffset = 0
    }

    private fun listBottom() = height - LIST_BOTTOM_MARGIN

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // 同 PerkChoiceScreen: 不调 renderBackground, 避免 1.21.6+ 的二次 blur 崩溃
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        // 标题与待选数
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.screen.my_perks"),
            width / 2, 16, ScreenStyle.TEXT_NAME,
        )
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.list.pending", pendingCount),
            width / 2, 30,
            if (pendingCount > 0) ScreenStyle.TEXT_HIGHLIGHT else ScreenStyle.TEXT_DIM,
        )

        if (perks.isEmpty()) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("rogue-bingo.list.empty"),
                width / 2, LIST_TOP + 12, ScreenStyle.TEXT_DIM,
            )
            super.render(context, mouseX, mouseY, delta)
            return
        }

        // 计算滚动范围
        val listBottom = listBottom()
        val listHeight = listBottom - LIST_TOP
        val rows = (perks.size + COLUMNS - 1) / COLUMNS
        val rowHeight = CARD_HEIGHT + ROW_GAP
        val contentHeight = rows * rowHeight
        maxScroll = (contentHeight - listHeight + ROW_GAP).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        hovered = -1

        // 列表区域 (裁剪, 防止滚动时卡片画出界)
        context.enableScissor(listLeft - 4, LIST_TOP, listLeft + listWidth + 4, listBottom)

        for ((index, perk) in perks.withIndex()) {
            val row = index / COLUMNS
            val col = index % COLUMNS
            val x = listLeft + col * (cardWidth + COL_GAP)
            val y = LIST_TOP + row * rowHeight - scrollOffset

            val isHovered = mouseX in x..(x + cardWidth) && mouseY in y..(y + CARD_HEIGHT)
            if (isHovered) hovered = index

            drawRow(context, perk, x, y, isHovered)
        }

        context.disableScissor()

        // 滚动指示条
        if (maxScroll > 0) {
            val barX = listLeft + listWidth + 8
            val barHeight = (listHeight * listHeight / contentHeight).coerceAtLeast(12)
            val barY = LIST_TOP + (listHeight - barHeight) * scrollOffset / maxScroll
            context.fill(barX, LIST_TOP, barX + 2, listBottom, ScreenStyle.DIVIDER)
            context.fill(barX - 1, barY, barX + 3, barY + barHeight, 0x90FFFFFF.toInt())
        }

        // 悬停时底部显示描述
        if (hovered in perks.indices) {
            val perk = perks[hovered]
            val lines = textRenderer.wrapLines(Text.translatable("rogue-bingo.perk.${perk.id}.desc"), width - 100)
            var textY = height - 14 - lines.size * 10
            context.fill(30, textY - 5, width - 30, height - 6, 0xC0000000.toInt())
            for (line in lines) {
                context.drawCenteredTextWithShadow(textRenderer, line, width / 2, textY, ScreenStyle.TEXT_DESC)
                textY += 10
            }
        }

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawRow(context: DrawContext, perk: PerkOptionData, x: Int, y: Int, isHovered: Boolean) {
        val rarity = ScreenStyle.rarity(perk.rarityOrdinal)
        val archetype = ScreenStyle.archetype(perk.archetypeOrdinal)

        // 卡片背景 + 左侧品质条
        context.fill(x, y, x + cardWidth, y + CARD_HEIGHT, if (isHovered) ScreenStyle.CARD_BG_HOVER else ScreenStyle.CARD_BG)
        context.fill(x, y, x + 3, y + CARD_HEIGHT, rarity.color)
        if (isHovered) {
            context.fill(x + 3, y, x + cardWidth, y + 1, ScreenStyle.BORDER_HOVER)
            context.fill(x + 3, y + CARD_HEIGHT - 1, x + cardWidth, y + CARD_HEIGHT, ScreenStyle.BORDER_HOVER)
            context.fill(x + cardWidth - 1, y, x + cardWidth, y + CARD_HEIGHT, ScreenStyle.BORDER_HOVER)
        }

        // 图标 (16x16, 垂直居中)
        context.drawItem(perk.icon, x + 8, y + 5)

        // 名称 (品质色) + 流派 (灰色)
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.perk.${perk.id}.name"),
            x + 30, y + 5, rarity.color,
        )
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.archetype.${archetype.id}"),
            x + 30, y + 15, ScreenStyle.TEXT_DIM,
        )
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (maxScroll > 0) {
            scrollOffset = (scrollOffset - (verticalAmount * 16).toInt()).coerceIn(0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun shouldPause(): Boolean = false
}

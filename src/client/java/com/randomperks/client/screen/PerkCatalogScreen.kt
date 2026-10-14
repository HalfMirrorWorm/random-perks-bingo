package com.randomperks.client.screen

import com.randomperks.network.PerkOptionData
import com.randomperks.perk.PerkArchetype
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * 词条百科 (按键 B 打开)。
 *
 * 展示全部词条 (含未获得), 支持按流派筛选; 未获得的词条显示为暗色。
 */
class PerkCatalogScreen(
    private val ownedIds: Set<String>,
    private val allPerks: List<PerkOptionData>,
) : Screen(Text.translatable("random-perks-bingo.screen.catalog")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val COLUMNS = 3
        const val COL_GAP = 6
        const val CARD_HEIGHT = 26
        const val ROW_GAP = 4
        const val TABS_TOP = 42
        const val TAB_HEIGHT = 14
        const val LIST_TOP = 64
        const val LIST_BOTTOM_MARGIN = 22
        const val MAX_WIDTH = 480

        const val TAB_BG_SELECTED = 0xE0505050.toInt()
        const val TAB_BG_HOVER = 0xC0303030.toInt()
        const val TAB_BG = 0xA0101010.toInt()
        const val LOCKED_NAME = 0xFF666666.toInt()
        const val LOCKED_SUB = 0xFF4A4A4A.toInt()
        const val LOCKED_OVERLAY = 0x99000000.toInt()
    }

    /** null = 全部 */
    private var selectedArchetype: PerkArchetype? = null

    private var scrollOffset = 0
    private var maxScroll = 0
    private var hovered = -1
    private var hoveredTab = -1

    private var listLeft = 0
    private var listWidth = 0
    private var cardWidth = 0

    private val tabs: List<PerkArchetype?> = listOf(null) + PerkArchetype.entries

    override fun init() {
        listWidth = (width - 40).coerceAtMost(MAX_WIDTH)
        listLeft = (width - listWidth) / 2
        cardWidth = (listWidth - COL_GAP * (COLUMNS - 1)) / COLUMNS
        scrollOffset = 0
    }

    private fun filtered(): List<PerkOptionData> =
        if (selectedArchetype == null) allPerks
        else allPerks.filter { ScreenStyle.archetype(it.archetypeOrdinal) == selectedArchetype }

    private fun tabWidth() = listWidth / tabs.size

    private fun listBottom() = height - LIST_BOTTOM_MARGIN

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // 不调 renderBackground, 避免 1.21.6+ 的二次 blur 崩溃
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        // 标题 + 收集进度
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, ScreenStyle.TEXT_NAME)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("random-perks-bingo.catalog.progress", ownedIds.size, allPerks.size),
            width / 2, 27, ScreenStyle.TEXT_HIGHLIGHT,
        )

        // 流派筛选标签
        hoveredTab = -1
        val tabW = tabWidth()
        for ((i, archetype) in tabs.withIndex()) {
            val x = listLeft + i * tabW
            val y = TABS_TOP
            val w = tabW - 2
            val isHovered = mouseX in x..(x + w) && mouseY in y..(y + TAB_HEIGHT)
            if (isHovered) hoveredTab = i
            val isSelected = selectedArchetype == archetype

            context.fill(x, y, x + w, y + TAB_HEIGHT, when {
                isSelected -> TAB_BG_SELECTED
                isHovered -> TAB_BG_HOVER
                else -> TAB_BG
            })
            val label = if (archetype == null) {
                Text.translatable("random-perks-bingo.catalog.all")
            } else {
                Text.translatable("random-perks-bingo.archetype.${archetype.id}")
            }
            context.drawCenteredTextWithShadow(
                textRenderer, label, x + w / 2, y + 3,
                if (isSelected) ScreenStyle.TEXT_NAME else ScreenStyle.TEXT_DESC,
            )
        }

        val perks = filtered()
        if (perks.isEmpty()) {
            super.render(context, mouseX, mouseY, delta)
            return
        }

        // 网格布局
        val listHeight = listBottom() - LIST_TOP
        val rows = (perks.size + COLUMNS - 1) / COLUMNS
        val rowHeight = CARD_HEIGHT + ROW_GAP
        val contentHeight = rows * rowHeight
        maxScroll = (contentHeight - listHeight + ROW_GAP).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        hovered = -1
        context.enableScissor(listLeft - 4, LIST_TOP, listLeft + listWidth + 4, listBottom())

        for ((index, perk) in perks.withIndex()) {
            val row = index / COLUMNS
            val col = index % COLUMNS
            val x = listLeft + col * (cardWidth + COL_GAP)
            val y = LIST_TOP + row * rowHeight - scrollOffset

            val isHovered = mouseX in x..(x + cardWidth) && mouseY in y..(y + CARD_HEIGHT)
            if (isHovered) hovered = index

            drawCard(context, perk, x, y, isHovered)
        }

        context.disableScissor()

        // 滚动条
        if (maxScroll > 0) {
            val barX = listLeft + listWidth + 8
            val barHeight = (listHeight * listHeight / contentHeight).coerceAtLeast(12)
            val barY = LIST_TOP + (listHeight - barHeight) * scrollOffset / maxScroll
            context.fill(barX, LIST_TOP, barX + 2, listBottom(), ScreenStyle.DIVIDER)
            context.fill(barX - 1, barY, barX + 3, barY + barHeight, 0x90FFFFFF.toInt())
        }

        // 悬停描述
        if (hovered in perks.indices) {
            val perk = perks[hovered]
            val lines = textRenderer.wrapLines(Text.translatable("random-perks-bingo.perk.${perk.id}.desc"), width - 100)
            var textY = height - 12 - lines.size * 10
            context.fill(30, textY - 5, width - 30, height - 5, 0xC0000000.toInt())
            for (line in lines) {
                context.drawCenteredTextWithShadow(textRenderer, line, width / 2, textY, ScreenStyle.TEXT_DESC)
                textY += 10
            }
        }

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawCard(context: DrawContext, perk: PerkOptionData, x: Int, y: Int, isHovered: Boolean) {
        val owned = perk.id in ownedIds
        val displayColor = ScreenStyle.rarityDisplayColor(perk.rarityOrdinal)
        val archetype = ScreenStyle.archetype(perk.archetypeOrdinal)

        context.fill(x, y, x + cardWidth, y + CARD_HEIGHT, if (isHovered) ScreenStyle.CARD_BG_HOVER else ScreenStyle.CARD_BG)
        // 左侧品质条 (未获得的减淡)
        context.fill(x, y, x + 3, y + CARD_HEIGHT, if (owned) displayColor else dim(displayColor))
        if (isHovered) {
            context.fill(x + 3, y, x + cardWidth, y + 1, ScreenStyle.BORDER_HOVER)
            context.fill(x + 3, y + CARD_HEIGHT - 1, x + cardWidth, y + CARD_HEIGHT, ScreenStyle.BORDER_HOVER)
            context.fill(x + cardWidth - 1, y, x + cardWidth, y + CARD_HEIGHT, ScreenStyle.BORDER_HOVER)
        }

        // 图标 (未获得盖半透明黑)
        context.drawItem(perk.icon, x + 7, y + 5)
        if (!owned) context.fill(x + 7, y + 5, x + 23, y + 21, LOCKED_OVERLAY)

        // 名称 + 流派
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("random-perks-bingo.perk.${perk.id}.name"),
            x + 27, y + 5,
            if (owned) displayColor else LOCKED_NAME,
        )
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("random-perks-bingo.archetype.${archetype.id}"),
            x + 27, y + 15,
            if (owned) ScreenStyle.TEXT_DIM else LOCKED_SUB,
        )
    }

    /** 颜色减淡到约 50% 亮度 (保留 alpha) */
    private fun dim(color: Int): Int =
        (color and 0xFF000000.toInt()) or ((color and 0x00FEFEFE.toInt()) shr 1)

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && hoveredTab in tabs.indices) {
            selectedArchetype = tabs[hoveredTab]
            scrollOffset = 0
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
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

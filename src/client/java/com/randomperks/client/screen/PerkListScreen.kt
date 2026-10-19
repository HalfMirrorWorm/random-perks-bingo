package com.randomperks.client.screen

import com.randomperks.network.PerkOptionData
import com.randomperks.network.PlayerPerkList
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * 词条列表 (按键 V 打开)。
 *
 * 顶部是 tab 栏 —— **每个在线玩家一个 tab**, 打开时默认选中自己;
 * 下面复用原来的两列卡片网格, 显示当前选中玩家的词条。
 */
class PerkListScreen(
    private val pendingCount: Int,
    private val players: List<PlayerPerkList>,
) : Screen(Text.translatable("random-perks-bingo.screen.my_perks")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val COLUMNS = 2
        const val COL_GAP = 8
        const val CARD_HEIGHT = 26
        const val ROW_GAP = 4
        const val LIST_BOTTOM_MARGIN = 26
        const val MAX_WIDTH = 460

        const val TAB_TOP = 44
        const val TAB_HEIGHT = 14
        const val TAB_GAP = 4
        const val TAB_PAD = 6
        const val TAB_LINE_GAP = 3
    }

    private data class TabBox(val index: Int, val x: Int, val y: Int, val width: Int)

    private var selected = -1
    private var scrollOffset = 0
    private var maxScroll = 0
    private var hovered = -1

    private var sortMode = PerkSortMode.DEFAULT
    private var sortBoxes: List<Pair<PerkSortMode, IntArray>> = emptyList()

    private var listLeft = 0
    private var listWidth = 0
    private var cardWidth = 0
    private var listTop = TAB_TOP

    private val tabs = mutableListOf<TabBox>()

    private fun selfUuid() = MinecraftClient.getInstance().player?.uuid

    override fun init() {
        listWidth = (width - 60).coerceAtMost(MAX_WIDTH)
        listLeft = (width - listWidth) / 2
        cardWidth = (listWidth - COL_GAP) / COLUMNS
        scrollOffset = 0

        // 只有第一次打开 (或选择失效) 时才定位到自己; 窗口 resize 保留当前选择
        if (selected !in players.indices) {
            val self = selfUuid()
            selected = players.indexOfFirst { it.uuid == self }.coerceAtLeast(0)
        }

        buildTabs()
    }

    /** tab 从左到右排, 一行放不下就换行 */
    private fun buildTabs() {
        tabs.clear()
        if (players.isEmpty()) {
            listTop = TAB_TOP
            return
        }

        var x = listLeft
        var y = TAB_TOP
        for ((index, player) in players.withIndex()) {
            val tabWidth = textRenderer.getWidth(player.name) + TAB_PAD * 2
            if (x > listLeft && x + tabWidth > listLeft + listWidth) {
                x = listLeft
                y += TAB_HEIGHT + TAB_LINE_GAP
            }
            tabs.add(TabBox(index, x, y, tabWidth))
            x += tabWidth + TAB_GAP
        }
        listTop = y + TAB_HEIGHT + 8
    }

    private fun listBottom() = height - LIST_BOTTOM_MARGIN

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // 同 PerkChoiceScreen: 不调 renderBackground, 避免 1.21.6+ 的二次 blur 崩溃
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        val current = players.getOrNull(selected)
        val self = selfUuid()

        // 右上角的排序切换 (默认 / 名称 / 稀有度)
        sortBoxes = drawPerkSortButtons(context, textRenderer, width - 8, 6, sortMode, mouseX, mouseY)

        // 标题: 看自己 → "我的词条"; 看别人 → "XXX 的词条"
        val title = if (current != null && current.uuid != self) {
            Text.translatable("random-perks-bingo.screen.player_perks", current.name)
        } else {
            Text.translatable("random-perks-bingo.screen.my_perks")
        }
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, ScreenStyle.TEXT_NAME)

        // 待选数只对自己有意义
        if (current != null && current.uuid == self) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("random-perks-bingo.list.pending", pendingCount),
                width / 2, 30,
                if (pendingCount > 0) ScreenStyle.TEXT_HIGHLIGHT else ScreenStyle.TEXT_DIM,
            )
        }

        // tab 栏
        for (tab in tabs) {
            val active = tab.index == selected
            val hover = mouseX in tab.x..(tab.x + tab.width) && mouseY in tab.y..(tab.y + TAB_HEIGHT)
            val background = when {
                active -> ScreenStyle.CARD_BG_HOVER
                hover -> ScreenStyle.CARD_BG
                else -> 0x40000000
            }
            context.fill(tab.x, tab.y, tab.x + tab.width, tab.y + TAB_HEIGHT, background)
            if (active) {
                context.fill(
                    tab.x, tab.y + TAB_HEIGHT - 2,
                    tab.x + tab.width, tab.y + TAB_HEIGHT,
                    ScreenStyle.BORDER_HOVER,
                )
            }
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(players[tab.index].name),
                tab.x + tab.width / 2,
                tab.y + 3,
                if (active) ScreenStyle.TEXT_NAME else ScreenStyle.TEXT_DIM,
            )
        }

        val perks = sortMode.sort(current?.perks.orEmpty())
        if (perks.isEmpty()) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("random-perks-bingo.list.empty"),
                width / 2, listTop + 12, ScreenStyle.TEXT_DIM,
            )
            super.render(context, mouseX, mouseY, delta)
            return
        }

        // 计算滚动范围
        val bottom = listBottom()
        val listHeight = bottom - listTop
        val rows = (perks.size + COLUMNS - 1) / COLUMNS
        val rowHeight = CARD_HEIGHT + ROW_GAP
        val contentHeight = rows * rowHeight
        maxScroll = (contentHeight - listHeight + ROW_GAP).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        hovered = -1

        // 列表区域 (裁剪, 防止滚动时卡片画出界)
        context.enableScissor(listLeft - 4, listTop, listLeft + listWidth + 4, bottom)

        for ((index, perk) in perks.withIndex()) {
            val row = index / COLUMNS
            val col = index % COLUMNS
            val x = listLeft + col * (cardWidth + COL_GAP)
            val y = listTop + row * rowHeight - scrollOffset

            val isHovered = mouseX in x..(x + cardWidth) && mouseY in y..(y + CARD_HEIGHT)
            if (isHovered) hovered = index

            drawRow(context, perk, x, y, isHovered)
        }

        context.disableScissor()

        // 滚动指示条
        if (maxScroll > 0) {
            val barX = listLeft + listWidth + 8
            val barHeight = (listHeight * listHeight / contentHeight).coerceAtLeast(12)
            val barY = listTop + (listHeight - barHeight) * scrollOffset / maxScroll
            context.fill(barX, listTop, barX + 2, bottom, ScreenStyle.DIVIDER)
            context.fill(barX - 1, barY, barX + 3, barY + barHeight, 0x90FFFFFF.toInt())
        }

        // 悬停时底部显示描述
        if (hovered in perks.indices) {
            val perk = perks[hovered]
            val lines = textRenderer.wrapLines(Text.translatable("random-perks-bingo.perk.${perk.id}.desc"), width - 100)
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
        val displayColor = ScreenStyle.rarityDisplayColor(perk.rarityOrdinal)
        val archetype = ScreenStyle.archetype(perk.archetypeOrdinal)

        // 卡片背景 + 左侧品质条
        context.fill(x, y, x + cardWidth, y + CARD_HEIGHT, if (isHovered) ScreenStyle.CARD_BG_HOVER else ScreenStyle.CARD_BG)
        context.fill(x, y, x + 3, y + CARD_HEIGHT, displayColor)
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
            Text.translatable("random-perks-bingo.perk.${perk.id}.name"),
            x + 30, y + 5, displayColor,
        )
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("random-perks-bingo.archetype.${archetype.id}"),
            x + 30, y + 15, ScreenStyle.TEXT_DIM,
        )
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // 排序切换
        for ((mode, box) in sortBoxes) {
            if (mouseX in box[0].toDouble()..(box[0] + box[2]).toDouble() &&
                mouseY in box[1].toDouble()..(box[1] + box[3]).toDouble()
            ) {
                sortMode = mode
                scrollOffset = 0
                return true
            }
        }

        // 点 tab 切换玩家
        for (tab in tabs) {
            if (mouseX in tab.x.toDouble()..(tab.x + tab.width).toDouble() &&
                mouseY in tab.y.toDouble()..(tab.y + TAB_HEIGHT).toDouble()
            ) {
                if (tab.index != selected) {
                    selected = tab.index
                    scrollOffset = 0
                }
                return true
            }
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

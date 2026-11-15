package com.randomperks.client.screen

import com.randomperks.client.net.ClientPerkNetworking
import com.randomperks.network.PerkOptionData
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * 特质选择界面: 网格展示剩余特质 (全部灰色), 点击选择, Esc 放弃 (不消耗机会)。
 *
 * 特质与普通词条分开: 服务端在"没有普通机会或开局特质优先"时才下发本界面。
 */
class TraitChoiceScreen(
    private val options: List<PerkOptionData>,
) : Screen(Component.translatable("random-perks-bingo.screen.choose_trait")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val COLUMNS = 5
        const val GAP = 8
        const val MARGIN = 20
    }

    private var cardWidth = 0
    private var cardHeight = 0
    private var startX = 0
    private var startY = 0

    override fun init() {
        cardWidth = ((width - 2 * MARGIN - GAP * (COLUMNS - 1)) / COLUMNS).coerceAtLeast(40)
        val rows = ((options.size + COLUMNS - 1) / COLUMNS).coerceAtLeast(1)
        val available = height - 74
        cardHeight = (available / rows - GAP).coerceIn(46, 92)
        startX = (width - (cardWidth * COLUMNS + GAP * (COLUMNS - 1))) / 2
        startY = 52
    }

    private fun cardX(index: Int) = startX + (index % COLUMNS) * (cardWidth + GAP)
    private fun cardY(index: Int) = startY + (index / COLUMNS) * (cardHeight + GAP)

    private fun cardAt(mouseX: Double, mouseY: Double): Int {
        for (index in options.indices) {
            val x = cardX(index)
            val y = cardY(index)
            if (mouseX >= x && mouseX <= x + cardWidth && mouseY >= y && mouseY <= y + cardHeight) return index
        }
        return -1
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        context.centeredText(font, title, width / 2, 16, ScreenStyle.TEXT_NAME)
        context.centeredText(
            font,
            Component.translatable("random-perks-bingo.screen.choose_trait.hint"),
            width / 2, 32, ScreenStyle.TEXT_DIM,
        )

        for ((index, option) in options.withIndex()) {
            val x = cardX(index)
            val y = cardY(index)
            val hovered = mouseX in x..(x + cardWidth) && mouseY in y..(y + cardHeight)
            drawCard(context, option, x, y, hovered)
        }

        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    private fun drawCard(context: GuiGraphicsExtractor, option: PerkOptionData, x: Int, y: Int, hovered: Boolean) {
        // 特质品质是灰色; 悬停时用白色边框
        val displayColor = ScreenStyle.rarityDisplayColor(option.rarityOrdinal)
        context.fill(x, y, x + cardWidth, y + cardHeight, if (hovered) ScreenStyle.CARD_BG_HOVER else ScreenStyle.CARD_BG)

        val border = if (hovered) ScreenStyle.BORDER_HOVER else displayColor
        context.fill(x, y, x + cardWidth, y + 2, border)
        context.fill(x, y + cardHeight - 2, x + cardWidth, y + cardHeight, border)
        context.fill(x, y, x + 2, y + cardHeight, border)
        context.fill(x + cardWidth - 2, y, x + cardWidth, y + cardHeight, border)

        val centerX = x + cardWidth / 2
        context.item(option.icon, centerX - 8, y + 7)
        context.centeredText(
            font,
            Component.translatable("random-perks-bingo.perk.${option.id}.name"),
            centerX, y + 27, ScreenStyle.TEXT_NAME,
        )

        // 描述 (按卡片宽度换行; 卡片矮时只画能放下的行)
        val desc = Component.translatable("random-perks-bingo.perk.${option.id}.desc")
        var lineY = y + 41
        for (line in font.split(desc, cardWidth - 10)) {
            if (lineY + 9 > y + cardHeight - 3) break
            context.centeredText(font, line, centerX, lineY, ScreenStyle.TEXT_DESC)
            lineY += 10
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        // 26.3: button() == 1 是左键 (SDL 编码)
        if (event.button() == 1) {
            val index = cardAt(event.x(), event.y())
            if (index in options.indices) {
                ClientPerkNetworking.pickTrait(options[index].id)
                onClose()
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun isPauseScreen(): Boolean = false
}

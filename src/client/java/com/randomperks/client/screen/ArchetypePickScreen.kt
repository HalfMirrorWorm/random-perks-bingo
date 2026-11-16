package com.randomperks.client.screen

import com.randomperks.client.net.ClientPerkNetworking
import com.randomperks.perk.PerkArchetype
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * "专精"特质的流派选择器: 选择一个词条类型, 之后每次普通三选一都至少出现一个该类型的词条。
 *
 * Esc 关闭不会消耗选择机会; 可以之后在 V 界面点击该特质重新打开。
 */
class ArchetypePickScreen(
    private val purpose: String = "specialization",
) : Screen(
    Component.translatable(
        when (purpose) {
            "streak" -> "random-perks-bingo.screen.choose_streak_archetype"
            "exclude" -> "random-perks-bingo.screen.choose_exclude_archetype"
            else -> "random-perks-bingo.screen.choose_archetype"
        },
    ),
) {

    private val hintKey = when (purpose) {
        "streak" -> "random-perks-bingo.screen.choose_streak_archetype.hint"
        "exclude" -> "random-perks-bingo.screen.choose_exclude_archetype.hint"
        else -> "random-perks-bingo.screen.choose_archetype.hint"
    }

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val COLUMNS = 4
        const val GAP = 10
        const val MARGIN = 20
    }

    private val archetypes = PerkArchetype.entries.filter { it != PerkArchetype.TRAIT }

    private var cardWidth = 0
    private val cardHeight = 54
    private var startX = 0
    private val startY = 60

    override fun init() {
        cardWidth = ((width - 2 * MARGIN - GAP * (COLUMNS - 1)) / COLUMNS).coerceAtLeast(60)
        val total = cardWidth * COLUMNS + GAP * (COLUMNS - 1)
        startX = (width - total) / 2
    }

    private fun cardX(index: Int) = startX + (index % COLUMNS) * (cardWidth + GAP)
    private fun cardY(index: Int) = startY + (index / COLUMNS) * (cardHeight + GAP)

    private fun cardAt(mouseX: Double, mouseY: Double): Int {
        for (index in archetypes.indices) {
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
            Component.translatable(hintKey),
            width / 2, 32, ScreenStyle.TEXT_DIM,
        )

        for ((index, archetype) in archetypes.withIndex()) {
            val x = cardX(index)
            val y = cardY(index)
            val hovered = mouseX in x..(x + cardWidth) && mouseY in y..(y + cardHeight)
            drawCard(context, archetype, x, y, hovered)
        }

        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    private fun drawCard(context: GuiGraphicsExtractor, archetype: PerkArchetype, x: Int, y: Int, hovered: Boolean) {
        context.fill(x, y, x + cardWidth, y + cardHeight, if (hovered) ScreenStyle.CARD_BG_HOVER else ScreenStyle.CARD_BG)
        val border = if (hovered) ScreenStyle.BORDER_HOVER else ScreenStyle.TEXT_DIM
        context.fill(x, y, x + cardWidth, y + 2, border)
        context.fill(x, y + cardHeight - 2, x + cardWidth, y + cardHeight, border)
        context.fill(x, y, x + 2, y + cardHeight, border)
        context.fill(x + cardWidth - 2, y, x + cardWidth, y + cardHeight, border)

        context.centeredText(
            font,
            Component.translatable("random-perks-bingo.archetype.${archetype.id}"),
            x + cardWidth / 2, y + 14,
            ScreenStyle.TEXT_NAME,
        )
        context.centeredText(
            font,
            Component.translatable("random-perks-bingo.screen.choose_archetype.card"),
            x + cardWidth / 2, y + 32,
            ScreenStyle.TEXT_DIM,
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 1) {
            val index = cardAt(event.x(), event.y())
            if (index in archetypes.indices) {
                ClientPerkNetworking.pickArchetype(purpose, archetypes[index].ordinal)
                onClose()
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun isPauseScreen(): Boolean = false
}

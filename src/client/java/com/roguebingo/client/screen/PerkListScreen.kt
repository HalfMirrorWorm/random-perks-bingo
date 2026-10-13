package com.roguebingo.client.screen

import com.roguebingo.network.PerkOptionData
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * 已获得词条列表 (按键 C 打开)。
 */
class PerkListScreen(
    private val pendingCount: Int,
    private val perks: List<PerkOptionData>,
) : Screen(Text.translatable("rogue-bingo.screen.my_perks")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
    }

    private val cardWidth = 150
    private val cardHeight = 40
    private val gap = 8
    private var startX = 0
    private var startY = 0
    private var hovered = -1

    override fun init() {
        startX = (width - cardWidth) / 2
        startY = 64
    }

    private fun cardY(index: Int) = startY + index * (cardHeight + gap)

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // 同 PerkChoiceScreen: 不调 renderBackground, 避免 1.21.6+ 的二次 blur 崩溃
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        // 标题与待选数
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.screen.my_perks"),
            width / 2, 24, ScreenStyle.TEXT_NAME,
        )
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.list.pending", pendingCount),
            width / 2, 40,
            if (pendingCount > 0) ScreenStyle.TEXT_HIGHLIGHT else ScreenStyle.TEXT_DIM,
        )

        if (perks.isEmpty()) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("rogue-bingo.list.empty"),
                width / 2, startY + 12, ScreenStyle.TEXT_DIM,
            )
            super.render(context, mouseX, mouseY, delta)
            return
        }

        hovered = -1
        for ((index, perk) in perks.withIndex()) {
            val x = startX
            val y = cardY(index)
            if (mouseX in x..(x + cardWidth) && mouseY in y..(y + cardHeight)) hovered = index
            drawRow(context, perk, x, y, hovered == index)
        }

        // 悬停时底部显示描述
        if (hovered in perks.indices) {
            val perk = perks[hovered]
            val lines = textRenderer.wrapLines(Text.translatable("rogue-bingo.perk.${perk.id}.desc"), width - 100)
            var textY = height - 24 - lines.size * 10
            context.fill(40, textY - 8, width - 40, height - 12, 0xC0000000.toInt())
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

        context.fill(x, y, x + cardWidth, y + cardHeight, if (isHovered) ScreenStyle.CARD_BG_HOVER else ScreenStyle.CARD_BG)
        context.fill(x, y, x + 4, y + cardHeight, rarity.color)
        if (isHovered) {
            context.fill(x + 4, y, x + cardWidth, y + 1, ScreenStyle.BORDER_HOVER)
            context.fill(x + 4, y + cardHeight - 1, x + cardWidth, y + cardHeight, ScreenStyle.BORDER_HOVER)
        }

        context.drawItem(perk.icon, x + 12, y + 12)
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.perk.${perk.id}.name"),
            x + 36, y + 10, rarity.color,
        )
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.archetype.${archetype.id}"),
            x + 36, y + 23, ScreenStyle.TEXT_DIM,
        )
    }

    override fun shouldPause(): Boolean = false
}

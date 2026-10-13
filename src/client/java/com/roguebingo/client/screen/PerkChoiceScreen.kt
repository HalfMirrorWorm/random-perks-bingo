package com.roguebingo.client.screen

import com.roguebingo.client.net.ClientPerkNetworking
import com.roguebingo.network.PerkOptionData
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * 三选一词条界面: 横向三张品质色卡牌, 点击选择, Esc 放弃 (不消耗机会)。
 */
class PerkChoiceScreen(
    private val options: List<PerkOptionData>,
) : Screen(Text.translatable("rogue-bingo.screen.choose_perk")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
    }

    private val cardWidth = 130
    private val cardHeight = 190
    private val gap = 14
    private var startX = 0
    private var cardY = 0
    private var hovered = -1

    override fun init() {
        val total = options.size * cardWidth + (options.size - 1) * gap
        startX = (width - total) / 2
        cardY = (height - cardHeight) / 2 + 10
    }

    private fun cardX(index: Int) = startX + index * (cardWidth + gap)

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // 注意: 1.21.6+ 的渲染管线会自动模糊屏幕背景,
        // 这里不能再调 renderBackground() (会造成二次 blur 崩溃), 用自绘遮罩代替
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, cardY - 30, ScreenStyle.TEXT_NAME)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.screen.choose_perk.hint"),
            width / 2, cardY - 18, ScreenStyle.TEXT_DIM,
        )

        hovered = -1
        for ((index, option) in options.withIndex()) {
            val x = cardX(index)
            val y = cardY
            if (mouseX in x..(x + cardWidth) && mouseY in y..(y + cardHeight)) hovered = index
            drawCard(context, option, x, y, hovered == index)
        }

        super.render(context, mouseX, mouseY, delta)
    }

    private fun drawCard(context: DrawContext, option: PerkOptionData, x: Int, y: Int, isHovered: Boolean) {
        val rarity = ScreenStyle.rarity(option.rarityOrdinal)
        val archetype = ScreenStyle.archetype(option.archetypeOrdinal)

        // 卡片主体
        context.fill(x, y, x + cardWidth, y + cardHeight, if (isHovered) ScreenStyle.CARD_BG_HOVER else ScreenStyle.CARD_BG)

        // 品质色边框 (悬停时白色)
        val border = if (isHovered) ScreenStyle.BORDER_HOVER else rarity.color
        context.fill(x, y, x + cardWidth, y + 2, border)
        context.fill(x, y + cardHeight - 2, x + cardWidth, y + cardHeight, border)
        context.fill(x, y, x + 2, y + cardHeight, border)
        context.fill(x + cardWidth - 2, y, x + cardWidth, y + cardHeight, border)

        // 顶部品质条带
        context.fill(x + 2, y + 2, x + cardWidth - 2, y + 9, rarity.color)

        // 图标区
        val centerX = x + cardWidth / 2
        context.fill(centerX - 20, y + 24, centerX + 20, y + 64, ScreenStyle.SLOT_BG)
        context.drawItem(option.icon, centerX - 8, y + 36)

        // 名称
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.perk.${option.id}.name"),
            centerX, y + 74, ScreenStyle.TEXT_NAME,
        )

        // 品质 · 流派
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.rarity.${rarity.name.lowercase()}"),
            centerX, y + 90, rarity.color,
        )
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("rogue-bingo.archetype.${archetype.id}"),
            centerX, y + 102, ScreenStyle.TEXT_DIM,
        )

        // 分隔线
        context.fill(x + 12, y + 118, x + cardWidth - 12, y + 119, ScreenStyle.DIVIDER)

        // 描述 (自动换行)
        val desc = Text.translatable("rogue-bingo.perk.${option.id}.desc")
        var lineY = y + 128
        for (line in textRenderer.wrapLines(desc, cardWidth - 20)) {
            context.drawCenteredTextWithShadow(textRenderer, line, centerX, lineY, ScreenStyle.TEXT_DESC)
            lineY += 10
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && hovered in options.indices) {
            ClientPerkNetworking.pick(hovered)
            close()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldPause(): Boolean = false

    override fun close() {
        // 未选择就关闭时, 清掉客户端缓存 (机会不会消耗)
        ClientPerkNetworking.clearOptions()
        super.close()
    }
}

package com.roguebingo.client.screen

import com.roguebingo.client.net.ClientPerkNetworking
import com.roguebingo.network.PerkOptionData
import com.roguebingo.perk.PerkRarity
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * 三选一词条界面 (M2 简版: 横向三张卡, 显示图标 / 名称 / 描述, 点击选择)。
 *
 * M3 阶段会美化 (品质色边框 / 流派标签 / 悬停效果等)。
 */
class PerkChoiceScreen(
    private val options: List<PerkOptionData>,
) : Screen(Text.translatable("rogue-bingo.screen.choose_perk")) {

    private val cardWidth = 110
    private val cardHeight = 160
    private var cardStartX = 0
    private var cardY = 0
    private var hovered = -1

    override fun init() {
        val total = options.size * cardWidth + (options.size - 1) * 12
        cardStartX = (width - total) / 2
        cardY = (height - cardHeight) / 2
    }

    private fun cardX(index: Int) = cardStartX + index * (cardWidth + 12)

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)

        hovered = -1
        for ((index, option) in options.withIndex()) {
            val x = cardX(index)
            val y = cardY
            if (mouseX in x..(x + cardWidth) && mouseY in y..(y + cardHeight)) hovered = index

            val rarity = PerkRarity.entries.getOrElse(option.rarityOrdinal) { PerkRarity.BLUE }
            val border = if (hovered == index) 0xFFFFFFFF.toInt() else rarity.color
            val background = 0xC0101010.toInt()

            // 卡片背景与品质色边框
            context.fill(x, y, x + cardWidth, y + cardHeight, background)
            context.fill(x, y, x + cardWidth, y + 2, border)
            context.fill(x, y + cardHeight - 2, x + cardWidth, y + cardHeight, border)
            context.fill(x, y, x + 2, y + cardHeight, border)
            context.fill(x + cardWidth - 2, y, x + cardWidth, y + cardHeight, border)

            // 图标
            context.drawItem(option.icon, x + cardWidth / 2 - 8, y + 16)

            // 名称 / 品质 / 描述
            val name = Text.translatable("rogue-bingo.perk.${option.id}.name")
            val desc = Text.translatable("rogue-bingo.perk.${option.id}.desc")
            context.drawCenteredTextWithShadow(textRenderer, name, x + cardWidth / 2, y + 44, 0xFFFFFF)

            val rarityLabel = Text.translatable("rogue-bingo.rarity.${rarity.name.lowercase()}")
            context.drawCenteredTextWithShadow(textRenderer, rarityLabel, x + cardWidth / 2, y + 58, rarity.color)

            var lineY = y + 76
            for (line in textRenderer.wrapLines(desc, cardWidth - 16)) {
                context.drawTextWithShadow(textRenderer, line, x + 8, lineY, 0xAAAAAA)
                lineY += 10
            }
        }

        super.render(context, mouseX, mouseY, delta)
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
        // 未选择就关闭时, 通知网络层清掉缓存 (机会不会消耗)
        ClientPerkNetworking.clearOptions()
        super.close()
    }
}

package com.randomperks.client.screen

import net.minecraft.client.input.MouseButtonEvent
import com.randomperks.client.net.ClientPerkNetworking
import com.randomperks.network.PerkOptionData
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * 三选一词条界面: 横向三张品质色卡牌, 点击选择, Esc 放弃 (不消耗机会)。
 */
class PerkChoiceScreen(
    private val options: List<PerkOptionData>,
    private val refreshCharges: Int = 0,
) : Screen(Component.translatable("random-perks-bingo.screen.choose_perk")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
    }

    private val cardWidth = 130
    private val cardHeight = 190
    private val gap = 14
    private var startX = 0
    private var cardY = 0
    private var hovered = -1

    private val refreshButtonWidth = 120
    private val refreshButtonHeight = 20
    private var refreshButtonX = 0
    private var refreshButtonY = 0

    override fun init() {
        val total = options.size * cardWidth + (options.size - 1) * gap
        startX = (width - total) / 2
        cardY = (height - cardHeight) / 2 + 10
        refreshButtonX = (width - refreshButtonWidth) / 2
        refreshButtonY = (cardY + cardHeight + 14).coerceAtMost(height - refreshButtonHeight - 6)
    }

    private fun cardX(index: Int) = startX + index * (cardWidth + gap)

    /** 直接用点击坐标做命中检测, 不依赖上一帧渲染时记录的 hovered */
    private fun cardAt(mouseX: Double, mouseY: Double): Int {
        for (index in options.indices) {
            val x = cardX(index)
            if (mouseX >= x && mouseX <= x + cardWidth &&
                mouseY >= cardY && mouseY <= cardY + cardHeight
            ) {
                return index
            }
        }
        return -1
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // 注意: 1.21.6+ 的渲染管线会自动模糊屏幕背景,
        // 这里不能再调 renderBackground() (会造成二次 blur 崩溃), 用自绘遮罩代替
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        context.centeredText(font, title, width / 2, cardY - 30, ScreenStyle.TEXT_NAME)
        context.centeredText(
            font,
            Component.translatable("random-perks-bingo.screen.choose_perk.hint"),
            width / 2, cardY - 18, ScreenStyle.TEXT_DIM,
        )

        hovered = -1
        for ((index, option) in options.withIndex()) {
            val x = cardX(index)
            val y = cardY
            if (mouseX in x..(x + cardWidth) && mouseY in y..(y + cardHeight)) hovered = index
            drawCard(context, option, x, y, hovered == index)
        }

        if (refreshCharges > 0) {
            val hover = mouseX in refreshButtonX..(refreshButtonX + refreshButtonWidth) &&
                mouseY in refreshButtonY..(refreshButtonY + refreshButtonHeight)
            context.fill(
                refreshButtonX, refreshButtonY,
                refreshButtonX + refreshButtonWidth, refreshButtonY + refreshButtonHeight,
                if (hover) ScreenStyle.CARD_BG_HOVER else ScreenStyle.CARD_BG,
            )
            val border = if (hover) ScreenStyle.BORDER_HOVER else ScreenStyle.TEXT_DIM
            context.fill(refreshButtonX, refreshButtonY, refreshButtonX + refreshButtonWidth, refreshButtonY + 1, border)
            context.fill(refreshButtonX, refreshButtonY + refreshButtonHeight - 1, refreshButtonX + refreshButtonWidth, refreshButtonY + refreshButtonHeight, border)
            context.fill(refreshButtonX, refreshButtonY, refreshButtonX + 1, refreshButtonY + refreshButtonHeight, border)
            context.fill(refreshButtonX + refreshButtonWidth - 1, refreshButtonY, refreshButtonX + refreshButtonWidth, refreshButtonY + refreshButtonHeight, border)
            context.centeredText(
                font,
                Component.translatable("random-perks-bingo.screen.refresh", refreshCharges),
                refreshButtonX + refreshButtonWidth / 2, refreshButtonY + 6,
                if (hover) ScreenStyle.TEXT_NAME else ScreenStyle.TEXT_DESC,
            )
        }

        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    private fun drawCard(context: GuiGraphicsExtractor, option: PerkOptionData, x: Int, y: Int, isHovered: Boolean) {
        val rarity = ScreenStyle.rarity(option.rarityOrdinal)
        val archetype = ScreenStyle.archetype(option.archetypeOrdinal)
        val displayColor = ScreenStyle.rarityDisplayColor(option.rarityOrdinal)

        // 卡片主体
        context.fill(x, y, x + cardWidth, y + cardHeight, if (isHovered) ScreenStyle.CARD_BG_HOVER else ScreenStyle.CARD_BG)

        // 品质色边框 (悬停时白色)
        val border = if (isHovered) ScreenStyle.BORDER_HOVER else displayColor
        context.fill(x, y, x + cardWidth, y + 2, border)
        context.fill(x, y + cardHeight - 2, x + cardWidth, y + cardHeight, border)
        context.fill(x, y, x + 2, y + cardHeight, border)
        context.fill(x + cardWidth - 2, y, x + cardWidth, y + cardHeight, border)

        // 顶部品质条带
        context.fill(x + 2, y + 2, x + cardWidth - 2, y + 9, displayColor)

        // 图标区
        val centerX = x + cardWidth / 2
        context.fill(centerX - 20, y + 24, centerX + 20, y + 64, ScreenStyle.SLOT_BG)
        context.item(option.icon, centerX - 8, y + 36)

        // 名称
        context.centeredText(
            font,
            Component.translatable("random-perks-bingo.perk.${option.id}.name"),
            centerX, y + 74, ScreenStyle.TEXT_NAME,
        )

        // 品质 · 流派 (+ 贮藏标记)
        val rarityLabel = Component.translatable("random-perks-bingo.rarity.${rarity.name.lowercase()}")
        val archetypeLabel = Component.translatable("random-perks-bingo.archetype.${archetype.id}")
        context.centeredText(font, rarityLabel, centerX, y + 90, displayColor)
        context.centeredText(font, archetypeLabel, centerX, y + 102, ScreenStyle.TEXT_DIM)

        // 分隔线
        context.fill(x + 12, y + 118, x + cardWidth - 12, y + 119, ScreenStyle.DIVIDER)

        // 贮藏电池: 候选阶段画满格, 每格代表一次使用机会 (神话词条不允许贮藏)
        val showStorable = option.storable && rarity != com.randomperks.perk.PerkRarity.MYTHIC
        if (showStorable) {
            val batteryWidth = PerkBatteryRenderer.width(option.maxUses)
            PerkBatteryRenderer.draw(
                context,
                centerX - batteryWidth / 2,
                y + 123,
                option.maxUses,
                option.remainingUses,
            )
        }

        // 描述 (自动换行) —— 有贮藏标记时整体下移一行
        val desc = Component.translatable("random-perks-bingo.perk.${option.id}.desc")
        var lineY = if (showStorable) y + 135 else y + 128
        for (line in font.split(desc, cardWidth - 20)) {
            context.centeredText(font, line, centerX, lineY, ScreenStyle.TEXT_DESC)
            lineY += 10
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = event.button()

        if (button == 1 && refreshCharges > 0 &&
            mouseX >= refreshButtonX && mouseX <= refreshButtonX + refreshButtonWidth &&
            mouseY >= refreshButtonY && mouseY <= refreshButtonY + refreshButtonHeight
        ) {
            ClientPerkNetworking.refreshOffer()
            // 立刻关掉旧界面, 等服务端的新候选重新打开; 避免旧卡牌在新候选到达前被点击
            onClose()
            return true
        }

        val index = cardAt(mouseX, mouseY)
        if (button == 1 && index in options.indices) {
            ClientPerkNetworking.pick(index)
            onClose()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        // 未选择就关闭时, 清掉客户端缓存 (机会不会消耗)
        ClientPerkNetworking.clearOptions()
        super.onClose()
    }
}

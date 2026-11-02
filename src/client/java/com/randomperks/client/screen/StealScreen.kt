package com.randomperks.client.screen

import net.minecraft.client.input.MouseButtonEvent
import com.randomperks.client.net.ClientPerkNetworking
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component

/**
 * "顺手牵羊"的选择界面: 显示目标玩家背包 (9×4 网格), 点击一格偷取。
 */
class StealScreen(
    private val targetName: String,
    private val items: List<ItemStack>,
) : Screen(Component.translatable("random-perks-bingo.screen.steal", targetName)) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val COLS = 9
        const val ROWS = 4
        const val CELL = 20
        const val CELL_BG = 0xC0202020.toInt()
        const val CELL_BG_HOVER = 0xE0454545.toInt()
        const val CELL_BG_EMPTY = 0xA0151515.toInt()
    }

    private var gridLeft = 0
    private var gridTop = 0
    private var hovered = -1

    override fun init() {
        gridLeft = (width - COLS * CELL) / 2
        gridTop = (height - ROWS * CELL) / 2 + 6
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        val titleWidth = font.width(title)
        PlayerHeadRenderer.drawByName(context, targetName, width / 2 - titleWidth / 2 - 12, gridTop - 34)
        context.centeredText(
            font,
            title,
            width / 2, gridTop - 30,
            TeamColorUtil.forPlayerName(targetName),
        )
        context.centeredText(
            font,
            Component.translatable("random-perks-bingo.steal.hint"),
            width / 2, gridTop - 17, ScreenStyle.TEXT_DIM,
        )

        hovered = -1
        for (index in items.indices) {
            val col = index % COLS
            val row = index / COLS
            val x = gridLeft + col * CELL
            val y = gridTop + row * CELL
            val stack = items[index]

            val isHovered = mouseX in x..(x + CELL - 2) && mouseY in y..(y + CELL - 2)
            val isEmpty = stack.isEmpty
            if (isHovered && !isEmpty) hovered = index

            context.fill(
                x, y, x + CELL - 2, y + CELL - 2,
                when {
                    isHovered && !isEmpty -> CELL_BG_HOVER
                    isEmpty -> CELL_BG_EMPTY
                    else -> CELL_BG
                },
            )
            if (!isEmpty) {
                context.item(stack, x + 2, y + 2)
            }
        }

        // 悬停: 显示物品名与数量
        if (hovered >= 0) {
            context.centeredText(
                font,
                items[hovered].hoverName,
                width / 2, gridTop + ROWS * CELL + 8, ScreenStyle.TEXT_HIGHLIGHT,
            )
        }

        super.extractRenderState(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = event.button()
        if (button == 0 && hovered >= 0) {
            ClientPerkNetworking.pickSteal(hovered)
            onClose()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun isPauseScreen(): Boolean = false
}

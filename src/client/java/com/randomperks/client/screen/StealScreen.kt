package com.randomperks.client.screen

import com.randomperks.client.net.ClientPerkNetworking
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.item.ItemStack
import net.minecraft.text.Text

/**
 * "顺手牵羊"的选择界面: 显示目标玩家背包 (9×4 网格), 点击一格偷取。
 */
class StealScreen(
    private val targetName: String,
    private val items: List<ItemStack>,
) : Screen(Text.translatable("random-perks-bingo.screen.steal", targetName)) {

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

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, gridTop - 30, ScreenStyle.TEXT_NAME)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("random-perks-bingo.steal.hint"),
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
                context.drawItem(stack, x + 2, y + 2)
            }
        }

        // 悬停: 显示物品名与数量
        if (hovered >= 0) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                items[hovered].name,
                width / 2, gridTop + ROWS * CELL + 8, ScreenStyle.TEXT_HIGHLIGHT,
            )
        }

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && hovered >= 0) {
            ClientPerkNetworking.pickSteal(hovered)
            close()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldPause(): Boolean = false
}

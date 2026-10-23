package com.randomperks.client.screen

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.item.ItemStack
import net.minecraft.text.Text

/**
 * **多格物品选择器** —— 显示自己的物品栏 (9×4 网格), 点格子切换选中, 集齐 required 格后确认。
 *
 * 用于「远程传输」: 服务端先锁定队友, 再下发自己的背包快照; 客户端选完把格号回传。
 */
class ItemSelectScreen(
    private val items: List<ItemStack>,
    private val count: Int,
    private val onSubmit: (List<Int>) -> Unit,
) : Screen(Text.translatable("random-perks-bingo.screen.remote_transfer_items")) {

    private companion object {
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val COLS = 9
        const val ROWS = 4
        const val CELL = 20
        const val CELL_BG = 0xC0202020.toInt()
        const val CELL_BG_HOVER = 0xE0454545.toInt()
        const val CELL_BG_EMPTY = 0xA0151515.toInt()
        const val CELL_BG_SELECTED = 0xE02E7D32.toInt()
    }

    /** 最多能选几格 (背包里不足 4 格时按实际数量) */
    private val required = minOf(count, items.count { !it.isEmpty })

    private val selected = linkedSetOf<Int>()

    private var gridLeft = 0
    private var gridTop = 0
    private var hovered = -1
    private var confirmButton: ButtonWidget? = null

    override fun init() {
        gridLeft = (width - COLS * CELL) / 2
        gridTop = (height - ROWS * CELL) / 2 + 6

        confirmButton = ButtonWidget.builder(
            Text.translatable("random-perks-bingo.remote_transfer_items.confirm"),
        ) { submit() }
            .dimensions(width / 2 - 60, gridTop + ROWS * CELL + 24, 120, 20)
            .build()
            .also { button ->
                button.active = false
                addDrawableChild(button)
            }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, gridTop - 38, ScreenStyle.TEXT_NAME)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable("random-perks-bingo.remote_transfer_items.hint", required),
            width / 2, gridTop - 24, ScreenStyle.TEXT_DIM,
        )
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable(
                "random-perks-bingo.remote_transfer_items.selected",
                selected.size,
                required,
            ),
            width / 2, gridTop + ROWS * CELL + 8, ScreenStyle.TEXT_HIGHLIGHT,
        )

        hovered = -1
        var hoveredStack: ItemStack? = null

        for (index in 0 until COLS * ROWS) {
            val col = index % COLS
            val row = index / COLS
            val x = gridLeft + col * CELL
            val y = gridTop + row * CELL
            val stack = items.getOrElse(index) { ItemStack.EMPTY }
            val isEmpty = stack.isEmpty

            val isHovered = !isEmpty && mouseX in x until (x + CELL - 2) && mouseY in y until (y + CELL - 2)
            if (isHovered) {
                hovered = index
                hoveredStack = stack
            }

            context.fill(
                x, y, x + CELL - 2, y + CELL - 2,
                when {
                    index in selected -> CELL_BG_SELECTED
                    isHovered -> CELL_BG_HOVER
                    isEmpty -> CELL_BG_EMPTY
                    else -> CELL_BG
                },
            )
            if (!isEmpty) {
                context.drawItem(stack, x + 2, y + 2)
            }
        }

        super.render(context, mouseX, mouseY, delta)

        hoveredStack?.let { context.drawItemTooltip(textRenderer, it, mouseX, mouseY) }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && hovered >= 0) {
            if (hovered in selected) {
                selected.remove(hovered)
            } else if (selected.size < required) {
                selected.add(hovered)
            }
            confirmButton?.active = selected.size == required
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun submit() {
        if (selected.size != required || required <= 0) return
        onSubmit(selected.toList())
        close()
    }

    override fun shouldPause(): Boolean = false
}

package com.randomperks.client.screen

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.text.Text

/**
 * **物品选择器** —— 把玩家身上所有东西摆成一个网格, 点一格就回调 onPick。
 *
 * 用于「快速附魔」: 由服务端在获得词条时下发 EquipmentPickOpenS2C 主动弹出, 全程无需按键。
 * 候选是固定的 (4 个护甲槽 + 副手 + 36 格物品栏), 所以服务端不下发列表 ——
 * 客户端直接读自己的背包来显示就够了; 服务端拿到 key 之后还会自己再查一遍。
 *
 * 每格带一个 key: 装备槽用 `EquipmentSlot.getName()` (head / chest / legs / feet / offhand),
 * 物品栏用 `inv:<索引>` —— 索引是 [PlayerInventory] 的原始索引, 0-8 是快捷栏。
 */
class EquipmentPickScreen(
    titleKey: String,
    private val hintKey: String,
    private val onPick: (String) -> Unit,
) : Screen(Text.translatable(titleKey)) {

    private companion object {
        val EQUIP_SLOTS: List<EquipmentSlot> = listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND,
        )

        const val CELL = 18
        const val COLS = 9
        const val DIM_BACKGROUND = 0x90000000.toInt()
        const val CELL_BG = 0xC0202020.toInt()
        const val CELL_BG_HOVER = 0xE0555555.toInt()
    }

    /** 网格里的一格; key 为 null 表示占位 (第一行补满用), 不可点 */
    private data class Cell(val key: String?, val stack: ItemStack)

    private var gridLeft = 0
    private var gridTop = 0
    private var cells: List<Cell> = emptyList()
    private var hovered = -1

    /** 按原版背包的视觉顺序摆: 装备 → 主背包 → 快捷栏 (快捷栏压在最下面一行, 和原版一致) */
    private fun buildCells(): List<Cell> {
        val player = client?.player ?: return emptyList()
        val inventory = player.inventory
        val list = ArrayList<Cell>(COLS * 5)

        // 第一行: 4 个护甲槽 + 副手, 余下的格子用占位补齐, 免得背包跟着错位
        for (slot in EQUIP_SLOTS) {
            list.add(Cell(slot.getName(), player.getEquippedStack(slot)))
        }
        repeat(COLS - EQUIP_SLOTS.size) { list.add(Cell(null, ItemStack.EMPTY)) }

        val hotbarSize = PlayerInventory.getHotbarSize()
        for (index in hotbarSize until inventory.size()) {
            list.add(Cell("inv:$index", inventory.getStack(index)))
        }
        for (index in 0 until hotbarSize) {
            list.add(Cell("inv:$index", inventory.getStack(index)))
        }
        return list
    }

    override fun init() {
        cells = buildCells()
        val rows = (cells.size + COLS - 1) / COLS
        gridLeft = (width - COLS * CELL) / 2
        gridTop = (height - rows * CELL) / 2 + 8
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // 每帧重读一次 —— 弹窗期间背包仍可能被外部改动 (自动拾取等), 格子顺序是固定的, 不影响 hovered
        cells = buildCells()

        context.fill(0, 0, width, height, DIM_BACKGROUND)

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, gridTop - 30, ScreenStyle.TEXT_NAME)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable(hintKey),
            width / 2, gridTop - 17, ScreenStyle.TEXT_DIM,
        )

        hovered = -1
        var hoveredStack: ItemStack? = null
        for ((index, cell) in cells.withIndex()) {
            val x = gridLeft + (index % COLS) * CELL
            val y = gridTop + (index / COLS) * CELL

            // 空格子不参与悬停/点击
            val filled = !cell.stack.isEmpty
            val isHovered = filled && mouseX in x until (x + CELL) && mouseY in y until (y + CELL)
            if (isHovered) {
                hovered = index
                hoveredStack = cell.stack
            }

            context.fill(x, y, x + CELL, y + CELL, if (isHovered) CELL_BG_HOVER else CELL_BG)
            if (filled) {
                context.drawItem(cell.stack, x + 1, y + 1)
            }
        }

        super.render(context, mouseX, mouseY, delta)

        hoveredStack?.let { context.drawItemTooltip(textRenderer, it, mouseX, mouseY) }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && hovered >= 0) {
            val key = cells[hovered].key
            if (key != null) {
                onPick(key)
                close()
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldPause(): Boolean = false
}

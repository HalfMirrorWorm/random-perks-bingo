package com.randomperks.client.screen

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.text.Text

/**
 * **装备选择器** —— 列出 4 个护甲槽与副手, 点一个就回调 onPick。
 *
 * 用于「快速附魔」: 由服务端在获得词条时下发 EquipmentPickOpenS2C 主动弹出, 全程无需按键。
 * 候选是固定的 5 个槽位, 所以服务端不下发列表 —— 客户端直接读自己的装备来显示就够了。
 */
class EquipmentPickScreen(
    titleKey: String,
    private val hintKey: String,
    private val onPick: (EquipmentSlot) -> Unit,
) : Screen(Text.translatable(titleKey)) {

    companion object {
        /** 可选中的槽位 —— 与服务端 QuickEnchant 里那份保持一致 (那边还会再校验一次) */
        val EQUIP_SLOTS: List<EquipmentSlot> = listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND,
        )

        private const val DIM_BACKGROUND = 0x90000000.toInt()
        private const val ROW_H = 22
        private const val ROW_BG = 0xC0202020.toInt()
        private const val ROW_BG_HOVER = 0xE0454545.toInt()
        private const val ROW_BG_EMPTY = 0x80101010.toInt()
        private const val ROW_WIDTH = 200
        private const val ICON_X = 4
        private const val SLOT_NAME_X = 26
        private const val ITEM_NAME_X = 76
    }

    private var listLeft = 0
    private var listTop = 0
    private var hovered = -1

    private fun stackOf(slot: EquipmentSlot): ItemStack =
        client?.player?.getEquippedStack(slot) ?: ItemStack.EMPTY

    override fun init() {
        listLeft = (width - ROW_WIDTH) / 2
        listTop = (height - EQUIP_SLOTS.size * ROW_H) / 2 + 6
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, DIM_BACKGROUND)

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, listTop - 30, ScreenStyle.TEXT_NAME)
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable(hintKey),
            width / 2, listTop - 17, ScreenStyle.TEXT_DIM,
        )

        hovered = -1
        for ((index, slot) in EQUIP_SLOTS.withIndex()) {
            val y = listTop + index * ROW_H
            val stack = stackOf(slot)
            val filled = !stack.isEmpty
            // 空槽位不参与悬停/点击
            val isHovered = filled && mouseX in listLeft..(listLeft + ROW_WIDTH) && mouseY in y..(y + ROW_H - 2)
            if (isHovered) hovered = index

            context.fill(
                listLeft, y, listLeft + ROW_WIDTH, y + ROW_H - 2,
                when {
                    isHovered -> ROW_BG_HOVER
                    filled -> ROW_BG
                    else -> ROW_BG_EMPTY
                },
            )
            if (filled) {
                context.drawItem(stack, listLeft + ICON_X, y + 3)
            }
            context.drawTextWithShadow(
                textRenderer,
                Text.translatable("random-perks-bingo.slot." + slot.getName()),
                listLeft + SLOT_NAME_X, y + 7,
                ScreenStyle.TEXT_DIM,
            )
            context.drawTextWithShadow(
                textRenderer,
                if (filled) stack.name else Text.translatable("random-perks-bingo.screen.equipment_empty"),
                listLeft + ITEM_NAME_X, y + 7,
                if (isHovered) ScreenStyle.TEXT_HIGHLIGHT else ScreenStyle.TEXT_NAME,
            )
        }

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && hovered >= 0) {
            onPick(EQUIP_SLOTS[hovered])
            close()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldPause(): Boolean = false
}

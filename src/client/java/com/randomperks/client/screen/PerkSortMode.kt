package com.randomperks.client.screen

import com.randomperks.network.PerkOptionData
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

/**
 * 词条列表的排序方式 (V 键「我的词条」与 K 键「词条百科」共用)。
 *
 * 名称按**翻译后的显示名**排序 (客户端语言表已加载), 稀有度按 ordinal 从高到低 ——
 * 也就是金/神话排在最前面。
 */
enum class PerkSortMode(val id: String) {

    /** 服务端下发的原始顺序 */
    DEFAULT("default"),

    /** 按显示名称 (不区分大小写) */
    NAME("name"),

    /** 按稀有度, 高稀有度在前 */
    RARITY("rarity");

    fun sort(perks: List<PerkOptionData>): List<PerkOptionData> = when (this) {
        DEFAULT -> perks
        NAME -> perks.sortedBy {
            Text.translatable("random-perks-bingo.perk.${it.id}.name").string.lowercase()
        }
        RARITY -> perks.sortedByDescending { it.rarityOrdinal }
    }

    fun label(): Text = Text.translatable("random-perks-bingo.sort.$id")
}

/**
 * 在右上角画一排排序按钮 (从右往左: 稀有度 / 名称 / 默认)。
 *
 * @return 每个按钮的 mode 与点击区域 `[x, y, w, h]`, 供调用方做命中检测
 */
fun drawPerkSortButtons(
    context: DrawContext,
    textRenderer: TextRenderer,
    right: Int,
    top: Int,
    current: PerkSortMode,
    mouseX: Int,
    mouseY: Int,
): List<Pair<PerkSortMode, IntArray>> {
    val boxes = ArrayList<Pair<PerkSortMode, IntArray>>(PerkSortMode.entries.size)
    val h = 12
    var x = right

    for (mode in PerkSortMode.entries.reversed()) {
        val label = mode.label()
        val w = textRenderer.getWidth(label) + 8
        x -= w

        val selected = mode == current
        val hover = mouseX in x until (x + w) && mouseY in top until (top + h)
        context.fill(x, top, x + w, top + h, when {
            selected -> ScreenStyle.CARD_BG_HOVER
            hover -> ScreenStyle.CARD_BG
            else -> 0x40000000
        })
        context.drawCenteredTextWithShadow(
            textRenderer, label, x + w / 2, top + 2,
            if (selected) ScreenStyle.TEXT_HIGHLIGHT else ScreenStyle.TEXT_DIM,
        )

        boxes.add(mode to intArrayOf(x, top, w, h))
        x -= 2
    }
    return boxes
}

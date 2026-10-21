package com.randomperks.client.screen

import com.randomperks.network.PerkOptionData
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

/**
 * 词条列表的排序方式 (V 键「我的词条」与 K 键「词条百科」共用)。
 *
 * - 名称: 按**翻译后的显示名**排序 (不区分大小写)
 * - 等级: 按稀有度 ordinal 排序 (原本的"稀有度", 界面上改叫"等级")
 *
 * 名称/等级两种模式都支持升序/降序; 方向状态见 [PerkSortState]。
 */
enum class PerkSortMode(val id: String) {

    /** 服务端下发的原始顺序 */
    DEFAULT("default"),

    /** 按显示名称 (不区分大小写) */
    NAME("name"),

    /** 按等级 (稀有度) */
    RARITY("rarity");

    fun sort(perks: List<PerkOptionData>, descending: Boolean): List<PerkOptionData> = when (this) {
        DEFAULT -> perks
        NAME -> {
            val comparator = compareBy<PerkOptionData> {
                Text.translatable("random-perks-bingo.perk.${it.id}.name").string.lowercase()
            }
            if (descending) perks.sortedWith(comparator.reversed()) else perks.sortedWith(comparator)
        }
        RARITY -> {
            val comparator = compareBy<PerkOptionData> { it.rarityOrdinal }
            if (descending) perks.sortedWith(comparator.reversed()) else perks.sortedWith(comparator)
        }
    }

    /** 未选中时的按钮文字 (默认 / 名称 / 等级) */
    fun label(): Text = Text.translatable("random-perks-bingo.sort.$id")

    /** 选中时的按钮文字: 非默认模式带上当前方向 (名称↑ / 名称↓ / 等级↑ / 等级↓) */
    fun label(descending: Boolean): Text {
        val base = label()
        if (this == DEFAULT) return base
        return base.copy().append(if (descending) "↓" else "↑")
    }
}

/**
 * 排序状态: 当前模式 + 当前模式的方向 (默认都是升序 ↑)。
 *
 * 点击规则:
 * - 点**当前模式** → 切换升/降序 (默认模式除外);
 * - 点**其他模式** → 切换到该模式, 并从升序 ↑ 开始。
 */
class PerkSortState {

    var mode: PerkSortMode = PerkSortMode.DEFAULT
        private set

    /** 当前模式是否降序; 仅对 名称/等级 有意义 */
    var isDescending: Boolean = false
        private set

    /** @return 状态是否发生变化 (调用方据此重置滚动位置) */
    fun click(mode: PerkSortMode): Boolean {
        return if (mode == this.mode) {
            if (mode == PerkSortMode.DEFAULT) {
                false
            } else {
                isDescending = !isDescending
                true
            }
        } else {
            this.mode = mode
            isDescending = false
            true
        }
    }

    fun sort(perks: List<PerkOptionData>): List<PerkOptionData> =
        mode.sort(perks, isDescending)
}

/**
 * 在右上角画一排排序按钮 (从左往右: 默认 / 名称↑↓ / 等级↑↓)。
 *
 * 只有**当前选中**的排序按钮显示方向箭头; 未选中的只显示名称。
 *
 * @return 每个按钮的 mode 与点击区域 `[x, y, w, h]`, 供调用方做命中检测
 */
fun drawPerkSortButtons(
    context: DrawContext,
    textRenderer: TextRenderer,
    right: Int,
    top: Int,
    state: PerkSortState,
    mouseX: Int,
    mouseY: Int,
): List<Pair<PerkSortMode, IntArray>> {
    val boxes = ArrayList<Pair<PerkSortMode, IntArray>>(PerkSortMode.entries.size)
    val h = 12
    var x = right

    for (mode in PerkSortMode.entries.reversed()) {
        val selected = mode == state.mode
        val label = if (selected) mode.label(state.isDescending) else mode.label()
        val w = textRenderer.getWidth(label) + 8
        x -= w

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

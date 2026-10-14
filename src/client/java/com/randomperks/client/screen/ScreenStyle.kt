package com.randomperks.client.screen

import com.randomperks.perk.PerkArchetype
import com.randomperks.perk.PerkRarity

/** 界面共享的配色与工具 (全部为 ARGB —— 1.21.6+ 不自动补 alpha) */
object ScreenStyle {
    const val CARD_BG = 0xC0101010.toInt()
    const val CARD_BG_HOVER = 0xE0242424.toInt()
    const val BORDER_HOVER = 0xFFFFFFFF.toInt()
    const val TEXT_NAME = 0xFFFFFFFF.toInt()
    const val TEXT_DIM = 0xFF888888.toInt()
    const val TEXT_DESC = 0xFFAAAAAA.toInt()
    const val TEXT_HIGHLIGHT = 0xFF55FF55.toInt()
    const val SLOT_BG = 0x60000000
    const val DIVIDER = 0x40FFFFFF

    fun rarity(ordinal: Int): PerkRarity =
        PerkRarity.entries.getOrElse(ordinal) { PerkRarity.BLUE }

    fun archetype(ordinal: Int): PerkArchetype =
        PerkArchetype.entries.getOrElse(ordinal) { PerkArchetype.SWIFT }
}

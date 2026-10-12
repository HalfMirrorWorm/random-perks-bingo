package com.roguebingo.client.screen

import com.roguebingo.perk.PerkArchetype
import com.roguebingo.perk.PerkRarity

/** 界面共享的配色与工具 */
object ScreenStyle {
    const val CARD_BG = 0xC0101010.toInt()
    const val CARD_BG_HOVER = 0xE0242424.toInt()
    const val BORDER_HOVER = 0xFFFFFFFF.toInt()
    const val TEXT_NAME = 0xFFFFFF
    const val TEXT_DIM = 0x888888
    const val TEXT_DESC = 0xAAAAAA
    const val SLOT_BG = 0x60000000
    const val DIVIDER = 0x40FFFFFF

    fun rarity(ordinal: Int): PerkRarity =
        PerkRarity.entries.getOrElse(ordinal) { PerkRarity.BLUE }

    fun archetype(ordinal: Int): PerkArchetype =
        PerkArchetype.entries.getOrElse(ordinal) { PerkArchetype.SWIFT }
}

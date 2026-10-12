package com.roguebingo.perk

/**
 * 词条品质。
 *
 * @param color  界面卡片边框颜色 (RGB)
 * @param weight 抽取权重 (相对值)
 */
enum class PerkRarity(val color: Int, val weight: Int) {
    BLUE(0x4A90D9, 65),
    PURPLE(0x9B59B6, 28),
    GOLD(0xF1C40F, 7),
}

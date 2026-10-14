package com.randomperks.perk

/**
 * 词条品质。
 *
 * 注意: 颜色为完整 ARGB 格式 —— MC 1.21.6+ 的渲染管线不会自动补 alpha 通道,
 * 缺少 alpha 会渲染为全透明。
 *
 * @param color  界面卡片边框/文字颜色 (ARGB)
 * @param weight 抽取权重 (相对值)
 */
enum class PerkRarity(val color: Int, val weight: Int) {
    BLUE(0xFF4A90D9.toInt(), 65),
    PURPLE(0xFF9B59B6.toInt(), 28),
    GOLD(0xFFF1C40F.toInt(), 7),
}

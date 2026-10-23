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
    /** 粗糙 (绿): 比普通更低级的品质 */
    GREEN(0xFF4CAF50.toInt(), 300),
    BLUE(0xFF4A90D9.toInt(), 800),
    PURPLE(0xFF9B59B6.toInt(), 315),
    GOLD(0xFFF1C40F.toInt(), 82),

    /**
     * 神话 (比传说更高): 全局唯一 (同时只能一名玩家拥有), 彩色显示。
     * 颜色由客户端动态渲染 (彩虹渐变), 此处的值是回退色。
     */
    MYTHIC(0xFFFF55FF.toInt(), 3),
}

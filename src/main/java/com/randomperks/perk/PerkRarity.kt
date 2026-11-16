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
    GREEN(0xFF4CAF50.toInt(), 200),
    BLUE(0xFF4A90D9.toInt(), 720),
    PURPLE(0xFF9B59B6.toInt(), 465),
    GOLD(0xFFF1C40F.toInt(), 92),

    /** 禁忌 (红): 高于传说、低于神话 */
    RED(0xFFFF4444.toInt(), 20),

    /**
     * 神话 (比禁忌更高): 全局唯一 (同时只能一名玩家拥有), 彩色显示。
     * 颜色由客户端动态渲染 (彩虹渐变), 此处的值是回退色。
     */
    MYTHIC(0xFFFF55FF.toInt(), 3),

    /** 特质 (灰): 只能通过"特质选择"获得, 不参与普通三选一 */
    TRAIT(0xFF9E9E9E.toInt(), 0),
}

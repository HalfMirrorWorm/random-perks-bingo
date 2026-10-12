package com.roguebingo.perk

/**
 * 词条流派。
 *
 * 同一流派的词条之间有配合加成 (见 [PerkContext.countArchetype]),
 * 每个流派的 GOLD 词条是该流派的核心/质变能力。
 */
enum class PerkArchetype(val id: String) {
    /** 疾风: 跑图 / 位移 / 抢格 */
    SWIFT("swift"),

    /** 深掘: 挖矿 / 地下探索 */
    DELVER("delver"),

    /** 丰饶: 作物 / 树木 / 动物 */
    HARVEST("harvest"),

    /** 猎杀: 战斗 / 怪物掉落 */
    HUNTER("hunter"),

    /** 秘藏: 信息 / 导航 / 找物 */
    SEEKER("seeker"),

    /** 匠造: 合成 / 工具 / 功能物品 */
    ARTISAN("artisan"),
}

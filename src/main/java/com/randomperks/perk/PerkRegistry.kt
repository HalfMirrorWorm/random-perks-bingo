package com.randomperks.perk

/**
 * 全局词条注册表 (静态定义, 局间不变)。
 */
object PerkRegistry {
    private val perks = LinkedHashMap<String, PerkDefinition>()

    fun register(perk: PerkDefinition) {
        perks[perk.id] = perk
    }

    fun byId(id: String): PerkDefinition? = perks[id]

    val all: Collection<PerkDefinition> get() = perks.values

    fun size(): Int = perks.size

    /** 仅供单元测试使用 */
    internal fun clear() {
        perks.clear()
    }
}

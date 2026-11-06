package com.randomperks.perk

/**
 * 全局词条注册表 (静态定义, 局间不变)。
 */
object PerkRegistry {
    private val perks = LinkedHashMap<String, PerkDefinition>()

    /**
     * 旧 id → 新 id 的兼容映射。
     *
     * 词条换了流派后前缀也跟着改时, 旧存档里的 owned / pending 仍然写着旧 id;
     * 读档时经 [byId] 解析回新词条, 不会丢词条。
     */
    private val LEGACY_IDS = mapOf(
        "delver_toughness" to "artisan_toughness",
        "delver_iron_skin" to "artisan_iron_skin",
    )

    fun register(perk: PerkDefinition) {
        perks[perk.id] = perk
    }

    fun byId(id: String): PerkDefinition? = perks[LEGACY_IDS[id] ?: id]

    /** 当前真正可用的词条 (依赖缺失的会被 [PerkDefinition.isAvailable] 过滤掉) */
    val all: Collection<PerkDefinition> get() = perks.values.filter { it.isAvailable() }

    fun size(): Int = all.size

    /** 仅供单元测试使用 */
    internal fun clear() {
        perks.clear()
    }
}

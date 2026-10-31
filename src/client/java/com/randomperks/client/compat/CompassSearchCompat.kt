package com.randomperks.client.compat

import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.world.World
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 给第三方界面 (自然罗盘 / 结构罗盘) 的搜索框补上拼音匹配。
 *
 * 两个 mod 都按"显示名包含搜索词"过滤, 但只认中文/英文原文; 我们在它们各自的
 * sort 方法末尾追加拼音命中的条目 (全拼 / 首字母)。
 *
 * 为了不把第三方 jar 变成编译依赖, 名称查找全部走反射 (结果缓存), 并且只在搜索时执行。
 * 对应 mod 不存在时, CompassMixinPlugin 会直接跳过这两个 mixin, 这里不会被调用。
 */
object CompassSearchCompat {

    private var biomeNameMethod: Method? = null
    private var biomeNameFailed = false

    private var structureNameMethod: Method? = null
    private var structureNameFailed = false

    private var ecSearchField: Field? = null
    private var ecSearchFieldFailed = false

    // ---- 自然罗盘 (生物群系) ----

    fun addBiomePinyinMatches(
        world: World?,
        searchField: TextFieldWidget?,
        allowed: List<*>?,
        matching: List<*>?,
    ) {
        if (world == null || searchField == null || allowed == null || matching == null) return
        val query = searchField.text.trim().lowercase()
        if (!isPlainQuery(query)) return
        val target = matching as? MutableList<Any?> ?: return

        val added = ArrayList<Pair<String, Any?>>()
        for (biome in allowed) {
            if (biome == null || target.contains(biome)) continue
            val name = biomeDisplayName(world, biome) ?: continue
            if (PinyinTable.matches(name, query)) added += name to biome
        }
        appendSorted(target, added)
    }

    private fun biomeDisplayName(world: World, biome: Any): String? {
        if (biomeNameMethod == null && !biomeNameFailed) {
            biomeNameMethod = findStatic(
                "com.chaosthedude.naturescompass.utils.BiomeUtils",
                "getBiomeNameForDisplay",
                2,
            )
            if (biomeNameMethod == null) biomeNameFailed = true
        }
        val method = biomeNameMethod ?: return null
        return try {
            method.invoke(null, world, biome) as? String
        } catch (t: Throwable) {
            biomeNameFailed = true
            null
        }
    }

    // ---- 结构罗盘 (结构) ----

    fun addStructurePinyinMatches(screen: Any?, allowed: List<*>?, matching: List<*>?) {
        if (screen == null || allowed == null || matching == null) return
        val query = ecSearchText(screen)?.trim()?.lowercase() ?: return
        if (!isPlainQuery(query)) return
        val target = matching as? MutableList<Any?> ?: return

        val added = ArrayList<Pair<String, Any?>>()
        for (id in allowed) {
            if (id == null || target.contains(id)) continue
            val name = structureDisplayName(id) ?: continue
            if (PinyinTable.matches(name, query)) added += name to id
        }
        appendSorted(target, added)
    }

    /** 结构罗盘的搜索框是它自己的 TextFieldWidget 子类, 只能按字段名反射取 */
    private fun ecSearchText(screen: Any): String? {
        if (ecSearchField == null && !ecSearchFieldFailed) {
            ecSearchField = try {
                screen.javaClass.declaredFields
                    .firstOrNull { it.name == "searchTextField" }
                    ?.apply { isAccessible = true }
            } catch (t: Throwable) {
                null
            }
            if (ecSearchField == null) ecSearchFieldFailed = true
        }
        val field = ecSearchField ?: return null
        return try {
            (field.get(screen) as? TextFieldWidget)?.text
        } catch (t: Throwable) {
            ecSearchFieldFailed = true
            null
        }
    }

    private fun structureDisplayName(id: Any): String? {
        if (structureNameMethod == null && !structureNameFailed) {
            structureNameMethod = findStatic(
                "com.chaosthedude.explorerscompass.util.StructureUtils",
                "getStructureName",
                1,
            )
            if (structureNameMethod == null) structureNameFailed = true
        }
        val method = structureNameMethod ?: return null
        return try {
            method.invoke(null, id) as? String
        } catch (t: Throwable) {
            structureNameFailed = true
            null
        }
    }

    // ---- 公共 ----

    /** `$` / `@` 是罗盘 mod 自己的标签/来源过滤语法, 不要掺和 */
    private fun isPlainQuery(query: String): Boolean =
        query.isNotEmpty() && !query.startsWith("$") && !query.startsWith("@")

    private fun appendSorted(target: MutableList<Any?>, added: List<Pair<String, Any?>>) {
        if (added.isEmpty()) return
        added.sortedBy { it.first.lowercase() }.forEach { target.add(it.second) }
    }

    private fun findStatic(className: String, methodName: String, parameterCount: Int): Method? =
        try {
            Class.forName(className).declaredMethods
                .firstOrNull { it.name == methodName && it.parameterCount == parameterCount }
                ?.apply { isAccessible = true }
        } catch (t: Throwable) {
            null
        }
}

package com.randomperks.client.compat

import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.world.World
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 给第三方界面 (自然罗盘 / 结构罗盘) 的搜索框补上拼音匹配, 并顺手修掉它们中文搜索的两个坑:
 *
 *  1. 两个 mod 的过滤逻辑都**不 trim 搜索词** —— 中文输入法上词时常常会在末尾带一个空格
 *     (有时是全角空格), 原版匹配就整个失效; 这里用自己的 trim 后查询词兜底。
 *  2. 未翻译的条目显示名是注册表 id (结构尤其常见), 中文名搜不到; 这里额外按
 *     `biome.<id>` / `structure.<id>` 的本地化名称匹配一次。
 *
 * 匹配是**并集**: 原 mod 自己命中的条目保持不动, 这里只追加它漏掉的。
 * `$`(标签) / `@`(来源) 前缀是罗盘自己的语法, 完全不掺和。
 *
 * 为了不把第三方 jar 变成编译依赖, 名称查找全部走反射 (结果缓存), 并且只在搜索时执行。
 * 对应 mod 不存在时, CompassMixinPlugin 会直接跳过这两个 mixin, 这里不会被调用。
 */
object CompassSearchCompat {

    private var biomeNameMethod: Method? = null
    private var biomeNameFailed = false
    private var biomeIdMethod: Method? = null
    private var biomeIdFailed = false

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
            val display = biomeDisplayName(world, biome)
            val localized = localizedBiomeName(world, biome)
            if (!matchesAny(query, display, localized)) continue
            added += (display ?: localized ?: "") to biome
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

    /** `biome.<namespace>.<path>` 的本地化名称 (显示名退化成 id 时用它兜底) */
    private fun localizedBiomeName(world: World, biome: Any): String? {
        val id = biomeIdentifier(world, biome) ?: return null
        return Text.translatable("biome.${id.namespace}.${id.path}").string
    }

    private fun biomeIdentifier(world: World, biome: Any): Identifier? {
        if (biomeIdMethod == null && !biomeIdFailed) {
            biomeIdMethod = findStatic(
                "com.chaosthedude.naturescompass.utils.BiomeUtils",
                "getIdentifierForBiome",
                2,
            )
            if (biomeIdMethod == null) biomeIdFailed = true
        }
        val method = biomeIdMethod ?: return null
        return try {
            method.invoke(null, world, biome) as? Identifier
        } catch (t: Throwable) {
            biomeIdFailed = true
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
            val display = structureDisplayName(id)
            val localized = localizedStructureName(id)
            if (!matchesAny(query, display, localized)) continue
            added += (display ?: localized ?: "") to id
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

    /** `structure.<namespace>.<path>` 的本地化名称 (未翻译的结构显示的是 id) */
    private fun localizedStructureName(id: Any): String? {
        val identifier = id as? Identifier ?: return null
        return Text.translatable("structure.${identifier.namespace}.${identifier.path}").string
    }

    // ---- 公共 ----

    /** `$` / `@` 是罗盘 mod 自己的标签/来源过滤语法, 不要掺和 */
    private fun isPlainQuery(query: String): Boolean =
        query.isNotEmpty() && !query.startsWith("$") && !query.startsWith("@")

    /** 中文/英文原文 (trim 后) 或拼音命中 */
    private fun matchesAny(query: String, vararg names: String?): Boolean =
        names.any { name ->
            if (name == null) return@any false
            val lower = name.lowercase()
            lower.contains(query) || PinyinTable.matches(lower, query)
        }

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

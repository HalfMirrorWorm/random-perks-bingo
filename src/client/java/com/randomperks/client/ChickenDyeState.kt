package com.randomperks.client

/**
 * "叮咚鸡" 的客户端状态: 鸡的实体 id → 染色用的 ARGB。
 *
 * 由 ChickenDyeS2C 写入; 渲染时先由 ChickenDyeRendererMixin 塞进 render state,
 * 再由 ModelTintMixin 应用到模型的 color 参数上。
 *
 * 另外提供一对 push/pop: 鸡的模型渲染发生在 LivingEntityRenderer 里, 那一层拿不到实体,
 * 所以用 ThreadLocal 把"当前正在渲染的鸡是什么颜色"传下去。渲染是单线程的,
 * 而且 push/pop 成对包在 ChickenEntityRenderer.render 里, 不会串色。
 */
object ChickenDyeState {

    /** 表示"没有染色" —— 用 -1 是因为 ARGB 里 -1 就是不透明白, 不可能是染料色 */
    const val NO_TINT = -1

    private val colors = mutableMapOf<Int, Int>()

    fun accept(entityId: Int, color: Int) {
        colors[entityId] = color
    }

    @JvmStatic
    fun colorOf(entityId: Int): Int = colors[entityId] ?: NO_TINT

    fun clear() = colors.clear()

    // ---- 渲染期传递 ----

    private val current = ThreadLocal<Int>()

    @JvmStatic
    fun pushTint(color: Int) {
        current.set(color)
    }

    @JvmStatic
    fun popTint() {
        current.remove()
    }

    /** 当前正在渲染的鸡的 tint; 没有就是 [NO_TINT] */
    @JvmStatic
    fun currentTint(): Int = current.get() ?: NO_TINT
}

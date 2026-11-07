package com.randomperks.client.compat

import com.randomperks.client.RandomPerksClient

/**
 * 罗盘 HUD 默认在左上角, 而 bingo 的卡片 HUD 也固定在左上角、并且在 HUD 最后绘制,
 * 会把罗盘的文字完全盖住。
 *
 * <p>这里在客户端启动完成后通过反射把两个罗盘的 `overlaySide` 直接改成 RIGHT。
 * 玩家不需要改任何配置文件, 只要正常通过「一键更新」换掉 mod jar 就能生效;
 * 即使本地 config 里还是 LEFT, 运行时也会被覆盖。
 */
object CompassHudCompat {

    fun forceRightSide() {
        setEnum("com.chaosthedude.naturescompass.config.NaturesCompassConfig", "overlaySide")
        setEnum("com.chaosthedude.explorerscompass.config.ExplorersCompassConfig", "overlaySide")
    }

    private fun setEnum(className: String, fieldName: String) {
        try {
            val clazz = Class.forName(className)
            val field = clazz.getField(fieldName)
            val value = field.type.enumConstants
                ?.firstOrNull { (it as? Enum<*>)?.name == "RIGHT" }
                ?: return
            field.set(null, value)
            RandomPerksClient.logger.info("[RandomPerks] forced {}.{} = RIGHT", className, fieldName)
        } catch (_: Throwable) {
            // 没装对应罗盘 / 字段名变化: 静默跳过
        }
    }
}

package com.randomperks.client

import com.randomperks.client.net.ClientPerkNetworking
import com.randomperks.mixin.ScreenInvoker
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text

/**
 * 在 YABingo 的**大厅配置菜单**（YACL 的 `YACLScreen`）左下角挂一个「随机词条」按钮。
 *
 * 为什么用类名判断而不是直接引用 `YACLScreen`: YACL 是 YABingo 的可选依赖，直接 import
 * 会让本 mod 在没装 YACL 时也硬依赖它、类加载直接失败。类名前缀判断没有这个问题。
 *
 * 点击后走和 X 键完全相同的流程: 请求服务端下发候选 → 收到 PerkOfferS2C → 弹出选择界面。
 * 没有待选词条时给一条 actionbar 提示，而不是静默无反应。
 *
 * <p><b>只在"从大厅 HUD 打开"的配置菜单上挂</b>：YABingo 的客户端设置页 (Mod Menu 里点
 * YABingo → 配置) 用的是**同一个** `YACLScreen` 类，光看类名区分不开。所以这里再读一下
 * YACL 屏幕的 `parent` 字段（反射，避免硬依赖），只有父屏幕是 bingo 自己的界面 (大厅 HUD)
 * 才加按钮；从 Mod Menu 进来的一律不加。
 */
object YaclPerkButton {

    private const val YACL_SCREEN_PREFIX = "dev.isxander.yacl3.gui."
    private const val BINGO_SCREEN_PREFIX = "me.jfenn.bingo."

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, scaledWidth, scaledHeight ->
            if (!screen.javaClass.name.startsWith(YACL_SCREEN_PREFIX)) return@register

            // 客户端设置页不挂; 只有大厅 HUD 打开的那份才挂
            val parent = findParent(screen) ?: return@register
            if (!parent.javaClass.name.startsWith(BINGO_SCREEN_PREFIX)) return@register

            val button = ButtonWidget.builder(
                Text.translatable("random-perks-bingo.screen.open_choices"),
            ) { _ ->
                if (ClientPerkNetworking.pendingChoices > 0) {
                    ClientPerkNetworking.requestOffer()
                } else {
                    net.minecraft.client.MinecraftClient.getInstance().player?.sendMessage(
                        Text.translatable("random-perks-bingo.message.no_pending_choices"),
                        true,
                    )
                }
            }
                .dimensions(10, scaledHeight - 28, 120, 20)
                .build()

            (screen as ScreenInvoker).randomperksAddDrawableChild(button)
        }
    }

    /**
     * 读取 YACL 屏幕的父界面。
     *
     * YACL 用的是一个普通私有字段 `private final Screen parent`；反射拿不到时返回 null
     * （宁可不加按钮，也不要出现在客户端设置页里）。
     */
    private fun findParent(screen: Screen): Screen? {
        var type: Class<*>? = screen.javaClass
        while (type != null && type != Any::class.java) {
            try {
                val field = type.declaredFields.firstOrNull {
                    it.name == "parent" &&
                        !java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                        Screen::class.java.isAssignableFrom(it.type)
                }
                if (field != null) {
                    field.isAccessible = true
                    return field.get(screen) as? Screen
                }
            } catch (_: Throwable) {
                return null
            }
            type = type.superclass
        }
        return null
    }
}

package com.randomperks.client

import com.randomperks.client.net.ClientPerkNetworking
import com.randomperks.mixin.ScreenInvoker
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
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
 */
object YaclPerkButton {

    private const val YACL_SCREEN_PREFIX = "dev.isxander.yacl3.gui."

    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, scaledWidth, scaledHeight ->
            if (!screen.javaClass.name.startsWith(YACL_SCREEN_PREFIX)) return@register

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
}

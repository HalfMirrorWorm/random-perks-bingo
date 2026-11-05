package com.randomperks.client

import com.randomperks.client.net.ClientPerkNetworking
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.resources.Identifier

/**
 * "风之步伐"的客户端侧结算。
 *
 * 玩家移动由客户端模拟, 只依赖服务端属性同步会有一拍延迟 —— 原版自己的疾跑加成也是
 * 双端各自本地挂载的, 这里照同样方式在客户端即时挂/摘 (与效果类修饰符一样是临时修饰符)。
 */
object WindStrideClient {

    // 必须与服务端 PerkBoostQuery 中的同名定义保持一致
    private val WIND_STRIDE_MOD = Identifier.fromNamespaceAndPath("random-perks-bingo", "wind_stride_sprint")
    private const val WIND_STRIDE_BONUS = 0.30
    private const val WIND_STRIDE_PERK = "swift_wind_stride"

    /** 词条列表变化 (获得/移除) 时按当前疾跑状态重算, 不依赖下次疾跑切换 */
    fun refresh(player: LocalPlayer) {
        update(player, player.isSprinting)
    }

    /** 疾跑状态变化时由 WindStrideClientMixin 调用 */
    fun update(player: LocalPlayer, sprinting: Boolean) {
        if (sprinting && ClientPerkNetworking.hasPerk(WIND_STRIDE_PERK)) {
            val instance = player.getAttribute(Attributes.MOVEMENT_SPEED) ?: return
            instance.removeModifier(WIND_STRIDE_MOD)
            instance.addTransientModifier(
                AttributeModifier(WIND_STRIDE_MOD, WIND_STRIDE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            )
        } else {
            player.getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(WIND_STRIDE_MOD)
        }
    }
}

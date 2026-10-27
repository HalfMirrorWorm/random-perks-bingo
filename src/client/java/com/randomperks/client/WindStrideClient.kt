package com.randomperks.client

import com.randomperks.client.net.ClientPerkNetworking
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.util.Identifier

/**
 * "风之步伐"的客户端侧结算。
 *
 * 玩家移动由客户端模拟, 只依赖服务端属性同步会有一拍延迟 —— 原版自己的疾跑加成也是
 * 双端各自本地挂载的, 这里照同样方式在客户端即时挂/摘 (与效果类修饰符一样是临时修饰符)。
 */
object WindStrideClient {

    // 必须与服务端 PerkBoostQuery 中的同名定义保持一致
    private val WIND_STRIDE_MOD = Identifier.of("random-perks-bingo", "wind_stride_sprint")
    private const val WIND_STRIDE_BONUS = 0.26
    private const val WIND_STRIDE_PERK = "swift_wind_stride"

    /** 疾跑状态变化时由 WindStrideClientMixin 调用 */
    fun update(player: ClientPlayerEntity, sprinting: Boolean) {
        if (sprinting && ClientPerkNetworking.hasPerk(WIND_STRIDE_PERK)) {
            val instance = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) ?: return
            instance.removeModifier(WIND_STRIDE_MOD)
            instance.addTemporaryModifier(
                EntityAttributeModifier(WIND_STRIDE_MOD, WIND_STRIDE_BONUS, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            )
        } else {
            player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED)?.removeModifier(WIND_STRIDE_MOD)
        }
    }
}

package com.randomperks.game

/**
 * 客户端词条状态的**只读镜像**, 放在 main 源集以便被 mixin 读到。
 *
 * 客户端在收到 PerkAbilitySyncS2C 时更新; 服务端全程为空集。
 *
 * 存在的理由: 有些词条必须在**客户端侧**就放行 —— 例如"大胃袋"要解除进食限制,
 * 而原版客户端在 canConsume 返回 false 时**根本不会发送使用物品的包**,
 * 服务端也就永远没机会处理。
 */
object ClientPerkFlags {

    @JvmField
    var ownedPerkIds: Set<String> = emptySet()

    /** "灵魂出鞘"是否正在进行中 (由 SoulOutS2C 同步; 客户端据此启用穿墙移动) */
    @JvmField
    var soulOutActive: Boolean = false

    @JvmStatic
    fun has(id: String): Boolean = id in ownedPerkIds
}

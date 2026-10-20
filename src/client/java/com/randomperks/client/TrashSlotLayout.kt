package com.randomperks.client

import net.minecraft.item.ItemStack
import net.minecraft.item.Items

/**
 * "磁力升级" 垃圾桶栏位的共享布局常量。
 *
 * <p>为什么不放在 mixin 里: Mixin 会把 mixin 类的静态字段合并进目标类, 框架因此要求
 * **静态字段必须是 private** —— 非 private 的静态字段会在 APPLY 阶段直接抛
 * {@code InvalidMixinException: contains non-private static field}。
 * 绘制 (TrashSlotMixin) 与点击 (TrashSlotClickMixin) 需要共用这些值, 所以放在普通类里。
 */
object TrashSlotLayout {

    const val PERK_ID = "seeker_magnet_upgrade"

    /** 格子边长 */
    const val SIZE = 18

    /** 相对 GUI 左上角的位置 (背包界面里 crafting 输出下方、主背包上方那块空白) */
    const val OFFSET_X = 138
    const val OFFSET_Y = 62

    /** 格子里的图标 (岩浆桶 = 销毁) */
    val ICON: ItemStack = ItemStack(Items.LAVA_BUCKET)
}

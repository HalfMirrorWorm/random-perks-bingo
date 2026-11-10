package com.randomperks.game

import com.randomperks.effect.PerkEffects
import com.randomperks.state.PerkStateManager
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

/**
 * "喵人斧" 的一次性标记与用后惩罚。
 *
 * <p>斧头本身用锋利 255 + 耐久上限 1 实现"一次机会"; 这里额外给物品打标记,
 * 在真正命中目标、斧头消耗耐久时给使用者挂上永久虚弱 I (见 NyaAxeUseMixin)。
 * 虚弱只在**使用后**出现, 获得词条时不会挂。
 */
object NyaAxeService {

    const val MARKER_KEY = "randomperks_nya_axe"

    /** 玩家状态 memory 里的标记: 是否已经用过喵人斧 (重生/读档后据此补挂虚弱) */
    const val USED_MEMORY_KEY = "nya_axe_used"

    fun markItem(stack: ItemStack) {
        val nbt = CompoundTag()
        nbt.putBoolean(MARKER_KEY, true)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt))
    }

    fun isMarked(stack: ItemStack): Boolean =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.contains(MARKER_KEY) == true

    /** 获得新斧头时重置"已使用"标记 */
    fun resetUsed(player: ServerPlayer) {
        PerkStateManager.get(player.uuid)?.memory?.remove(USED_MEMORY_KEY)
    }

    fun hasUsed(player: ServerPlayer): Boolean =
        PerkStateManager.get(player.uuid)?.memory?.get(USED_MEMORY_KEY) == "1"

    /** 斧头真正命中目标、消耗耐久后调用 */
    fun onUsed(player: ServerPlayer) {
        PerkStateManager.get(player.uuid)?.memory?.put(USED_MEMORY_KEY, "1")
        PerkEffects.permanentEffect(player, MobEffects.WEAKNESS, 0)
    }
}

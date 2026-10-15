package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity

/**
 * "风之翼"绑定鞘翅的死亡保留 (单物品版"死亡不掉落")。
 *
 * 死亡时把胸甲槽里的绑定鞘翅**取下暂存到玩家状态** —— 不走原版掉落 (拿不到),
 * 也不会被销毁; 重生时由词条 onRespawn 原样装回 (同一件, 耐久等数据完整保留)。
 */
object ElytraBondService {

    private const val PERK_ID = "swift_elytra_bond"

    /** 死亡时调用 (由 ElytraBondDeathMixin 挂钩原版 onDeath 最前端) */
    fun saveOnDeath(player: ServerPlayerEntity) {
        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.hasPerk(PERK_ID)) return

        val chest = player.getEquippedStack(EquipmentSlot.CHEST)
        if (!chest.isOf(Items.ELYTRA)) return

        state.savedChestItem = chest                      // 保留同一件物品
        player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY)
    }

    /** 重生时取出暂存的鞘翅 (取出即清空暂存); 没有暂存则返回 null */
    fun takeSaved(player: ServerPlayerEntity): ItemStack? {
        val state = PerkStateManager.get(player.uuid) ?: return null
        return state.savedChestItem?.also { state.savedChestItem = null }
    }
}

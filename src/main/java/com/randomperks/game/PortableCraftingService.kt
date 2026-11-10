package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand

/**
 * "便携工作台" 附带的**随身合成台**: 手持工作台右键直接打开 3×3 合成界面, 而不是放置方块。
 *
 * <p>右键方块与右键空气是两条路径, 两个事件都要拦 (与 "随身仓库" 的潜影盒同范式)。
 * <b>潜行右键不拦截</b> —— 保留原版"放置工作台"的行为; 放置本身照常消耗手持的工作台。
 */
object PortableCraftingService {

    private const val PERK_ID = "artisan_portable_crafting"

    fun register() {
        // 右键方块: 拦住"放置方块", 改为打开
        UseBlockCallback.EVENT.register { player, _, hand, _ ->
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            if (tryOpen(player)) InteractionResult.SUCCESS else InteractionResult.PASS
        }

        // 右键空气
        UseItemCallback.EVENT.register { player, _, hand ->
            if (hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            if (tryOpen(player)) InteractionResult.SUCCESS else InteractionResult.PASS
        }
    }

    private fun tryOpen(player: ServerPlayer): Boolean {
        if (!PerkSession.isEnabled()) return false

        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(PERK_ID)) return false

        // 潜行右键保留原版"放置工作台"的行为
        if (player.isShiftKeyDown) return false
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).`is`(Items.CRAFTING_TABLE)) return false

        player.openMenu(
            SimpleMenuProvider(
                { syncId, playerInventory, owner ->
                    PortableCraftingScreenHandler(
                        syncId, playerInventory,
                        ContainerLevelAccess.create(owner.level(), owner.blockPosition()),
                    )
                },
                Component.translatable("container.crafting"),
            ),
        )
        return true
    }

    /**
     * 没有工作台方块可依托:
     *  - 传真实 context 只是为了关闭界面时由原版 [CraftingScreenHandler.onClosed]
     *    把合成格里的物品还给玩家 (空的 [ScreenHandlerContext.EMPTY] 会让物品直接消失);
     *  - [canUse] 必须恒真 —— 原版每 tick 校验"上下文位置的方块是不是工作台",
     *    不覆盖的话界面下一秒就会被服务端关掉。
     */
    private class PortableCraftingScreenHandler(
        syncId: Int,
        playerInventory: Inventory,
        context: ContainerLevelAccess,
    ) : CraftingMenu(syncId, playerInventory, context) {
        override fun stillValid(player: Player): Boolean = true
    }
}

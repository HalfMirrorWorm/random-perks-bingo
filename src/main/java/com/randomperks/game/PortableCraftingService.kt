package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.Items
import net.minecraft.screen.CraftingScreenHandler
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

/**
 * "便携工作台" 附带的**随身合成台**: 手持工作台右键直接打开 3×3 合成界面, 而不是放置方块。
 *
 * <p>右键方块与右键空气是两条路径, 两个事件都要拦 (与 "磁力升级" 的潜影盒同范式)。
 * <b>潜行右键不拦截</b> —— 保留原版"放置工作台"的行为; 放置本身照常消耗手持的工作台。
 */
object PortableCraftingService {

    private const val PERK_ID = "artisan_portable_crafting"

    fun register() {
        // 右键方块: 拦住"放置方块", 改为打开
        UseBlockCallback.EVENT.register { player, _, hand, _ ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (tryOpen(player)) ActionResult.SUCCESS else ActionResult.PASS
        }

        // 右键空气
        UseItemCallback.EVENT.register { player, _, hand ->
            if (hand != Hand.MAIN_HAND) return@register ActionResult.PASS
            if (player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (tryOpen(player)) ActionResult.SUCCESS else ActionResult.PASS
        }
    }

    private fun tryOpen(player: ServerPlayerEntity): Boolean {
        if (!PerkSession.isEnabled()) return false

        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(PERK_ID)) return false

        // 潜行右键保留原版"放置工作台"的行为
        if (player.isSneaking) return false
        if (!player.getStackInHand(Hand.MAIN_HAND).isOf(Items.CRAFTING_TABLE)) return false

        player.openHandledScreen(
            SimpleNamedScreenHandlerFactory(
                { syncId, playerInventory, owner ->
                    PortableCraftingScreenHandler(
                        syncId, playerInventory,
                        ScreenHandlerContext.create(owner.world, owner.blockPos),
                    )
                },
                Text.translatable("container.crafting"),
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
        playerInventory: PlayerInventory,
        context: ScreenHandlerContext,
    ) : CraftingScreenHandler(syncId, playerInventory, context) {
        override fun canUse(player: PlayerEntity): Boolean = true
    }
}

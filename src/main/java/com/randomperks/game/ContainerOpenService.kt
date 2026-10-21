package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.block.ShulkerBoxBlock
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ContainerComponent
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ShulkerBoxScreenHandler
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import java.util.UUID

/**
 * "磁力升级" 附带的**便携容器**: 手持潜影盒 / 末影箱右键直接打开, 而不是放置方块。
 *
 * <p>潜影盒的内容存在物品的 {@code CONTAINER} 组件里。打开时复制进一个临时的
 * [SimpleInventory] 交给 [ShulkerBoxScreenHandler]; 界面关闭时再写回物品 —— 全程不放置方块。
 * 末影箱更简单: 直接打开玩家自己的末影箱库存。
 *
 * <p>右键方块与右键空气是两条路径, 两个事件都要拦 (与 "TNT 投手" 同范式)。
 * <b>潜行右键不拦截</b> —— 保留原版"放置方块"的行为。
 */
object ContainerOpenService {

    private const val PERK_ID = "seeker_magnet_upgrade"

    /** 潜影盒格数 */
    private const val SHULKER_SLOTS = 27

    /** 正在开着的潜影盒会话: 玩家 → (界面, 物品, 临时库存) */
    private class ShulkerSession(
        val handler: ScreenHandler,
        val stack: ItemStack,
        val inventory: SimpleInventory,
    )

    private val sessions = mutableMapOf<UUID, ShulkerSession>()

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

        // 潜影盒界面关闭后把内容写回物品
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (sessions.isEmpty()) return@register
            for ((uuid, session) in sessions.toList()) {
                val player = server.playerManager.getPlayer(uuid)
                if (player == null) {
                    sessions.remove(uuid)
                    continue
                }
                if (player.currentScreenHandler !== session.handler) {
                    syncBack(session)
                    sessions.remove(uuid)
                }
            }
        }
    }

    private fun tryOpen(player: ServerPlayerEntity): Boolean {
        if (!PerkSession.isEnabled()) return false

        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(PERK_ID)) return false

        // 潜行右键保留原版"放置方块"的行为
        if (player.isSneaking) return false

        val stack = player.getStackInHand(Hand.MAIN_HAND)
        if (stack.isEmpty) return false

        if (stack.isOf(Items.ENDER_CHEST)) {
            player.openHandledScreen(
                SimpleNamedScreenHandlerFactory(
                    { syncId, playerInventory, _ ->
                        GenericContainerScreenHandler.createGeneric9x3(
                            syncId, playerInventory, player.enderChestInventory,
                        )
                    },
                    Text.translatable("block.minecraft.ender_chest"),
                ),
            )
            return true
        }

        val item = stack.item
        if (item is BlockItem && item.block is ShulkerBoxBlock) {
            openShulker(player, stack)
            return true
        }
        return false
    }

    private fun openShulker(player: ServerPlayerEntity, stack: ItemStack) {
        val inventory = SimpleInventory(SHULKER_SLOTS)
        stack.get(DataComponentTypes.CONTAINER)?.copyTo(inventory.heldStacks)

        val name = stack.name.copy()
        sessions.remove(player.uuid)
        player.openHandledScreen(
            SimpleNamedScreenHandlerFactory(
                { syncId, playerInventory, _ ->
                    ShulkerBoxScreenHandler(syncId, playerInventory, inventory)
                },
                name,
            ),
        )
        sessions[player.uuid] = ShulkerSession(player.currentScreenHandler, stack, inventory)
    }

    /** 把临时库存写回物品的 CONTAINER 组件 */
    private fun syncBack(session: ShulkerSession) {
        if (session.stack.isEmpty) return
        val stacks = session.inventory.heldStacks.map {
            if (it.isEmpty) ItemStack.EMPTY else it.copy()
        }
        session.stack.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(stacks))
    }
}

package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ShulkerBoxMenu
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
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
        val handler: AbstractContainerMenu,
        val stack: ItemStack,
        val inventory: SimpleContainer,
    ) {
        /** 上次写回的内容, 避免每 tick 都重建组件 */
        var lastContents: List<ItemStack> = emptyList()
    }

    private val sessions = mutableMapOf<UUID, ShulkerSession>()

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

        // 开着界面时每 tick 把临时库存写回物品:
        // 这样玩家随时掉线 / 服务器崩溃, 盒子的内容都和界面里看到的一致, 不会出现
        // "拿出来的物品留在背包里、盒子组件还是旧的" 这种复制。
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (sessions.isEmpty()) return@register
            for ((uuid, session) in sessions.toList()) {
                val player = server.playerList.getPlayer(uuid)
                if (player == null) {
                    // 玩家已经不在线: 尽力写回一次再丢弃会话
                    syncBack(session)
                    sessions.remove(uuid)
                    continue
                }
                syncBack(session)
                if (player.containerMenu !== session.handler) {
                    sessions.remove(uuid)
                }
            }
        }

        // 断开连接时补一次写回, 尽量赶在玩家存档落盘之前
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val uuid = handler.player.uuid
            sessions.remove(uuid)?.let { syncBack(it) }
        }
    }

    private fun tryOpen(player: ServerPlayer): Boolean {
        if (!PerkSession.isEnabled()) return false

        val state = PerkStateManager.get(player.uuid) ?: return false
        if (!state.hasPerk(PERK_ID)) return false

        // 潜行右键保留原版"放置方块"的行为
        if (player.isShiftKeyDown) return false

        val stack = player.getItemInHand(InteractionHand.MAIN_HAND)
        if (stack.isEmpty) return false

        if (stack.`is`(Items.ENDER_CHEST)) {
            player.openMenu(
                SimpleMenuProvider(
                    { syncId, playerInventory, _ ->
                        ChestMenu.threeRows(
                            syncId, playerInventory, player.enderChestInventory,
                        )
                    },
                    Component.translatable("block.minecraft.ender_chest"),
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

    private fun openShulker(player: ServerPlayer, stack: ItemStack) {
        val inventory = SimpleContainer(SHULKER_SLOTS)
        stack.get(DataComponents.CONTAINER)?.copyInto(inventory.items)

        val name = stack.hoverName.copy()
        sessions.remove(player.uuid)
        player.openMenu(
            SimpleMenuProvider(
                { syncId, playerInventory, _ ->
                    ShulkerBoxMenu(syncId, playerInventory, inventory)
                },
                name,
            ),
        )
        val session = ShulkerSession(player.containerMenu, stack, inventory)
        session.lastContents = inventory.items.map { if (it.isEmpty) ItemStack.EMPTY else it.copy() }
        sessions[player.uuid] = session
    }

    /** 把临时库存写回物品的 CONTAINER 组件 (内容没变就跳过) */
    private fun syncBack(session: ShulkerSession) {
        if (session.stack.isEmpty) return
        val stacks = session.inventory.items.map {
            if (it.isEmpty) ItemStack.EMPTY else it.copy()
        }
        if (sameContents(stacks, session.lastContents)) return
        session.stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks))
        session.lastContents = stacks
    }

    private fun sameContents(a: List<ItemStack>, b: List<ItemStack>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (!ItemStack.matches(a[i], b[i])) return false
        }
        return true
    }
}

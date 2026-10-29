package com.randomperks.game

import com.randomperks.state.PerkStateManager
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.item.Items
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity

/**
 * "自动搭路" (金 · 疾风): 手持圆石时, 自动在脚下铺设圆石, 走过悬空区域也不会掉下去。
 *
 * <p>每个服务器 tick 检查一次:
 * <ul>
 *   <li>持有词条、主手是圆石;</li>
 *   <li>不在落地/滑翔/骑乘/飞行/游泳/岩浆状态;</li>
 *   <li>脚下的方块可替换 (空气/水等), 且玩家对该位置有修改权限;</li>
 * </ul>
 * 满足时把脚下那一格替换成圆石, 并消耗 1 个圆石 (创造模式不消耗)。
 *
 * <p>放在 `blockPos.down()` 而不是玩家所在格: 方块上表面正好与脚底齐平,
 * 铺出来的路与当前站立高度一致。走路时大约每跨过一格触发一次。
 */
object AutoBridgeService {

    private const val PERK_ID = "swift_auto_bridge"

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server -> tick(server) }
    }

    private fun tick(server: MinecraftServer) {
        if (!PerkSession.isEnabled()) return

        for (player in server.playerManager.playerList) {
            try {
                tryPlace(player)
            } catch (_: Throwable) {
                // 单个玩家异常不应影响其他人
            }
        }
    }

    private fun tryPlace(player: ServerPlayerEntity) {
        if (player.isSpectator) return
        if (player.abilities.flying) return
        if (player.isOnGround) return
        if (player.isGliding) return
        if (player.getVehicle() != null) return
        if (player.isTouchingWater || player.isInLava) return
        if (player.velocity.y > 0.0) return          // 起跳上升阶段不铺

        val state = PerkStateManager.get(player.uuid) ?: return
        if (!state.hasPerk(PERK_ID)) return

        val stack = player.mainHandStack
        if (!stack.isOf(Items.COBBLESTONE)) return

        val pos = player.blockPos.down()
        if (!player.world.getBlockState(pos).isReplaceable) return
        if (!player.world.canEntityModifyAt(player, pos)) return

        player.world.setBlockState(pos, Blocks.COBBLESTONE.defaultState, Block.NOTIFY_ALL)
        if (!player.isCreative) {
            stack.decrement(1)
        }
    }
}

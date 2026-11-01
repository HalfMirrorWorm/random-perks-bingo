package com.randomperks.client

import com.randomperks.game.ClientPerkFlags
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.Items
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

/**
 * "自动搭路" (金 · 疾风) 的**客户端**实现: 手持圆石时, 自动在脚下铺圆石。
 *
 * <h3>为什么从服务端搬到客户端</h3>
 * 旧实现在服务端每 tick 判定后直接 `setBlockState`。服务端先放方块、客户端后知道,
 * 而客户端在收到方块更新之前已经按"脚下是空气"预测了下落, 服务端随后把玩家纠正回
 * 方块上表面 —— 表现就是**位置回弹**。服务端刷得再快也快不过本地物理预测。
 *
 * <h3>参考的做法 (Meteor Scaffold / Wurst ScaffoldWalk)</h3>
 * 脚手架类 mod 全都是**客户端放置**: 客户端调用
 * [net.minecraft.client.network.ClientPlayerInteractionManager.interactBlock], 它内部走原版的
 * "序列化预测" (sendSequencedPacket → interactBlockInternal) —— 先在**本地**世界
 * `setBlockState` 把方块放出来, 再发 `PlayerInteractBlockC2SPacket` 给服务端。
 * 于是客户端物理从这一 tick 起就踩在方块上, 根本不会预测下落, 也就没有回弹;
 * 服务端收到同一个包后按原版规则权威放置 (距离校验 / 领地保护 / 创造不消耗 / 扣物品),
 * 万一服务端拒绝, 原版的 sequenced 机制会把本地方块回滚成服务端的真实状态。
 *
 * <h3>目标格算法 (取自 Meteor Scaffold)</h3>
 * 用 `position + velocity + (0, -0.75, 0)` 取脚下那一格: 减 0.75 是为了让
 * "刚踏空、已经在下落一点点"的那一 tick 仍然落回**原来那一层**, 铺出来的路与站立高度齐平
 * (直接用 `blockPos.down()` 会低一格, 走出一步就掉一层)。
 */
object AutoBridgeClient {

    private const val PERK_ID = "swift_auto_bridge"

    fun register() {
        // START_CLIENT_TICK: 早于本 tick 的实体物理, 放下的方块当场就能参与碰撞
        ClientTickEvents.START_CLIENT_TICK.register { client -> tick(client) }
    }

    private fun tick(client: MinecraftClient) {
        val player = client.player ?: return
        val world = client.world ?: return
        val interaction = client.interactionManager ?: return

        if (!ClientPerkFlags.has(PERK_ID)) return
        if (player.isSpectator || player.abilities.flying) return
        if (player.isGliding || player.getVehicle() != null) return
        if (player.isTouchingWater || player.isInLava || player.isSwimming) return
        if (player.velocity.y > 0.0) return          // 起跳上升阶段不铺
        if (!player.mainHandStack.isOf(Items.COBBLESTONE)) return

        // 脚下已经有东西踩着 (含半砖/台阶) 就不用铺, 免得站在半砖上白扣圆石
        val support = player.blockPos.down()
        if (!world.getBlockState(support).isReplaceable) return

        // 目标格: 脚下那一层 (减 0.75 让刚踏空的 tick 仍落在原来那一层)
        val target = BlockPos.ofFloored(
            player.x,
            player.y + player.velocity.y - 0.75,
            player.z,
        )
        // 不允许铺到玩家自己所在的高度或更高
        val maxY = player.blockPos.y - 1
        val placePos = if (target.y > maxY) BlockPos(target.x, maxY, target.z) else target

        val state = world.getBlockState(placePos)
        if (!state.isReplaceable) return
        if (!state.fluidState.isEmpty) return

        val hit = findClickTarget(world, placePos, player) ?: return
        interaction.interactBlock(player, Hand.MAIN_HAND, hit)
    }

    /**
     * 原版放置必须"贴着某个面", 不能凭空放: 在目标格周围找一个实心邻居,
     * 把点击面设成朝向目标的那一面, 服务端就会把方块放到目标格上。
     *
     * <p>优先点"身后"那一格 —— 也就是刚走过的路面, 通常是圆石, 不会被方块自身
     * 的交互 (门/拉杆/箱子等) 吃掉这次右键。
     */
    private fun findClickTarget(
        world: ClientWorld,
        target: BlockPos,
        player: ClientPlayerEntity,
    ): BlockHitResult? {
        val behind = player.horizontalFacing.opposite
        val order = ArrayList<Direction>(6)
        order.add(behind)
        order.add(Direction.DOWN)
        for (dir in Direction.values()) {
            if (dir != behind && dir != Direction.DOWN) order.add(dir)
        }

        for (dir in order) {
            val neighbor = target.offset(dir)
            val neighborState = world.getBlockState(neighbor)
            if (neighborState.isReplaceable) continue
            if (neighborState.getCollisionShape(world, neighbor).isEmpty) continue

            // 点击邻居朝向目标的那一面: 放置位置 = neighbor.offset(face) = target
            val face = dir.opposite
            val hitPos = Vec3d.ofCenter(neighbor).add(Vec3d.of(face.vector).multiply(0.5))
            return BlockHitResult(hitPos, face, neighbor, false)
        }
        return null
    }
}

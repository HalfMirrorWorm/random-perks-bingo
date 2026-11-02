package com.randomperks.client

import com.randomperks.game.AutoBridgePlan
import com.randomperks.game.ClientPerkFlags
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.item.Items
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

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
 * [net.minecraft.client.network.ClientPlayerInteractionManager.useItemOn], 它内部走原版的
 * "序列化预测" (sendSequencedPacket → useItemOnInternal) —— 先在**本地**世界
 * `setBlockState` 把方块放出来, 再发 `PlayerInteractBlockC2SPacket` 给服务端。
 * 于是客户端物理从这一 tick 起就踩在方块上, 根本不会预测下落, 也就没有回弹;
 * 服务端收到同一个包后按原版规则权威放置 (距离校验 / 领地保护 / 创造不消耗 / 扣物品),
 * 万一服务端拒绝, 原版的 sequenced 机制会把本地方块回滚成服务端的真实状态。
 *
 * <h3>什么时候会突然停</h3>
 * 只在"中心已经走到空气上"才铺, 能铺的窗口只有碰撞箱还搭在上一格上的那 0.3 格。
 * 疾跑、速度词条或斜着过角时, 一 tick 就跨过这 0.3 格, 人先掉下去。
 * 掉了不到一格后 `blockPos.y` 会减 1, 若再把目标夹到 `blockPos.y - 1`, 铺的是更低的一层,
 * 那一格和原来的路不共面, [findClickTarget] 找不到点击面, 之后每一跳都失败, 看起来就是突然不搭了。
 * 所以在 tick 开头按速度提前铺下一格 (人还踩在旧路上), 斜向时先补共面的中间格。
 * 见 [AutoBridgePlan]。
 */
object AutoBridgeClient {

    private const val PERK_ID = "swift_auto_bridge"

    /** 起跳初速度约 0.42。低于此的向上速度 (碰撞挤出、击退残余) 仍然铺路。 */
    private const val JUMP_VELOCITY = 0.2

    fun register() {
        // START_CLIENT_TICK: 早于本 tick 的实体物理, 放下的方块当场就能参与碰撞
        ClientTickEvents.START_CLIENT_TICK.register { client -> tick(client) }
    }

    private fun tick(client: Minecraft) {
        val player = client.player ?: return
        val world = client.level ?: return
        val interaction = client.gameMode ?: return

        if (!ClientPerkFlags.has(PERK_ID)) return
        if (player.isSpectator || player.abilities.flying) return
        if (player.isFallFlying || player.getVehicle() != null) return
        if (player.isInLava || player.isSwimming) return
        if (player.deltaMovement.y > JUMP_VELOCITY) return
        if (!player.mainHandItem.`is`(Items.COBBLESTONE)) return

        val velocity = player.deltaMovement
        val targets = AutoBridgePlan.targets(player.x, player.y, player.z, velocity.x, velocity.z)
        for (placePos in targets) {
            if (!player.mainHandItem.`is`(Items.COBBLESTONE)) return
            tryPlace(world, player, interaction, placePos)
        }
    }

    /**
     * 原版放置要求目标格可替换, 且玩家碰撞箱不能和它相交 (相交会被服务端拒绝并回滚)。
     * 成功后本地世界已经有这块方块, 同一 tick 里后面的格子可以点它的面。
     */
    private fun tryPlace(
        world: ClientLevel,
        player: LocalPlayer,
        interaction: MultiPlayerGameMode,
        placePos: BlockPos,
    ) {
        val state = world.getBlockState(placePos)
        if (!state.canBeReplaced()) return
        if (player.boundingBox.intersects(AABB(placePos))) return

        val hit = findClickTarget(world, placePos, player) ?: return
        interaction.useItemOn(player, InteractionHand.MAIN_HAND, hit)
    }

    /**
     * 原版放置必须"贴着某个面", 不能凭空放: 在目标格周围找一个实心邻居,
     * 把点击面设成朝向目标的那一面, 服务端就会把方块放到目标格上。
     *
     * <p>优先点"身后"那一格 —— 也就是刚走过的路面, 通常是圆石, 不会被方块自身
     * 的交互 (门/拉杆/箱子等) 吃掉这次右键。
     */
    private fun findClickTarget(
        world: ClientLevel,
        target: BlockPos,
        player: LocalPlayer,
    ): BlockHitResult? {
        val behind = player.direction.opposite
        val order = ArrayList<Direction>(6)
        order.add(behind)
        order.add(Direction.DOWN)
        for (dir in Direction.entries) {
            if (dir != behind && dir != Direction.DOWN) order.add(dir)
        }

        for (dir in order) {
            val neighbor = target.relative(dir)
            val neighborState = world.getBlockState(neighbor)
            if (neighborState.canBeReplaced()) continue
            if (neighborState.getCollisionShape(world, neighbor).isEmpty) continue

            // 点击邻居朝向目标的那一面: 放置位置 = neighbor.offset(face) = target
            val face = dir.opposite
            val hitPos = Vec3.atCenterOf(neighbor).add(face.unitVec3.scale(0.5))
            return BlockHitResult(hitPos, face, neighbor, false)
        }
        return null
    }
}

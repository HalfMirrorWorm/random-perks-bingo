package com.randomperks.effect

import net.minecraft.block.BlockState
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/**
 * 连锁破坏的通用实现 (矿脉之主 / 树倒了 共用)。
 *
 * 从 origin 出发沿六个方向扩散, 破坏所有与 match 匹配的相连方块, 上限 cap 个。
 * 通过 ServerWorld.breakBlock 破坏: 掉落物按原版规则生成 (含时运/精准采集),
 * 且不会再次触发玩家挖掘事件 —— 天然防递归。
 */
object ChainBreak {

    /** @return 实际连锁破坏的方块数量 (不含触发挖掘的原方块本身) */
    fun run(
        player: ServerPlayerEntity,
        origin: BlockPos,
        cap: Int,
        match: (BlockState) -> Boolean,
    ): Int {
        val world = player.world as? ServerWorld ?: return 0
        if (player.isCreative) return 0
        val start = origin.toImmutable()

        val queue = ArrayDeque<BlockPos>()
        val visited = HashSet<BlockPos>()
        visited.add(start)
        queue.addFirst(start)

        var broken = 0
        while (queue.isNotEmpty() && broken < cap) {
            val current = queue.removeFirst()
            for (direction in Direction.values()) {
                val next = current.offset(direction)
                if (!visited.add(next)) continue
                if (!match(world.getBlockState(next))) continue

                world.breakBlock(next, true, player)
                broken++
                queue.addFirst(next)
                if (broken >= cap) break
            }
        }
        return broken
    }
}

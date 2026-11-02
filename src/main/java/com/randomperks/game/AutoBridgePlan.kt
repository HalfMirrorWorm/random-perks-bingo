package com.randomperks.game

import net.minecraft.util.math.BlockPos
import kotlin.math.abs

/**
 * "自动搭路" 每 tick 要尝试放置的格子。纯计算, 不碰世界。
 *
 * 玩家碰撞箱宽 0.6, 脚底中心超出当前格边缘 0.3 格之后, 人就完全离开支撑开始下落。
 * 而这一跳的水平速度在 tick 开始时只看得到上一跳摩擦之后的值, 比真实位移小。
 * 所以朝速度方向再探出 [SUPPORT_MARGIN], 在人还踩在上一格时就把下一格铺上。
 * 斜向移动时两格之间不共面, 原版对不上点击面, 中间按曼哈顿顺序补一格。
 */
object AutoBridgePlan {

    /** 玩家碰撞箱半宽, 也是"再走这么远就会失去脚下支撑"的距离。 */
    const val SUPPORT_MARGIN = 0.3

    /** 一 tick 最多铺这么多格, 避免速度异常时连发过多放置包。 */
    private const val MAX_STEP = 4

    /**
     * 脚底所在的路面高度。
     * 减去一个很小的数, 使正好站在方块顶上 (y 为整数) 时落在脚下那一层;
     * 刚踏空、只掉了不到 1 格时仍然是原来那一层。
     */
    fun bridgeY(feetY: Double): Int = BlockPos.ofFloored(0.0, feetY - 1.0e-3, 0.0).y

    /**
     * 本 tick 按顺序尝试的格子: 先脚下, 再沿速度走到的那一格。
     * 相邻两项最多差一格, 且只在一个轴上差, 保证后一格能点到前一格的面。
     */
    fun targets(
        feetX: Double,
        feetY: Double,
        feetZ: Double,
        velocityX: Double,
        velocityZ: Double,
    ): List<BlockPos> {
        val y = bridgeY(feetY)
        val origin = BlockPos.ofFloored(feetX, y.toDouble(), feetZ)
        val ahead = BlockPos.ofFloored(
            feetX + velocityX + lead(velocityX),
            y.toDouble(),
            feetZ + velocityZ + lead(velocityZ),
        )
        return manhattan(origin, ahead)
    }

    private fun lead(velocity: Double): Double =
        if (abs(velocity) > 1.0e-4) Math.copySign(SUPPORT_MARGIN, velocity) else 0.0

    private fun manhattan(from: BlockPos, to: BlockPos): List<BlockPos> {
        var dx = (to.x - from.x).coerceIn(-MAX_STEP, MAX_STEP)
        var dz = (to.z - from.z).coerceIn(-MAX_STEP, MAX_STEP)
        val sx = dx.compareTo(0)
        val sz = dz.compareTo(0)
        val out = ArrayList<BlockPos>(1 + abs(dx) + abs(dz))
        var x = from.x
        var z = from.z
        val y = from.y
        out.add(BlockPos(x, y, z))
        while (dx != 0) {
            x += sx
            dx -= sx
            out.add(BlockPos(x, y, z))
        }
        while (dz != 0) {
            z += sz
            dz -= sz
            out.add(BlockPos(x, y, z))
        }
        return out
    }
}

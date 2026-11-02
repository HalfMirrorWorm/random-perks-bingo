package com.randomperks.game

import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoBridgePlanTest {

    @Test
    fun bridgeHeightStaysOnTheSameLayerAfterAShortFall() {
        assertEquals(63, AutoBridgePlan.bridgeY(64.0))
        // 掉了不到一格 (重力第一跳约 0.08) 时, 不能改去铺更低的一层
        assertEquals(63, AutoBridgePlan.bridgeY(63.92))
        assertEquals(62, AutoBridgePlan.bridgeY(63.0))
    }

    @Test
    fun placesTheNextBlockBeforeSprintingOffTheEdge() {
        // 中心还在这一格 (0.95), 但这一跳的速度会跨过 0.3 的支撑边缘
        val targets = AutoBridgePlan.targets(0.95, 64.0, 5.2, 0.22, 0.0)
        assertEquals(listOf(BlockPos(0, 63, 5), BlockPos(1, 63, 5)), targets)
    }

    @Test
    fun diagonalStepStaysFaceAdjacent() {
        val targets = AutoBridgePlan.targets(0.9, 64.0, 0.9, 0.2, 0.2)
        assertTrue(targets.size >= 2)
        for (i in 1 until targets.size) {
            val prev = targets[i - 1]
            val next = targets[i]
            val manhattan = abs(next.x - prev.x) + abs(next.y - prev.y) + abs(next.z - prev.z)
            assertEquals(1, manhattan, "目标 $next 必须和 $prev 共面, 否则原版点不到")
        }
        assertEquals(63, targets.first().y)
        assertTrue(targets.last().x > targets.first().x)
        assertTrue(targets.last().z > targets.first().z)
    }

    @Test
    fun negativeCoordinatesAndStandingStill() {
        assertEquals(
            listOf(BlockPos(-2, -11, -8)),
            AutoBridgePlan.targets(-1.2, -10.0, -7.4, 0.0, 0.0),
        )
        val westward = AutoBridgePlan.targets(-0.15, 64.0, 0.4, -0.6, 0.0)
        assertEquals(BlockPos(-1, 63, 0), westward.first())
        assertTrue(westward.any { it.x == -2 })
    }
}
